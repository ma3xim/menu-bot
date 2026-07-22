package com.foodmate.bot.telegram.callback;

public final class CallbackData {

    public static final String MENU_MAIN = "menu:main";
    public static final String DISH_RANDOM = "dish:random";
    public static final String DISH_AGAIN = "dish:again";
    public static final String RECIPE_ADD = "recipe:add";
    public static final String RECIPE_LIST = "list:recipes";
    public static final String FAV_LIST = "list:fav";
    public static final String HIST_LIST = "list:hist";
    public static final String SEARCH = "search:start";
    public static final String FILTER_TAGS = "filter:tags";
    public static final String SHOP_ALL = "shop:all";
    public static final String SHOP_CLEAR = "shop:clear";
    public static final String SHOP_CLEAR_OK = "shop:clearok";
    public static final String STATS = "stats:show";
    public static final String ADD_CONFIRM = "add:confirm";
    public static final String ADD_CANCEL = "add:cancel";

    private CallbackData() {
    }

    public static String recipeView(long id) {
        return "recipe:view:" + id;
    }

    public static String recipeCooked(long id) {
        return "recipe:cooked:" + id;
    }

    public static String recipeFav(long id) {
        return "recipe:fav:" + id;
    }

    public static String recipeEdit(long id) {
        return "recipe:edit:" + id;
    }

    public static String recipeDelete(long id) {
        return "recipe:del:" + id;
    }

    public static String recipeDeleteConfirm(long id) {
        return "recipe:delok:" + id;
    }

    public static String recipeRate(long id, int rating) {
        return "recipe:rate:" + id + ":" + rating;
    }

    public static String recipeSkipRate(long id) {
        return "recipe:skiprate:" + id;
    }

    public static String listRecipes(int page) {
        return "list:recipes:" + page;
    }

    public static String listFav(int page) {
        return "list:fav:" + page;
    }

    public static String listHist(int page) {
        return "list:hist:" + page;
    }

    public static String filterTag(long tagId) {
        return "filter:tag:" + tagId;
    }

    public static String shopRecipe(long id) {
        return "shop:add:" + id;
    }

    public static String dishOfDay(long id) {
        return "dish:day:" + id;
    }
}
