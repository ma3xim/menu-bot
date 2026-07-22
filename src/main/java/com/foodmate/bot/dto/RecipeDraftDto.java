package com.foodmate.bot.dto;

import java.util.List;

public record RecipeDraftDto(
        String name,
        String description,
        Integer cookingTimeMinutes,
        List<IngredientLineDto> ingredients,
        List<String> tags,
        String videoFileId,
        String videoFileUniqueId,
        String videoKind
) {
}
