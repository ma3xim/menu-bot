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

    public static InlineKeyboardMarkup mainMenu(boolean superUser) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(btn("🎲 Случайное блюдо", CallbackData.DISH_RANDOM)));
        if (superUser) {
            rows.add(row(
                    btn("📖 Рецепты", CallbackData.listRecipes(0)),
                    btn("➕ Добавить", CallbackData.RECIPE_ADD)
            ));
            rows.add(row(
                    btn("❤ Избранное", CallbackData.listFav(0)),
                    btn("🕘 История", CallbackData.listHist(0))
            ));
        } else {
            rows.add(row(btn("📖 Рецепты", CallbackData.listRecipes(0))));
            rows.add(row(btn("🕘 История", CallbackData.listHist(0))));
        }
        rows.add(row(
                btn("🔍 Поиск", CallbackData.SEARCH),
                btn("🏷 Фильтр", CallbackData.FILTER_TAGS)
        ));
        if (superUser) {
            rows.add(row(
                    btn("🛒 Покупки", CallbackData.SHOP_ALL),
                    btn("📊 Статистика", CallbackData.STATS)
            ));
            rows.add(row(btn("⚙️ Настройки", CallbackData.SETTINGS)));
        } else {
            rows.add(row(btn("📊 Статистика", CallbackData.STATS)));
        }
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup recipeActions(long recipeId, boolean favorite, boolean superUser) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (superUser) {
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
        } else {
            rows.add(row(btn("⭐ Оценки и отзывы", CallbackData.recipeReviews(recipeId, 0))));
        }
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup settings(List<Long> viewerTelegramIds, List<String> labels) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(btn("➕ Добавить пользователя", CallbackData.SETTINGS_ADD)));
        for (int i = 0; i < viewerTelegramIds.size(); i++) {
            rows.add(row(btn("🗑 " + labels.get(i), CallbackData.settingsRemove(viewerTelegramIds.get(i)))));
        }
        rows.add(row(btn("⬅️ Меню", CallbackData.MENU_MAIN)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup confirmRemoveAccess(long telegramId) {
        return new InlineKeyboardMarkup(List.of(
                row(
                        btn("Да, отключить", CallbackData.settingsRemoveConfirm(telegramId)),
                        btn("Отмена", CallbackData.SETTINGS)
                )
        ));
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

    public static InlineKeyboardMarkup editHub() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(
                btn("Название", CallbackData.editField("name")),
                btn("Описание", CallbackData.editField("description"))
        ));
        rows.add(row(
                btn("Время", CallbackData.editField("time")),
                btn("Ингредиенты", CallbackData.editField("ingredients"))
        ));
        rows.add(row(
                btn("Способ", CallbackData.editField("instructions")),
                btn("Теги", CallbackData.editField("tags"))
        ));
        rows.add(row(btn("Видео", CallbackData.editField("video"))));
        rows.add(row(
                btn("✅ Сохранить", CallbackData.EDIT_SAVE),
                btn("❌ Отменить", CallbackData.EDIT_CANCEL)
        ));
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
