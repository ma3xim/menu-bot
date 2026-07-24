package com.foodmate.bot.telegram;

import com.foodmate.bot.config.BotProperties;
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
import com.foodmate.bot.telegram.fsm.EditField;
import com.foodmate.bot.telegram.fsm.RecipeFsmService;
import com.foodmate.bot.telegram.fsm.RecipeFsmSession;
import com.foodmate.bot.telegram.fsm.RecipeFsmState;
import com.foodmate.bot.telegram.keyboards.KeyboardFactory;
import com.foodmate.bot.util.RecipeFormatter;
import java.time.Instant;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateRouter {

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter HISTORY_PATTERN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
    private final PendingShoppingService pendingShoppingService;
    private final PendingAddUserService pendingAddUserService;
    private final StatsService statsService;
    private final DishOfTheDayService dishOfTheDayService;
    private final BotProperties botProperties;

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
                clearPending(telegramId);
                sender.sendText(chatId, "Привет! Я FoodMate — помогу выбрать блюдо на день.", mainMenuFor(telegramId));
                return;
            }
            if (text.startsWith("/menu")) {
                clearPending(telegramId);
                sender.sendText(chatId, "Главное меню:", mainMenuFor(telegramId));
                return;
            }
            if (text.startsWith("/cancel")) {
                clearPending(telegramId);
                sender.sendText(chatId, "Отменено.", mainMenuFor(telegramId));
                return;
            }

            if (pendingAddUserService.isWaiting(telegramId)) {
                handleAddUserInput(chatId, telegramId, text);
                return;
            }

            if (pendingCommentService.getHistoryId(telegramId).isPresent()) {
                accessService.requireCanWrite(telegramId);
                handleCommentInput(chatId, user, text);
                return;
            }

            var shoppingMode = pendingShoppingService.getMode(telegramId);
            if (shoppingMode.isPresent()) {
                accessService.requireCanWrite(telegramId);
                handleShoppingInput(chatId, user, shoppingMode.get(), text);
                return;
            }

            var sessionOpt = fsmService.get(telegramId);
            if (sessionOpt.isPresent()) {
                accessService.requireCanWrite(telegramId);
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
        if (sessionOpt.isPresent()) {
            RecipeFsmSession session = sessionOpt.get();
            if (session.getState() == RecipeFsmState.WAIT_VIDEO
                    || (session.getState() == RecipeFsmState.EDIT_FIELD && session.getEditField() == EditField.VIDEO)) {
                accessService.requireCanWrite(telegramId);
                if (attachMediaFromMessage(session, message)) {
                    if (session.getState() == RecipeFsmState.EDIT_FIELD) {
                        session.setState(RecipeFsmState.EDIT_HUB);
                        session.setEditField(null);
                        showEditHub(chatId, null, session);
                    } else {
                        goToConfirm(chatId, session);
                    }
                    return;
                }
                sender.sendText(chatId, "Пришлите видео или фото (можно переслать), '-' чтобы пропустить/очистить, или «удалить».");
                return;
            }
        }

        sender.sendText(chatId, "Не понял команду. Откройте меню:", mainMenuFor(telegramId));
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
            clearPending(telegramId);
            sender.editText(chatId, messageId, "Главное меню:", mainMenuFor(telegramId));
            return;
        }
        if (CallbackData.DISH_RANDOM.equals(data)) {
            showRandom(chatId, messageId, user, null);
            return;
        }
        if (CallbackData.SETTINGS.equals(data)) {
            accessService.requireSuper(telegramId);
            showSettings(chatId, messageId);
            return;
        }
        if (CallbackData.SETTINGS_ADD.equals(data)) {
            accessService.requireSuper(telegramId);
            pendingAddUserService.start(telegramId);
            sender.editText(chatId, messageId,
                    "Пришлите Telegram ID пользователя для доступа (только просмотр).\nОтмена: /cancel",
                    KeyboardFactory.backToMenu());
            return;
        }
        if (data.startsWith("settings:removeok:")) {
            accessService.requireSuper(telegramId);
            Long targetId = Long.parseLong(data.substring("settings:removeok:".length()));
            if (accessService.isSuper(targetId)) {
                throw new BotBusinessException("Нельзя отключить суперадмина.");
            }
            userService.deactivateViewer(targetId);
            showSettings(chatId, messageId);
            return;
        }
        if (data.startsWith("settings:remove:")) {
            accessService.requireSuper(telegramId);
            Long targetId = Long.parseLong(data.substring("settings:remove:".length()));
            if (accessService.isSuper(targetId)) {
                throw new BotBusinessException("Нельзя отключить суперадмина.");
            }
            User target = userService.requireByTelegramId(targetId);
            String whoLabel = StringUtils.hasText(target.getDisplayName())
                    ? target.getDisplayName() + " (" + targetId + ")"
                    : String.valueOf(targetId);
            sender.editText(chatId, messageId,
                    "Удалить доступ у пользователя " + whoLabel + "?",
                    KeyboardFactory.confirmRemoveAccess(targetId));
            return;
        }
        if (CallbackData.RECIPE_ADD.equals(data)) {
            accessService.requireCanWrite(telegramId);
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
            accessService.requireCanWrite(telegramId);
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
            List<Tag> tags = tagService.findAllUsed();
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
            accessService.requireCanWrite(telegramId);
            Long recipeId = Long.parseLong(data.substring("dish:day:".length()));
            dishOfTheDayService.setDishOfTheDay(recipeId, user);
            Recipe recipe = recipeService.getDetailed(recipeId);
            sender.editText(chatId, messageId,
                    "📌 «" + recipe.getName() + "» закреплено как блюдо дня в группе.",
                    recipeActionsFor(user, recipe));
            groupNotifyService.notify(who(user) + " выбрал(а) блюдо дня: «" + recipe.getName() + "»");
            return;
        }
        if (CallbackData.SHOP_ALL.equals(data)
                || CallbackData.SHOP_MANUAL_ADD.equals(data)
                || CallbackData.SHOP_EDIT.equals(data)
                || CallbackData.SHOP_CLEAR.equals(data)
                || CallbackData.SHOP_CLEAR_OK.equals(data)
                || data.startsWith("shop:add:")) {
            accessService.requireCanWrite(telegramId);
        }
        if (CallbackData.SHOP_ALL.equals(data)) {
            pendingShoppingService.clear(telegramId);
            String text = shoppingListService.formatCurrentList();
            boolean hasItems = shoppingListService.count() > 0;
            sender.editText(chatId, messageId, text, KeyboardFactory.shoppingList(hasItems));
            return;
        }
        if (CallbackData.SHOP_MANUAL_ADD.equals(data)) {
            pendingShoppingService.startAdd(telegramId);
            sender.editText(chatId, messageId,
                    "Напиши позиции для добавления — каждая с новой строки.\n"
                            + "Формат: название или название | кол-во | единица\n\n"
                            + "Пример:\nмолоко | 1 | л\nхлеб\nяйца | 10 | шт\n\n"
                            + "Отмена: /cancel",
                    KeyboardFactory.backToMenu());
            return;
        }
        if (CallbackData.SHOP_EDIT.equals(data)) {
            pendingShoppingService.startEdit(telegramId);
            String current = shoppingListService.formatEditableText();
            StringBuilder prompt = new StringBuilder();
            prompt.append("Пришли новый список целиком — он заменит текущий.\n")
                    .append("Формат: название или название | кол-во | единица\n")
                    .append("«-» очистит список.\n\n");
            if (StringUtils.hasText(current)) {
                prompt.append("Сейчас:\n").append(current).append("\n\n");
            } else {
                prompt.append("Сейчас список пуст.\n\n");
            }
            prompt.append("Отмена: /cancel");
            sender.editText(chatId, messageId, prompt.toString(), KeyboardFactory.backToMenu());
            return;
        }
        if (CallbackData.SHOP_CLEAR.equals(data)) {
            pendingShoppingService.clear(telegramId);
            sender.editText(chatId, messageId, "Очистить весь список покупок?", KeyboardFactory.confirmClearShopping());
            return;
        }
        if (CallbackData.SHOP_CLEAR_OK.equals(data)) {
            pendingShoppingService.clear(telegramId);
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
            accessService.requireCanWrite(telegramId);
            fsmService.clear(telegramId);
            sender.editText(chatId, messageId, "Отменено.", mainMenuFor(telegramId));
            return;
        }
        if (CallbackData.ADD_CONFIRM.equals(data)) {
            accessService.requireCanWrite(telegramId);
            confirmFsm(chatId, messageId, user);
            return;
        }
        if (data.startsWith("recipe:view:")) {
            showRecipe(chatId, messageId, user, Long.parseLong(data.substring("recipe:view:".length())));
            return;
        }
        if (data.startsWith("recipe:video:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:video:".length()));
            showRecipeMedia(chatId, user, recipeId);
            sender.answerCallback(callback.getId(), "Медиа");
            return;
        }
        if (data.startsWith("recipe:reviews:")) {
            String[] parts = data.split(":");
            Long recipeId = Long.parseLong(parts[2]);
            int page = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
            showRecipeReviews(chatId, messageId, recipeId, page);
            return;
        }
        if (data.startsWith("recipe:cooked:")
                || data.startsWith("recipe:rate:")
                || data.startsWith("recipe:skiprate:")
                || data.startsWith("recipe:fav:")
                || data.startsWith("recipe:edit:")
                || data.startsWith("edit:")
                || data.startsWith("recipe:del")) {
            accessService.requireCanWrite(telegramId);
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
            showRecipe(chatId, messageId, user, recipeId);
            sender.answerCallback(callback.getId(), nowFavorite ? "В избранном" : "Убрано");
            return;
        }
        if (data.startsWith("recipe:edit:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:edit:".length()));
            Recipe existing = recipeService.getDetailed(recipeId);
            RecipeFsmSession session = fsmService.startEdit(telegramId, existing);
            showEditHub(chatId, messageId, session);
            return;
        }
        if (data.startsWith("edit:field:")) {
            accessService.requireCanWrite(telegramId);
            RecipeFsmSession session = fsmService.get(telegramId)
                    .orElseThrow(() -> new BotBusinessException("Нет сессии редактирования"));
            String field = data.substring("edit:field:".length());
            startEditField(chatId, messageId, session, field);
            return;
        }
        if (CallbackData.EDIT_SAVE.equals(data)) {
            accessService.requireCanWrite(telegramId);
            saveEditSession(chatId, messageId, user);
            return;
        }
        if (CallbackData.EDIT_CANCEL.equals(data)) {
            accessService.requireCanWrite(telegramId);
            RecipeFsmSession session = fsmService.get(telegramId).orElse(null);
            Long recipeId = session != null ? session.getEditingRecipeId() : null;
            fsmService.clear(telegramId);
            if (recipeId != null) {
                showRecipe(chatId, messageId, user, recipeId);
            } else {
                sender.editText(chatId, messageId, "Отменено.", mainMenuFor(telegramId));
            }
            return;
        }
        if (data.startsWith("recipe:delok:")) {
            Long recipeId = Long.parseLong(data.substring("recipe:delok:".length()));
            recipeService.delete(recipeId);
            sender.editText(chatId, messageId, "Рецепт удалён.", mainMenuFor(telegramId));
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
            sender.sendText(chatId, "Ок, без комментария.", mainMenuFor(user.getTelegramId()));
            return;
        }
        CookingHistory history = cookingHistoryService.addComment(historyId, text.trim());
        Recipe recipe = recipeService.getDetailed(history.getRecipe().getId());
        sender.sendText(chatId, "Комментарий сохранён.", mainMenuFor(user.getTelegramId()));
        groupNotifyService.notify(who(user) + " оставил(а) отзыв к «" + recipe.getName() + "»: " + truncate(text.trim(), 120));
    }

    private void handleShoppingInput(Long chatId, User user, PendingShoppingService.Mode mode, String text) {
        pendingShoppingService.clear(user.getTelegramId());
        if (mode == PendingShoppingService.Mode.ADD) {
            int added = shoppingListService.addManual(text, user.getTelegramId());
            long total = shoppingListService.count();
            sender.sendText(chatId, shoppingListService.formatCurrentList(),
                    KeyboardFactory.shoppingList(total > 0));
            groupNotifyService.notify(who(user) + " добавил(а) в покупки " + added
                    + " поз. вручную (всего " + total + ")");
            return;
        }

        String payload = "-".equals(text.trim()) ? "" : text;
        int total = shoppingListService.replaceFromText(payload, user.getTelegramId());
        sender.sendText(chatId, shoppingListService.formatCurrentList(),
                KeyboardFactory.shoppingList(total > 0));
        if (total == 0) {
            groupNotifyService.notify(who(user) + " очистил(а) список покупок (редактирование)");
        } else {
            groupNotifyService.notify(who(user) + " обновил(а) список покупок вручную (" + total + " поз.)");
        }
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
                if (!"-".equals(text.trim())) {
                    Integer minutes = parseCookingMinutes(text);
                    if (minutes == null) {
                        sender.sendText(chatId, "Нужно число минут (например 70 или 60-80) или '-'");
                        return;
                    }
                    session.setCookingTimeMinutes(minutes);
                }
                session.setState(RecipeFsmState.WAIT_INGREDIENTS);
                sender.sendText(chatId, """
                        Введите ингредиенты по одному в строке (можно сразу списком):
                        название | количество | единица
                        Например:
                        яйца | 3 | шт
                        молоко | 50 | мл
                        '-' — закончить список.""");
            }
            case WAIT_INGREDIENTS -> {
                if ("-".equals(text.trim())) {
                    session.setState(RecipeFsmState.WAIT_INSTRUCTIONS);
                    sender.sendText(chatId, """
                            Введите способ приготовления.
                            Или '-' чтобы пропустить (например, если всё есть на фото/видео).""");
                    return;
                }
                int added = 0;
                for (String raw : text.split("\\R")) {
                    String line = raw.trim();
                    if (!StringUtils.hasText(line) || "-".equals(line)) {
                        continue;
                    }
                    IngredientLineDto parsed = parseIngredientLine(line);
                    if (parsed == null) {
                        continue;
                    }
                    session.getIngredients().add(parsed);
                    added++;
                }
                if (added == 0) {
                    sender.sendText(chatId, "Не распознал ингредиенты. Формат: название | количество | единица");
                    return;
                }
                sender.sendText(chatId, "Добавлено: " + added + ". Ещё ингредиенты или '-' для продолжения.");
            }
            case WAIT_INSTRUCTIONS -> {
                session.setCookingInstructions("-".equals(text.trim()) ? null : text.trim());
                session.setState(RecipeFsmState.WAIT_TAGS);
                sender.sendText(chatId, "Введите теги через запятую (или '-'):");
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
                        Пришлите видео или фото к рецепту (можно переслать из другого чата).
                        Или '-' чтобы пропустить.
                        Или «удалить» — убрать вложение (при редактировании).""");
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
                sender.sendText(chatId, "Нужно видео или фото (можно переслать), '-' или «удалить».");
            }
            case CONFIRM -> sender.sendText(chatId, "Нажмите «Сохранить» или «Отмена».", KeyboardFactory.confirmAdd());
            case EDIT_HUB -> sender.sendText(chatId, "Выберите, что изменить, кнопками ниже.\nИли «Сохранить» / «Отменить».",
                    KeyboardFactory.editHub());
            case EDIT_FIELD -> handleEditFieldInput(chatId, session, text);
            default -> {
                fsmService.clear(user.getTelegramId());
                sender.sendText(chatId, "Сессия сброшена.", mainMenuFor(user.getTelegramId()));
            }
        }
    }

    private void handleEditFieldInput(Long chatId, RecipeFsmSession session, String text) {
        EditField field = session.getEditField();
        if (field == null) {
            session.setState(RecipeFsmState.EDIT_HUB);
            showEditHub(chatId, null, session);
            return;
        }
        String trimmed = text.trim();
        switch (field) {
            case NAME -> {
                if ("-".equals(trimmed) || !StringUtils.hasText(trimmed)) {
                    sender.sendText(chatId, "Название нельзя очистить. Пришлите новое название.");
                    return;
                }
                session.setName(trimmed);
            }
            case DESCRIPTION -> session.setDescription("-".equals(trimmed) ? null : trimmed);
            case TIME -> {
                if ("-".equals(trimmed)) {
                    session.setCookingTimeMinutes(null);
                } else {
                    Integer minutes = parseCookingMinutes(trimmed);
                    if (minutes == null) {
                        sender.sendText(chatId, "Нужно число минут (например 70 или 60-80) или '-'");
                        return;
                    }
                    session.setCookingTimeMinutes(minutes);
                }
            }
            case INGREDIENTS -> {
                if ("-".equals(trimmed)) {
                    session.setIngredients(new ArrayList<>());
                } else {
                    List<IngredientLineDto> lines = new ArrayList<>();
                    for (String raw : text.split("\\R")) {
                        String line = raw.trim();
                        if (!StringUtils.hasText(line) || "-".equals(line)) {
                            continue;
                        }
                        IngredientLineDto parsed = parseIngredientLine(line);
                        if (parsed != null) {
                            lines.add(parsed);
                        }
                    }
                    if (lines.isEmpty()) {
                        sender.sendText(chatId, "Не распознал ингредиенты. Формат: название | количество | единица");
                        return;
                    }
                    session.setIngredients(lines);
                }
            }
            case INSTRUCTIONS -> session.setCookingInstructions("-".equals(trimmed) ? null : trimmed);
            case TAGS -> {
                if ("-".equals(trimmed)) {
                    session.setTags(new ArrayList<>());
                } else {
                    session.setTags(Arrays.stream(trimmed.split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList());
                }
            }
            case VIDEO -> {
                if ("удалить".equalsIgnoreCase(trimmed) || "-".equals(trimmed)) {
                    session.setVideoFileId(null);
                    session.setVideoFileUniqueId(null);
                    session.setVideoKind(null);
                } else {
                    sender.sendText(chatId, "Пришлите видео или фото, или '-' / «удалить» чтобы убрать.");
                    return;
                }
            }
        }
        session.setEditField(null);
        session.setState(RecipeFsmState.EDIT_HUB);
        showEditHub(chatId, null, session);
    }

    private void startEditField(Long chatId, Integer messageId, RecipeFsmSession session, String fieldKey) {
        EditField field = switch (fieldKey) {
            case "name" -> EditField.NAME;
            case "description" -> EditField.DESCRIPTION;
            case "time" -> EditField.TIME;
            case "ingredients" -> EditField.INGREDIENTS;
            case "instructions" -> EditField.INSTRUCTIONS;
            case "tags" -> EditField.TAGS;
            case "video" -> EditField.VIDEO;
            default -> throw new BotBusinessException("Неизвестное поле");
        };
        session.setEditField(field);
        session.setState(RecipeFsmState.EDIT_FIELD);
        String prompt = buildEditFieldPrompt(session, field);
        if (messageId != null) {
            sender.editText(chatId, messageId, prompt, KeyboardFactory.editHub());
        } else {
            sender.sendText(chatId, prompt, KeyboardFactory.editHub());
        }
    }

    private static String buildEditFieldPrompt(RecipeFsmSession session, EditField field) {
        StringBuilder sb = new StringBuilder("Редактирование: ");
        switch (field) {
            case NAME -> {
                sb.append("название\n\nСейчас:\n").append(nullToDash(session.getName()));
                sb.append("\n\nПришлите новое название.");
            }
            case DESCRIPTION -> {
                sb.append("описание\n\nСейчас:\n").append(nullToDash(session.getDescription()));
                sb.append("\n\nПришлите новое описание или '-' чтобы очистить.");
            }
            case TIME -> {
                sb.append("время\n\nСейчас:\n")
                        .append(session.getCookingTimeMinutes() == null ? "—" : session.getCookingTimeMinutes() + " мин");
                sb.append("\n\nПришлите минуты (например 70 или 60-80) или '-' чтобы очистить.");
            }
            case INGREDIENTS -> {
                sb.append("ингредиенты\n\nСейчас:\n").append(formatIngredientsDraft(session));
                sb.append("\n\nПришлите новый список целиком (каждая строка: название | кол-во | единица).\n'-' — очистить список.");
            }
            case INSTRUCTIONS -> {
                sb.append("способ приготовления\n\nСейчас:\n").append(nullToDash(session.getCookingInstructions()));
                sb.append("\n\nПришлите новый текст или '-' чтобы очистить.");
            }
            case TAGS -> {
                sb.append("теги\n\nСейчас:\n")
                        .append(session.getTags() == null || session.getTags().isEmpty()
                                ? "—"
                                : String.join(", ", session.getTags()));
                sb.append("\n\nПришлите теги через запятую или '-' чтобы очистить.");
            }
            case VIDEO -> {
                sb.append("фото/видео\n\nСейчас: ").append(mediaStatusLabel(session.getVideoFileId(), session.getVideoKind()));
                sb.append("\n\nПришлите новое видео или фото, или '-' / «удалить» чтобы убрать.");
            }
        }
        return truncateForTelegram(sb.toString());
    }

    private void showEditHub(Long chatId, Integer messageId, RecipeFsmSession session) {
        String text = "✏️ Редактирование рецепта\n\n"
                + truncateForTelegram(buildDraftPreview(session))
                + "\n\nВыберите, что изменить:";
        if (messageId != null) {
            sender.editText(chatId, messageId, text, KeyboardFactory.editHub());
        } else {
            sender.sendText(chatId, text, KeyboardFactory.editHub());
        }
    }

    private void saveEditSession(Long chatId, Integer messageId, User user) {
        RecipeFsmSession session = fsmService.get(user.getTelegramId())
                .orElseThrow(() -> new BotBusinessException("Нет сессии редактирования"));
        if (session.getEditingRecipeId() == null) {
            throw new BotBusinessException("Нет рецепта для сохранения");
        }
        if (!StringUtils.hasText(session.getName())) {
            throw new BotBusinessException("Название обязательно");
        }
        RecipeDraftDto draft = new RecipeDraftDto(
                session.getName(),
                session.getDescription(),
                session.getCookingTimeMinutes(),
                session.getIngredients(),
                session.getCookingInstructions(),
                session.getTags(),
                session.getVideoFileId(),
                session.getVideoFileUniqueId(),
                session.getVideoKind()
        );
        Long recipeId = session.getEditingRecipeId();
        recipeService.update(recipeId, draft);
        fsmService.clear(user.getTelegramId());
        Recipe detailed = recipeService.getDetailed(recipeId);
        boolean fav = favoriteService.isFavorite(user.getId(), recipeId);
        String card = truncateForTelegram(RecipeFormatter.formatCard(detailed, fav));
        sender.editText(chatId, messageId, card, recipeActionsFor(user, detailed));
        groupNotifyService.notify(who(user) + " обновил(а) рецепт «" + detailed.getName() + "»");
    }

    private static String formatIngredientsDraft(RecipeFsmSession session) {
        if (session.getIngredients() == null || session.getIngredients().isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (IngredientLineDto i : session.getIngredients()) {
            sb.append("• ").append(i.name());
            if (i.amount() != null) {
                sb.append(" | ").append(i.amount());
            }
            if (i.unit() != null) {
                sb.append(" | ").append(i.unit());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private static String truncateForTelegram(String text) {
        if (text == null) {
            return "";
        }
        int max = 3500;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…";
    }

    private void goToConfirm(Long chatId, RecipeFsmSession session) {
        session.setState(RecipeFsmState.CONFIRM);
        sender.sendText(chatId, buildDraftPreview(session), KeyboardFactory.confirmAdd());
    }

    private boolean attachMediaFromMessage(RecipeFsmSession session, Message message) {
        if (message.hasPhoto() && message.getPhoto() != null && !message.getPhoto().isEmpty()) {
            var photo = message.getPhoto().get(message.getPhoto().size() - 1);
            session.setVideoFileId(photo.getFileId());
            session.setVideoFileUniqueId(photo.getFileUniqueId());
            session.setVideoKind("PHOTO");
            return true;
        }
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
        if (message.hasDocument() && message.getDocument().getMimeType() != null) {
            String mime = message.getDocument().getMimeType();
            if (mime.startsWith("video/")) {
                session.setVideoFileId(message.getDocument().getFileId());
                session.setVideoFileUniqueId(message.getDocument().getFileUniqueId());
                session.setVideoKind("DOCUMENT");
                return true;
            }
            if (mime.startsWith("image/")) {
                session.setVideoFileId(message.getDocument().getFileId());
                session.setVideoFileUniqueId(message.getDocument().getFileUniqueId());
                session.setVideoKind("IMAGE");
                return true;
            }
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
                session.getCookingInstructions(),
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
                recipeActionsFor(user, detailed));
        boolean edited = session.getEditingRecipeId() != null;
        groupNotifyService.notify(who(user) + (edited ? " обновил(а) рецепт «" : " добавил(а) новый рецепт «")
                + detailed.getName() + "»");
    }

    private void showRandom(Long chatId, Integer messageId, User user, Long tagId) {
        Recipe picked = recommendationService.requireRandom(tagId);
        Recipe recipe = recipeService.getDetailed(picked.getId());
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        sender.editText(chatId, messageId, RecipeFormatter.formatCard(recipe, fav),
                recipeActionsFor(user, recipe));
    }

    private void showRecipe(Long chatId, Integer messageId, User user, Long recipeId) {
        Recipe recipe = recipeService.getDetailed(recipeId);
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        sender.editText(chatId, messageId, RecipeFormatter.formatCard(recipe, fav),
                recipeActionsFor(user, recipe));
    }

    private void showRecipeMedia(Long chatId, User user, Long recipeId) {
        Recipe recipe = recipeService.getDetailed(recipeId);
        if (!StringUtils.hasText(recipe.getVideoFileId())) {
            sender.sendText(chatId, "У этого рецепта нет фото или видео.", recipeActionsFor(user, recipe));
            return;
        }
        sender.sendRecipeMedia(chatId, recipe.getVideoFileId(), recipe.getVideoKind());
        sender.sendText(chatId, "🍽 «" + recipe.getName() + "»", recipeActionsFor(user, recipe));
    }

    private void showRecipeReviews(Long chatId, Integer messageId, Long recipeId, int page) {
        Recipe recipe = recipeService.getDetailed(recipeId);
        Page<CookingHistory> reviews = cookingHistoryService.listReviews(recipeId, page, PAGE_SIZE);
        if (reviews.isEmpty()) {
            sender.editText(chatId, messageId,
                    "⭐ Оценки и отзывы — «" + recipe.getName() + "»\n\nПока нет оценок и отзывов.",
                    KeyboardFactory.recipeReviews(recipeId, 0, 0));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⭐ Оценки и отзывы — «").append(recipe.getName()).append("»\n");
        if (recipe.getRatingsCount() > 0) {
            sb.append("Средняя: ").append(String.format(Locale.ROOT, "%.1f", recipe.getAverageRating()))
                    .append(" (").append(recipe.getRatingsCount()).append(")\n");
        }
        sb.append('\n');
        for (CookingHistory h : reviews) {
            sb.append("• ").append(who(h.getUser()));
            if (h.getRating() != null) {
                sb.append(" — ").append(h.getRating()).append("⭐");
            }
            sb.append(" · ").append(formatLocal(h.getCookedAt())).append('\n');
            if (StringUtils.hasText(h.getComment())) {
                sb.append("  «").append(truncate(h.getComment(), 200)).append("»\n");
            }
            sb.append('\n');
        }
        if (reviews.getTotalPages() > 1) {
            sb.append("Стр. ").append(page + 1).append('/').append(reviews.getTotalPages());
        }
        sender.editText(chatId, messageId, sb.toString().trim(),
                KeyboardFactory.recipeReviews(recipeId, page, reviews.getTotalPages()));
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
                    KeyboardFactory.tagsFilter(tagService.findAllUsed()));
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
            String title = h.getRecipe().getName() + " · " + formatLocal(h.getCookedAt());
            titles.add(truncate(title, 40));
            sb.append("• ").append(h.getRecipe().getName())
                    .append(" — ").append(formatLocal(h.getCookedAt()));
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
        String normalized = text.trim();
        if (normalized.endsWith(" -") || normalized.endsWith("\t-") || normalized.equals("-")) {
            normalized = normalized.replaceAll("\\s*-\\s*$", "").trim();
        }
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] parts = Arrays.stream(normalized.split("\\|")).map(String::trim).toArray(String[]::new);
        if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
            return null;
        }
        String amount = parts.length > 1 && StringUtils.hasText(parts[1]) ? parts[1] : null;
        String unit = parts.length > 2 && StringUtils.hasText(parts[2]) ? parts[2] : null;
        return new IngredientLineDto(parts[0].toLowerCase(Locale.ROOT), amount, unit);
    }

    private static Integer parseCookingMinutes(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        java.util.List<Integer> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        if (numbers.isEmpty()) {
            return null;
        }
        if (numbers.size() >= 2) {
            return (numbers.get(0) + numbers.get(1)) / 2;
        }
        return numbers.get(0);
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
        if (StringUtils.hasText(session.getCookingInstructions())) {
            sb.append("\n👨‍🍳 Способ приготовления:\n").append(session.getCookingInstructions()).append('\n');
        }
        if (!session.getTags().isEmpty()) {
            sb.append("\n🏷 ").append(String.join(", ", session.getTags()));
        }
        if (StringUtils.hasText(session.getVideoFileId())) {
            sb.append('\n').append(mediaAttachedLabel(session.getVideoKind()));
        } else {
            sb.append("\nБез фото/видео");
        }
        return sb.toString();
    }

    private static String mediaStatusLabel(String fileId, String kind) {
        if (!StringUtils.hasText(fileId)) {
            return "нет";
        }
        return isPhotoKind(kind) ? "есть фото" : "есть видео";
    }

    private static String mediaAttachedLabel(String kind) {
        return isPhotoKind(kind) ? "🖼 Фото прикреплено" : "🎬 Видео прикреплено";
    }

    private static boolean isPhotoKind(String kind) {
        if (kind == null) {
            return false;
        }
        String normalized = kind.toUpperCase(Locale.ROOT);
        return "PHOTO".equals(normalized) || "IMAGE".equals(normalized);
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

    private void handleAddUserInput(Long chatId, Long adminTelegramId, String text) {
        pendingAddUserService.clear(adminTelegramId);
        Long targetId;
        try {
            targetId = Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            sender.sendText(chatId, "Нужен числовой Telegram ID.", mainMenuFor(adminTelegramId));
            return;
        }
        User added = userService.addViewer(targetId);
        List<User> viewers = userService.listActiveViewers();
        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (User viewer : viewers) {
            ids.add(viewer.getTelegramId());
            String label = viewer.getTelegramId()
                    + (StringUtils.hasText(viewer.getDisplayName()) ? " · " + viewer.getDisplayName() : "");
            labels.add(truncate(label, 40));
        }
        sender.sendText(chatId,
                "Доступ выдан: " + added.getTelegramId() + " (просмотр).\n\nНастройки:",
                KeyboardFactory.settings(ids, labels));
    }

    private void showSettings(Long chatId, Integer messageId) {
        List<User> viewers = userService.listActiveViewers();
        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        StringBuilder sb = new StringBuilder("⚙️ Настройки доступа\n\nСуперадмины:\n");
        for (Long id : botProperties.getSuperAdminIds()) {
            sb.append("• ").append(id).append(" (SUPER)\n");
        }
        sb.append("\nПользователи с просмотром:\n");
        if (viewers.isEmpty()) {
            sb.append("пока никого\n");
        } else {
            for (User viewer : viewers) {
                ids.add(viewer.getTelegramId());
                String label = viewer.getTelegramId()
                        + (StringUtils.hasText(viewer.getDisplayName()) ? " · " + viewer.getDisplayName() : "");
                labels.add(truncate(label, 40));
                sb.append("• ").append(label).append('\n');
            }
        }
        sb.append("\nНажмите на пользователя, чтобы отключить доступ.");
        sender.editText(chatId, messageId, sb.toString(), KeyboardFactory.settings(ids, labels));
    }

    private void clearPending(Long telegramId) {
        fsmService.clear(telegramId);
        pendingCommentService.clear(telegramId);
        pendingShoppingService.clear(telegramId);
        pendingAddUserService.clear(telegramId);
    }

    private InlineKeyboardMarkup mainMenuFor(Long telegramId) {
        return KeyboardFactory.mainMenu(accessService.isSuper(telegramId));
    }

    private InlineKeyboardMarkup recipeActionsFor(User user, Recipe recipe) {
        boolean fav = favoriteService.isFavorite(user.getId(), recipe.getId());
        boolean hasMedia = StringUtils.hasText(recipe.getVideoFileId());
        return KeyboardFactory.recipeActions(
                recipe.getId(),
                fav,
                accessService.isSuper(user.getTelegramId()),
                hasMedia,
                isPhotoKind(recipe.getVideoKind()));
    }

    private String formatLocal(Instant instant) {
        return HISTORY_PATTERN.withZone(ZoneId.of(botProperties.getTimezone())).format(instant);
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
