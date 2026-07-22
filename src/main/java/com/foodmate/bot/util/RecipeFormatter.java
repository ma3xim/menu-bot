package com.foodmate.bot.util;

import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.RecipeIngredient;
import com.foodmate.bot.entity.Tag;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class RecipeFormatter {

    private RecipeFormatter() {
    }

    public static String formatCard(Recipe recipe, boolean favorite) {
        StringBuilder sb = new StringBuilder();
        sb.append("🍽 ").append(recipe.getName()).append('\n');
        if (StringUtils.hasText(recipe.getDescription())) {
            sb.append('\n').append(recipe.getDescription()).append('\n');
        }
        if (recipe.getCookingTimeMinutes() != null) {
            sb.append("\n⏱ ").append(recipe.getCookingTimeMinutes()).append(" мин");
        }
        if (recipe.getRatingsCount() > 0) {
            sb.append("\n⭐ ").append(String.format("%.1f", recipe.getAverageRating()))
                    .append(" (").append(recipe.getRatingsCount()).append(")");
        }
        if (favorite) {
            sb.append("\n❤ В избранном");
        }
        if (StringUtils.hasText(recipe.getVideoFileId())) {
            sb.append("\n🎬 Есть видео");
        }
        if (recipe.getTags() != null && !recipe.getTags().isEmpty()) {
            String tags = recipe.getTags().stream().map(Tag::getName).sorted().collect(Collectors.joining(", "));
            sb.append("\n🏷 ").append(tags);
        }
        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
            sb.append("\n\nИнгредиенты:\n");
            for (RecipeIngredient ri : recipe.getIngredients()) {
                sb.append("• ").append(ri.getIngredient().getName());
                if (StringUtils.hasText(ri.getAmount()) || StringUtils.hasText(ri.getUnit())) {
                    sb.append(" — ");
                    if (StringUtils.hasText(ri.getAmount())) {
                        sb.append(ri.getAmount());
                    }
                    if (StringUtils.hasText(ri.getUnit())) {
                        if (StringUtils.hasText(ri.getAmount())) {
                            sb.append(' ');
                        }
                        sb.append(ri.getUnit());
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    public static String formatListItem(Recipe recipe) {
        String time = recipe.getCookingTimeMinutes() == null ? "" : " (" + recipe.getCookingTimeMinutes() + " мин)";
        return "• " + recipe.getName() + time;
    }
}
