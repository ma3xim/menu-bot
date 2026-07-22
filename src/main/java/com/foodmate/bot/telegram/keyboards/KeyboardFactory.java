package com.foodmate.bot.telegram.keyboards;

import com.foodmate.bot.entity.Tag;
import com.foodmate.bot.telegram.callback.CallbackData;
import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

public final class KeyboardFactory {

    private KeyboardFactory() {
    }

    public static InlineKeyboardMarkup mainMenu() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(btn("🎲 Случайное блюдо", CallbackData.DISH_RANDOM)));
        rows.add(row(
                btn("📖 Рецепты", CallbackData.listRecipes(0)),
                btn("➕ Добавить", CallbackData.RECIPE_ADD)
        ));
        rows.add(row(
                btn("❤ Избранное", CallbackData.listFav(0)),
                btn("🕘 История", CallbackData.listHist(0))
        ));
        rows.add(row(
                btn("🔍 Поиск", CallbackData.SEARCH),
                btn("🏷 Фильтр", CallbackData.FILTER_TAGS)
        ));
        rows.add(row(
                btn("🛒 Покупки", CallbackData.SHOP_ALL),
                btn("📊 Статистика", CallbackData.STATS)
        ));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup recipeActions(long recipeId, boolean favorite) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(
                btn("⭐ Оценки и отзывы", CallbackData.recipeReviews(recipeId, 0)),
                btn("✅ Приготовили", CallbackData.recipeCooked(recipeId))
        ));
        rows.add(row(
                btn(favorite ? "💔 Убрать" : "❤ Любимое", CallbackData.recipeFav(recipeId)),
                btn("✏️ Изменить", CallbackData.recipeEdit(recipeId))
        ));
        rows.add(row(
                btn("🗑 Удалить", CallbackData.recipeDelete(recipeId)),
                btn("🛒 В покупки", CallbackData.shopRecipe(recipeId))
        ));
        rows.add(row(btn("📌 Блюдо дня", CallbackData.dishOfDay(recipeId))));
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup recipeReviews(long recipeId, int page, int totalPages) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) {
            nav.add(btn("⬅️", CallbackData.recipeReviews(recipeId, page - 1)));
        }
        if (page + 1 < totalPages) {
            nav.add(btn("➡️", CallbackData.recipeReviews(recipeId, page + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(new InlineKeyboardRow(nav));
        }
        rows.add(row(btn("⬅️ К рецепту", CallbackData.recipeView(recipeId))));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup ratingKeyboard(long recipeId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> stars = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            stars.add(btn(i + "⭐", CallbackData.recipeRate(recipeId, i)));
        }
        rows.add(new InlineKeyboardRow(stars));
        rows.add(row(btn("Пропустить", CallbackData.recipeSkipRate(recipeId))));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup confirmDelete(long recipeId) {
        return new InlineKeyboardMarkup(List.of(
                row(
                        btn("Да, удалить", CallbackData.recipeDeleteConfirm(recipeId)),
                        btn("Отмена", CallbackData.recipeView(recipeId))
                )
        ));
    }

    public static InlineKeyboardMarkup confirmAdd() {
        return new InlineKeyboardMarkup(List.of(
                row(
                        btn("✅ Сохранить", CallbackData.ADD_CONFIRM),
                        btn("❌ Отмена", CallbackData.ADD_CANCEL)
                )
        ));
    }

    public static InlineKeyboardMarkup pagination(String prefix, int page, int totalPages) {
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) {
            nav.add(btn("⬅️", prefix + ":" + (page - 1)));
        }
        nav.add(btn((page + 1) + "/" + Math.max(totalPages, 1), CallbackData.MENU_MAIN));
        if (page + 1 < totalPages) {
            nav.add(btn("➡️", prefix + ":" + (page + 1)));
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(nav));
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup tagsFilter(List<Tag> tags) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Tag tag : tags) {
            rows.add(row(btn(tag.getName(), CallbackData.filterTag(tag.getId()))));
        }
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup backToMenu() {
        return new InlineKeyboardMarkup(List.of(row(btn("⬅️ Меню", CallbackData.MENU_MAIN))));
    }

    public static InlineKeyboardMarkup shoppingList(boolean hasItems) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(
                btn("➕ Добавить", CallbackData.SHOP_MANUAL_ADD),
                btn("✏️ Редактировать", CallbackData.SHOP_EDIT)
        ));
        if (hasItems) {
            rows.add(row(btn("🗑 Очистить список", CallbackData.SHOP_CLEAR)));
        }
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup confirmClearShopping() {
        return new InlineKeyboardMarkup(List.of(
                row(
                        btn("Да, очистить", CallbackData.SHOP_CLEAR_OK),
                        btn("Отмена", CallbackData.SHOP_ALL)
                )
        ));
    }

    public static InlineKeyboardMarkup afterAddToShopping(long recipeId) {
        return new InlineKeyboardMarkup(List.of(
                row(
                        btn("🛒 Открыть список", CallbackData.SHOP_ALL),
                        btn("⬅️ К рецепту", CallbackData.recipeView(recipeId))
                ),
                row(btn("🏠 Меню", CallbackData.MENU_MAIN))
        ));
    }

    public static InlineKeyboardMarkup recipeListButtons(List<Long> ids, List<String> titles, String listCallbackPrefix, int page, int totalPages) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            rows.add(row(btn(titles.get(i), CallbackData.recipeView(ids.get(i)))));
        }
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) {
            nav.add(btn("⬅️", listCallbackPrefix + ":" + (page - 1)));
        }
        if (page + 1 < totalPages) {
            nav.add(btn("➡️", listCallbackPrefix + ":" + (page + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(new InlineKeyboardRow(nav));
        }
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardRow row(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(List.of(buttons));
    }

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
