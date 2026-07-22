package com.foodmate.bot.service;

import com.foodmate.bot.entity.CookingHistory;
import com.foodmate.bot.entity.DishOfTheDay;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.repository.CookingHistoryRepository;
import com.foodmate.bot.repository.RecipeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final RecipeRepository recipeRepository;
    private final CookingHistoryRepository cookingHistoryRepository;
    private final DishOfTheDayService dishOfTheDayService;

    @Transactional(readOnly = true)
    public String buildStatsReport() {
        StringBuilder sb = new StringBuilder("📊 Статистика\n\n");

        dishOfTheDayService.findLatest().ifPresentOrElse(
                d -> {
                    String by = d.getSetBy() != null && StringUtils.hasText(d.getSetBy().getDisplayName())
                            ? d.getSetBy().getDisplayName()
                            : "кто-то";
                    sb.append("📌 Блюдо дня: ").append(d.getRecipe().getName())
                            .append(" (от ").append(by).append(")\n\n");
                },
                () -> sb.append("📌 Блюдо дня пока не выбрано\n\n")
        );

        List<Recipe> top = recipeRepository.findTopRated(PageRequest.of(0, 5));
        sb.append("🏆 Топ по оценкам:\n");
        if (top.isEmpty()) {
            sb.append("пока нет оценок\n");
        } else {
            int i = 1;
            for (Recipe r : top) {
                long cooks = cookingHistoryRepository.countByRecipeId(r.getId());
                sb.append(i++).append(". ").append(r.getName())
                        .append(" — ⭐ ").append(String.format("%.1f", r.getAverageRating()))
                        .append(" (").append(r.getRatingsCount()).append(" оценок, ")
                        .append(cooks).append(" раз готовили)\n");
            }
        }

        sb.append("\n💬 Последние отзывы:\n");
        var comments = cookingHistoryRepository.findByCommentIsNotNullOrderByCookedAtDesc(PageRequest.of(0, 5));
        if (comments.isEmpty()) {
            sb.append("пока нет комментариев\n");
        } else {
            for (CookingHistory h : comments) {
                String who = h.getUser() != null && StringUtils.hasText(h.getUser().getDisplayName())
                        ? h.getUser().getDisplayName()
                        : "Кто-то";
                String rating = h.getRating() == null ? "" : " " + h.getRating() + "⭐";
                sb.append("• ").append(h.getRecipe().getName()).append(rating)
                        .append(" — ").append(who).append(": ")
                        .append(truncate(h.getComment(), 80)).append('\n');
            }
        }

        sb.append("\n⭐ Последние оценки:\n");
        var ratings = cookingHistoryRepository.findByRatingIsNotNullOrderByCookedAtDesc(PageRequest.of(0, 5));
        if (ratings.isEmpty()) {
            sb.append("пока нет оценок\n");
        } else {
            for (CookingHistory h : ratings) {
                String who = h.getUser() != null && StringUtils.hasText(h.getUser().getDisplayName())
                        ? h.getUser().getDisplayName()
                        : "Кто-то";
                sb.append("• ").append(h.getRecipe().getName())
                        .append(" — ").append(who).append(": ").append(h.getRating()).append("⭐\n");
            }
        }

        return sb.toString().trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
