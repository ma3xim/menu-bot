package com.foodmate.bot.dto;

import java.util.List;

public record RecipeExportDto(
        String name,
        String description,
        Integer cookingTimeMinutes,
        Double averageRating,
        List<IngredientLineDto> ingredients,
        List<String> tags
) {
}
