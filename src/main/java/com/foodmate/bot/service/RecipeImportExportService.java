package com.foodmate.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.bot.dto.IngredientLineDto;
import com.foodmate.bot.dto.RecipeDraftDto;
import com.foodmate.bot.dto.RecipeExportDto;
import com.foodmate.bot.dto.RecipesExportFile;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.BotBusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeImportExportService {

    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String exportAllAsJson() {
        try {
            List<RecipeExportDto> recipes = new ArrayList<>();
            for (Recipe recipe : recipeService.findAllDetailed()) {
                recipes.add(toExport(recipe));
            }
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new RecipesExportFile(recipes));
        } catch (Exception e) {
            throw new BotBusinessException("Не удалось экспортировать рецепты: " + e.getMessage());
        }
    }

    @Transactional
    public int importFromJson(String json, User author) {
        try {
            RecipesExportFile file = objectMapper.readValue(json, RecipesExportFile.class);
            if (file.recipes() == null || file.recipes().isEmpty()) {
                throw new BotBusinessException("В файле нет рецептов");
            }
            int imported = 0;
            for (RecipeExportDto dto : file.recipes()) {
                RecipeDraftDto draft = new RecipeDraftDto(
                        dto.name(),
                        dto.description(),
                        dto.cookingTimeMinutes(),
                        dto.ingredients() == null ? List.of() : dto.ingredients(),
                        dto.tags() == null ? List.of() : dto.tags(),
                        null,
                        null,
                        null
                );
                recipeService.create(draft, author);
                imported++;
            }
            return imported;
        } catch (BotBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BotBusinessException("Некорректный JSON импорта: " + e.getMessage());
        }
    }

    private static RecipeExportDto toExport(Recipe recipe) {
        List<IngredientLineDto> ingredients = recipe.getIngredients().stream()
                .map(ri -> new IngredientLineDto(
                        ri.getIngredient().getName(),
                        ri.getAmount(),
                        ri.getUnit()
                ))
                .toList();
        List<String> tags = recipe.getTags().stream().map(t -> t.getName()).toList();
        return new RecipeExportDto(
                recipe.getName(),
                recipe.getDescription(),
                recipe.getCookingTimeMinutes(),
                recipe.getAverageRating(),
                ingredients,
                tags
        );
    }
}
