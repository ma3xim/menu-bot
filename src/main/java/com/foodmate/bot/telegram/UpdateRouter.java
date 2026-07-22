package com.foodmate.bot.telegram;

import com.foodmate.bot.dto.IngredientLineDto;
import com.foodmate.bot.dto.RecipeDraftDto;
import com.foodmate.bot.entity.CookingHistory;
import com.foodmate.bot.entity.FavoriteRecipe;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.Tag;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.AccessDeniedException;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.service.AccessService;
import com.foodmate.bot.service.CookingHistoryService;
import com.foodmate.bot.service.DishOfTheDayService;
import com.foodmate.bot.service.FavoriteService;
import com.foodmate.bot.service.GroupNotifyService;
import com.foodmate.bot.service.RecipeService;
import com.foodmate.bot.service.RecommendationService;
import com.foodmate.bot.service.ShoppingListService;
import com.foodmate.bot.service.StatsService;
import com.foodmate.bot.service.TagService;
import com.foodmate.bot.service.UserService;
import com.foodmate.bot.telegram.callback.CallbackData;
import com.foodmate.bot.telegram.fsm.RecipeFsmService;
import com.foodmate.bot.telegram.fsm.RecipeFsmSession;
import com.foodmate.bot.telegram.fsm.RecipeFsmState;
import com.foodmate.bot.telegram.keyboards.KeyboardFactory;
import com.foodmate.bot.util.RecipeFormatter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateRouter {

    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter HISTORY_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final AccessService accessService;
    private final UserService userService;
    private final RecipeService recipeService;
    private final RecommendationService recommendationService;
    private final FavoriteService favoriteService;
    private final CookingHistoryService cookingHistoryService;
    private final TagService tagService;
    private final ShoppingListService shoppingListService;
    private final RecipeFsmService fsmService;
    private final TelegramSender sender;
    private final UpdateContext updateContext;
    private final GroupNotifyService groupNotifyService;
    private final PendingCommentService pendingCommentService;
    private final StatsService statsService;
    private final DishOfTheDayService dishOfTheDayService;

    public void route(Update update) {
        try {
            bindContext(update);
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (AccessDeniedException e) {
            Long chatId = extractChatId(update);
            if (chatId != null) {
                sender.sendText(chatId, e.getMessage());
            }
        } catch (NotFoundException | BotBusinessException e) {
            Long chatId = extractChatId(update);
            if (chatId != null) {
                sender.sendText(chatId, e.getMessage(), KeyboardFactory.backToMenu());
            }
        } catch (Exception e) {
            log.error("Unhandled update error", e);
            Long chatId = extractChatId(update);
            if (chatId != null) {
                sender.sendText(chatId, "Произошла ошибка. Попробуйте ещё раз.");
            }
        } finally {
            updateContext.clear();
        }
    }

    private void bindContext(Update update) {
        Long chatId = extractChatId(update);
        Integer threadId = extractThreadId(update);
        updateContext.set(chatId, threadId);
        groupNotifyService.rememberGroupTarget(chatId, threadId);
        if (chatId != null && chatId < 0) {
            log.info("Group update chatId={}, threadId={}", chatId, threadId);
        }
    }

    private void handleMessage(Message message) {
        if (message.getFrom() == null) {
            return;
        }
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        accessService.requireAllowed(telegramId);
        User user = userService.getOrCreate(telegramId, displayName(message));

        if (message.hasText()) {
            String text = message.getText().trim();
            if (text.startsWith("/start")) {
                fsmService.clear(telegramId);
                sender.sendText(chatId, "Привет! Я FoodMate — помогу выбрать блюдо на день.", KeyboardFactory.mainMenu());
                return;
            }
            if (text.startsWith("/menu")) {
                fsmService.clear(telegramId);
                sender.sendText(chatId, "Главное меню:", KeyboardFactory.mainMenu());
                return;
            }
            if (text.startsWith("/cancel")) {
                fsmService.clear(telegramId);
                pendingCommentService.clear(telegramId);
                sender.sendText(chatId, "Отменено.", KeyboardFactory.mainMenu());
                return;
            }

            if (pendingCommentService.getHistoryId(telegramId).isPresent()) {
                handleCommentInput(chatId, user, text);
                return;
            }

            var sessionOpt = fsmService.get(telegramId);
            if (sessionOpt.isPresent()) {
                handleFsmInput(chatId, user, sessionOpt.get(), text);
                return;
            }

            if (text.startsWith("/search ") || !text.startsWith("/")) {
                String query = text.startsWith("/search ") ? text.substring(8).trim() : text;
                if (StringUtils.hasText(query) && !query.startsWith("/")) {
                    showSearchResults(chatId, query, 0);
                    return;
                }
            }
        }

        var sessionOpt = fsmService.get(telegramId);
        if (sessionOpt.isPresent() && sessionOpt.get().getState() == RecipeFsmState.WAIT_VIDEO) {
            if (attachVideoFromMessage(sessionOpt.get(), message)) {
                goToConfirm(chatId, sessionOpt.get());
                return;
            }
            sender.sendText(chatId, "Пришлите видео (можно переслать), '-' чтобы пропустить, или «удалить» чтобы убрать видео.");
            return;
        }

        sender.sendText(chatId, "Не понял команду. Откройте меню:", KeyboardFactory.mainMenu());
    }

    private void handleCallback(CallbackQuery callback) {
        Long telegramId = callback.getFrom().getId();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();
        String data = callback.getData();
        accessService.requireAllowed(telegramId);
        User user = userService.getOrCreate(telegramId, displayName(callback));
        sender.answerCallback(callback.getId(), "Ок");

        if (CallbackData.MENU_MAIN.equals(data)) {
            fsmService.clear(telegramId);
            sender.editText(chatId, messageId, "Главное меню:", KeyboardFactory.mainMenu());
            return;
        }
        if (CallbackData.DISH_RANDOM.equals(data)) {
            showRandom(chatId, messageId, user, null, true);
            return;
        }
        if (CallbackData.DISH_AGAIN.equals(data)) {
            showRandom(chatId, messageId, user, null, false);
            return;
        }
        if (CallbackData.RECIPE_ADD.equals(data)) {
            fsmService.startAdd(telegramId);
            sender.editText(chatId, messageId, "Добавление рецепта.\nВведите название:", KeyboardFactory.backToMenu());
            return;
        }
        if (data.startsWith("list:recipes")) {
            int page = parsePage(data, "list:recipes");
            showRecipes(chatId, messageId, page);
            return;
        }
        if (data.startsWith("list:fav")) {
            int page = parsePage(data, "list:fav");
            showFavorites(chatId, messageId, user, page);
            return;
        }
        if (data.startsWith("list:hist")) {
            int page = parsePage(data, "list:hist");
            showHistory(chatId, messageId, user, page);
            return;
        }
        if (CallbackData.SEARCH.equals(data)) {
            sender.editText(chatId, messageId,
                    "Введите текст для поиска (или /search название):",
                    KeyboardFactory.backToMenu());
            return;
        }
        if (CallbackData.FILTER_TAGS.equals(data)) {
            List<Tag> tags = tagService.findAll();
            if (tags.isEmpty()) {
                sender.editText(chatId, messageId, "Тегов пока нет.", KeyboardFactory.backToMenu());
            } else {
                sender.editText(chatId, messageId, "Выберите тег:", KeyboardFactory.tagsFilter(tags));
            }
            return;
        }
        if (data.startsWith("filter:tag:")) {
            String rest = data.substring("filter:tag:".length());
            String[] parts = rest.split(":");
            Long tagId = Long.parseLong(parts[0]);
            int page = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            showByTag(chatId, messageId, tagId, page);
            return;
        }
        if (CallbackData.STATS.equals(data)) {
            sender.editText(chatId, messageId, statsService.buildStatsReport(), KeyboardFactory.backToMenu());
            return;
        }
        if (data.startsWith("dish:day:")) {
            Long recipeId = Long.parseLong(data.substring("dish:day:".length()));
            var entry = dishOfTheDayService.setDishOfTheDay(recipeId, user);
            sender.editText(chatId, messageId,
                    "📌 «" + entry.getRecipe().getName() + "» закреплено как блюдо дня в группе.",
                    KeyboardFactory.recipeActions(recipeId, favoriteService.isFavorite(user.getId(), recipeId)));
            // Always announce in group (pin message already there; if action was in DM, also notify)
            groupNotifyService.notify(who(user) + " выбрал(а) блюдо дня: «" + entry.getRecipe().getName() + "»");
            return;
        }
        if (CallbackData.SHOP_ALL.equals(data)) {
            String text = shoppingListService.formatCurrentList();
            boolean hasItems = shoppingListService.count() > 0;
            sender.editText(chatId, messageId, text, KeyboardFactory.shoppingList(hasItems));
            return;
        }
        if (CallbackData.SHOP_CLEAR.equals(data)) {
            sender.editText(chatId, messageId, "Очистить весь список покупок?", KeyboardFactory.confirmClearShopping());
            return;
        }
        if (CallbackData.SHOP_CLEAR_OK.equals(data)) {
            int removed = shoppingListService.clearAll();
            sender.editText(chatId, messageId,
                    "Список очищен (удалено позиций: " + removed + ").",
                    KeyboardFactory.shoppingList(false));
            groupNotifyService.notify(who(user) + " очистил(а) список покупок");
            return;
        }
        if (data.startsWith("shop:add:")) {
            Long recipeId = Long.parseLong(data.substring("shop:add:".length()));
            int added = shoppingListService.addFromRecipe(recipeId, telegramId);
            long total = shoppingListService.count();
            sender.editText(chatId, messageId,
                    "Добавлено в покупки: " + added + "\nВсего в списке: " + total,
                    KeyboardFactory.afterAddToShopping(recipeId));
            Recipe recipe = recipeService.getDetailed(recipeId);
            groupNotifyService.notify(who(user) + " добавил(а) в покупки ингредиенты из «" + recipe.getName()
                    + "» (+" + added + ", всего " + total + ")");
            return;
        }
        if (CallbackData.ADD_CANCEL.equals(data)) {
            fsmService.clear(telegramId);
            sender.editText(chatId, messageId, "Отменено.", KeyboardFactory.mainMenu());
            return;
        }
        if (CallbackData.ADD_CONFIRM.equals(data)) {
            confirmFsm(chatId, messageId, user);
            return;
        }
        if (data.startsWith("recipe:view:")) {
            showRecipe(chatId, messageId, user, Long.parseLong(data.substring("recipe:view:".length())), true);
            return;
        }
        if (data.startsWith("recipe:cooked:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:cooked:".length()));
            sender.editText(chatId, messageId,
                    "Оцените блюдо (или пропустите):",
                    KeyboardFactory.ratingKeyboard(recipeId));
            return;
        }
        if (data.startsWith("recipe:rate:")) {
            String[] parts = data.split(":");
            Long recipeId = Long.parseLong(parts[2]);
            int rating = Integer.parseInt(parts[3]);
            var history = cookingHistoryService.markCooked(user, recipeId, rating);
            Recipe recipe = recipeService.getDetailed(recipeId);
            sender.editText(chatId, messageId,
                    "Записал оценку " + rating + "⭐.\nНапишите комментарий к блюду (или '-' чтобы пропустить):",
                    null);
            pendingCommentService.start(telegramId, history.getId());
            groupNotifyService.notify(who(user) + " ставит блюду «" + recipe.getName()
                    + "» оценку " + rating + "⭐");
            return;
        }
        if (data.startsWith("recipe:skiprate:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:skiprate:".length()));
            var history = cookingHistoryService.markCooked(user, recipeId, null);
            Recipe recipe = recipeService.getDetailed(recipeId);
            sender.editText(chatId, messageId,
                    "Записал в историю.\nНапишите комментарий к блюду (или '-' чтобы пропустить):",
                    null);
            pendingCommentService.start(telegramId, history.getId());
            groupNotifyService.notify(who(user) + " отмечает, что блюдо «" + recipe.getName() + "» приготовили");
            return;
        }
        if (data.startsWith("recipe:fav:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:fav:".length()));
            boolean nowFavorite = favoriteService.toggle(user, recipeId);
            showRecipe(chatId, messageId, user, recipeId, false);
            sender.answerCallback(callback.getId(), nowFavorite ? "В избранном" : "Убрано");
            return;
        }
        if (data.startsWith("recipe:edit:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:edit:".length()));
            Recipe existing = recipeService.getDetailed(recipeId);
            RecipeFsmSession session = fsmService.startEdit(telegramId, recipeId);
            session.setVideoFileId(existing.getVideoFileId());
            session.setVideoFileUniqueId(existing.getVideoFileUniqueId());
            session.setVideoKind(existing.getVideoKind());
            sender.editText(chatId, messageId,
                    "Редактирование. Введите новое название (или /cancel):",
                    KeyboardFactory.backToMenu());
            return;
        }
        if (data.startsWith("recipe:delok:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:delok:".length()));
            recipeService.delete(recipeId);
            sender.editText(chatId, messageId, "Рецепт удалён.", KeyboardFactory.mainMenu());
            return;
        }
        if (data.startsWith("recipe:del:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:del:".length()));
            sender.editText(chatId, messageId, "Удалить рецепт?", KeyboardFactory.confirmDelete(recipeId));
        }
    }

    private void handleCommentInput(Long chatId, User user, String text) {
        Long historyId = pendingCommentService.getHistoryId(user.getTelegramId())
                .orElseThrow(() -> new BotBusinessException("Нет ожидания комментария"));
        pendingCommentService.clear(user.getTelegramId());
        if ("-".equals(text.trim())) {
            sender.sendText(chatId, "Ок, без комментария.", KeyboardFactory.mainMenu());
            return;
        }
        CookingHistory history = cookingHistoryService.addComment(historyId, text.trim());
        Recipe recipe = recipeService.getDetailed(history.getRecipe().getId());
        sender.sendText(chatId, "Комментарий сохранён.", KeyboardFactory.mainMenu());
        groupNotifyService.notify(who(user) + " оставил(а) отзыв к «" + recipe.getName() + "»: " + truncate(text.trim(), 120));
    }

    private void handleFsmInput(Long chatId, User user, RecipeFsmSession session, String text) {
        switch (session.getState()) {
            case WAIT_NAME -> {
                session.setName(text);
                session.setState(RecipeFsmState.WAIT_DESCRIPTION);
                sender.sendText(chatId, "Введите описание (или '-' чтобы пропустить):");
            }
            case WAIT_DESCRIPTION -> {
                session.setDescription("-".equals(text) ? null : text);
                session.setState(RecipeFsmState.WAIT_TIME);
                sender.sendText(chatId, "Введите время приготовления в минутах (или '-' ):");
            }
            case WAIT_TIME -> {
                if (!"-".equals(text)) {
                    try {
                        session.setCookingTimeMinutes(Integer.parseInt(text.trim()));
                    } catch (NumberFormatException e) {
                        sender.sendText(chatId, "Нужно число минут или '-'");
                        return;
                    }
                }
                session.setState(RecipeFsmState.WAIT_INGREDIENTS);
                sender.sendText(chatId, """
                        Введите ингредиенты по одному в строке:
                        название | количество | единица
                        Например:
                        яйца | 3 | шт
                        молоко | 50 | мл
                        Пустая строка с '-' — закончить список.""");
            }
            case WAIT_INGREDIENTS -> {
                if ("-".equals(text.trim())) {
                    session.setState(RecipeFsmState.WAIT_TAGS);
                    sender.sendText(chatId, "Введите теги через запятую (или '-'):");
                    return;
                }
                IngredientLineDto line = parseIngredientLine(text);
                if (line == null) {
                    sender.sendText(chatId, "Формат: название | количество | единица");
                    return;
                }
                session.getIngredients().add(line);
                sender.sendText(chatId, "Добавлено. Ещё ингредиент или '-' для продолжения.");
            }
            case WAIT_TAGS -> {
                if (!"-".equals(text.trim())) {
                    session.setTags(Arrays.stream(text.split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList());
                }
                session.setState(RecipeFsmState.WAIT_VIDEO);
                sender.sendText(chatId, """
                        Пришлите видео к рецепту (можно переслать из другого чата).
                        Или '-' чтобы пропустить.
                        Или «удалить» — убрать видео (при редактировании).""");
            }
            case WAIT_VIDEO -> {
                if ("удалить".equalsIgnoreCase(text.trim())) {
                    session.setVideoFileId(null);
                    session.setVideoFileUniqueId(null);
                    session.setVideoKind(null);
                    goToConfirm(chatId, session);
                    return;
                }
                if ("-".equals(text.trim())) {
                    goToConfirm(chatId, session);
                    return;
                }
                sender.sendText(chatId, "Нужно видео (можно переслать), '-' или «удалить».");
            }
            case CONFIRM -> sender.sendText(chatId, "Нажмите «Сохранить» или «Отмена».", KeyboardFactory.confirmAdd());
            default -> {
                fsmService.clear(user.getTelegramId());
                sender.sendText(chatId, "Сессия сброшена.", KeyboardFactory.mainMenu());
            }
        }
    }

    private void goToConfirm(Long chatId, RecipeFsmSession session) {
        session.setState(RecipeFsmState.CONFIRM);
        sender.sendText(chatId, buildDraftPreview(session), KeyboardFactory.confirmAdd());
    }

    private boolean attachVideoFromMessage(RecipeFsmSession session, Message message) {
        if (message.hasVideo()) {
            session.setVideoFileId(message.getVideo().getFileId());
            session.setVideoFileUniqueId(message.getVideo().getFileUniqueId());
            session.setVideoKind("VIDEO");
            return true;
        }
        if (message.hasVideoNote()) {
            session.setVideoFileId(message.getVideoNote().getFileId());
            session.setVideoFileUniqueId(message.getVideoNote().getFileUniqueId());
            session.setVideoKind("VIDEO_NOTE");
            return true;
        }
        if (message.hasDocument()
                && message.getDocument().getMimeType() != null
                && message.getDocument().getMimeType().startsWith("video/")) {
            session.setVideoFileId(message.getDocument().getFileId());
            session.setVideoFileUniqueId(message.getDocument().getFileUniqueId());
            session.setVideoKind("DOCUMENT");
            return true;
        }
        return false;
    }

    private void confirmFsm(Long chatId, Integer messageId, User user) {
        RecipeFsmSession session = fsmService.get(user.getTelegramId())
                .orElseThrow(() -> new BotBusinessException("Нет активной сессии добавления"));
        RecipeDraftDto draft = new RecipeDraftDto(
                session.getName(),
                session.getDescription(),
                session.getCookingTimeMinutes(),
                session.getIngredients(),
                session.getTags(),
                session.getVideoFileId(),
                session.getVideoFileUniqueId(),
                session.getVideoKind()
        );
        Recipe recipe;
        if (session.getEditingRecipeId() != null) {
            recipe = recipeService.update(session.getEditingRecipeId(), draft);
        } else {
            recipe = recipeService.create(draft, user);
        }
        fsmService.clear(user.getTelegramId());
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        Recipe detailed = recipeService.getDetailed(recipe.getId());
        sender.editText(chatId, messageId, RecipeFormatter.formatCard(detailed, fav),
                KeyboardFactory.recipeActions(detailed.getId(), fav));
        sendRecipeVideoIfAny(chatId, detailed);
        boolean edited = session.getEditingRecipeId() != null;
        groupNotifyService.notify(who(user) + (edited ? " обновил(а) рецепт «" : " добавил(а) новый рецепт «")
                + detailed.getName() + "»");
    }

    private void showRandom(Long chatId, Integer messageId, User user, Long tagId, boolean notifyGroup) {
        Recipe picked = recommendationService.requireRandom(tagId);
        Recipe recipe = recipeService.getDetailed(picked.getId());
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        sender.editText(chatId, messageId, RecipeFormatter.formatCard(recipe, fav),
                KeyboardFactory.recipeActions(recipe.getId(), fav));
        sendRecipeVideoIfAny(chatId, recipe);
        if (notifyGroup) {
            groupNotifyService.notify(who(user) + " выбирает на сегодня: «" + recipe.getName() + "»");
        }
    }

    private void showRecipe(Long chatId, Integer messageId, User user, Long recipeId, boolean withVideo) {
        Recipe recipe = recipeService.getDetailed(recipeId);
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        sender.editText(chatId, messageId, RecipeFormatter.formatCard(recipe, fav),
                KeyboardFactory.recipeActions(recipe.getId(), fav));
        if (withVideo) {
            sendRecipeVideoIfAny(chatId, recipe);
        }
    }

    private void sendRecipeVideoIfAny(Long chatId, Recipe recipe) {
        if (StringUtils.hasText(recipe.getVideoFileId())) {
            sender.sendRecipeVideo(chatId, recipe.getVideoFileId(), recipe.getVideoKind());
        }
    }

    private void showRecipes(Long chatId, Integer messageId, int page) {
        Page<Recipe> recipes = recipeService.list(page, PAGE_SIZE);
        if (recipes.isEmpty()) {
            sender.editText(chatId, messageId, "Рецептов пока нет.", KeyboardFactory.backToMenu());
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> {
            ids.add(r.getId());
            titles.add(r.getName());
        });
        sender.editText(chatId, messageId, "Рецепты:",
                KeyboardFactory.recipeListButtons(ids, titles, "list:recipes", page, recipes.getTotalPages()));
    }

    private void showByTag(Long chatId, Integer messageId, Long tagId, int page) {
        Tag tag = tagService.requireById(tagId);
        Page<Recipe> recipes = recipeService.findByTag(tagId, page, PAGE_SIZE);
        if (recipes.isEmpty()) {
            sender.editText(chatId, messageId, "Нет блюд с тегом «" + tag.getName() + "».",
                    KeyboardFactory.tagsFilter(tagService.findAll()));
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> {
            ids.add(r.getId());
            titles.add(r.getName());
        });
        sender.editText(chatId, messageId, "Тег «" + tag.getName() + "»:",
                KeyboardFactory.recipeListButtons(ids, titles, "filter:tag:" + tagId, page, recipes.getTotalPages()));
    }

    private void showFavorites(Long chatId, Integer messageId, User user, int page) {
        Page<FavoriteRecipe> favorites = favoriteService.list(user.getId(), page, PAGE_SIZE);
        if (favorites.isEmpty()) {
            sender.editText(chatId, messageId, "Избранное пусто.", KeyboardFactory.backToMenu());
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        favorites.forEach(f -> {
            ids.add(f.getRecipe().getId());
            titles.add(f.getRecipe().getName());
        });
        sender.editText(chatId, messageId, "Избранное:",
                KeyboardFactory.recipeListButtons(ids, titles, "list:fav", page, favorites.getTotalPages()));
    }

    private void showHistory(Long chatId, Integer messageId, User user, int page) {
        Page<CookingHistory> history = cookingHistoryService.list(user.getId(), page, PAGE_SIZE);
        if (history.isEmpty()) {
            sender.editText(chatId, messageId, "История пуста.", KeyboardFactory.backToMenu());
            return;
        }
        StringBuilder sb = new StringBuilder("История приготовления:\n\n");
        List<Long> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        history.forEach(h -> {
            ids.add(h.getRecipe().getId());
            String title = h.getRecipe().getName() + " · " + HISTORY_FMT.format(h.getCookedAt());
            titles.add(truncate(title, 40));
            sb.append("• ").append(h.getRecipe().getName())
                    .append(" — ").append(HISTORY_FMT.format(h.getCookedAt()));
            if (h.getRating() != null) {
                sb.append(" ⭐").append(h.getRating());
            }
            sb.append('\n');
        });
        sender.editText(chatId, messageId, sb.toString(),
                KeyboardFactory.recipeListButtons(ids, titles, "list:hist", page, history.getTotalPages()));
    }

    private void showSearchResults(Long chatId, String query, int page) {
        Page<Recipe> recipes = recipeService.search(query, page, PAGE_SIZE);
        if (recipes.isEmpty()) {
            sender.sendText(chatId, "Ничего не найдено по запросу: " + query, KeyboardFactory.backToMenu());
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> {
            ids.add(r.getId());
            titles.add(r.getName());
        });
        sender.sendText(chatId, "Результаты поиска: " + query,
                KeyboardFactory.recipeListButtons(ids, titles, "list:recipes", page, recipes.getTotalPages()));
    }

    private static IngredientLineDto parseIngredientLine(String text) {
        String[] parts = Arrays.stream(text.split("\\|")).map(String::trim).toArray(String[]::new);
        if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
            return null;
        }
        String amount = parts.length > 1 ? parts[1] : null;
        String unit = parts.length > 2 ? parts[2] : null;
        return new IngredientLineDto(parts[0].toLowerCase(Locale.ROOT), amount, unit);
    }

    private static String buildDraftPreview(RecipeFsmSession session) {
        StringBuilder sb = new StringBuilder("Проверьте рецепт:\n\n");
        sb.append("🍽 ").append(session.getName()).append('\n');
        if (StringUtils.hasText(session.getDescription())) {
            sb.append(session.getDescription()).append('\n');
        }
        if (session.getCookingTimeMinutes() != null) {
            sb.append("⏱ ").append(session.getCookingTimeMinutes()).append(" мин\n");
        }
        if (!session.getIngredients().isEmpty()) {
            sb.append("\nИнгредиенты:\n");
            session.getIngredients().forEach(i ->
                    sb.append("• ").append(i.name())
                            .append(i.amount() == null ? "" : " " + i.amount())
                            .append(i.unit() == null ? "" : " " + i.unit())
                            .append('\n'));
        }
        if (!session.getTags().isEmpty()) {
            sb.append("\n🏷 ").append(String.join(", ", session.getTags()));
        }
        if (StringUtils.hasText(session.getVideoFileId())) {
            sb.append("\n🎬 Видео прикреплено");
        } else {
            sb.append("\n🎬 Без видео");
        }
        return sb.toString();
    }

    private static int parsePage(String data, String prefix) {
        if (data.equals(prefix)) {
            return 0;
        }
        String suffix = data.substring(prefix.length());
        if (suffix.startsWith(":")) {
            return Integer.parseInt(suffix.substring(1));
        }
        return 0;
    }

    private static String displayName(Message message) {
        if (message.getFrom() == null) {
            return null;
        }
        String first = message.getFrom().getFirstName();
        String last = message.getFrom().getLastName();
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static String displayName(CallbackQuery callback) {
        String first = callback.getFrom().getFirstName();
        String last = callback.getFrom().getLastName();
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static Long extractChatId(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        return null;
    }

    private static Integer extractThreadId(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            var maybe = update.getCallbackQuery().getMessage();
            if (maybe instanceof Message message) {
                return message.getMessageThreadId();
            }
            return null;
        }
        if (update.hasMessage()) {
            return update.getMessage().getMessageThreadId();
        }
        return null;
    }

    private static String who(User user) {
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName();
        }
        return "Кто-то";
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
