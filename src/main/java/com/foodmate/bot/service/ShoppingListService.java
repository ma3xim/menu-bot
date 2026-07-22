package com.foodmate.bot.service;

import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.RecipeIngredient;
import com.foodmate.bot.entity.ShoppingListItem;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.RecipeRepository;
import com.foodmate.bot.repository.ShoppingListItemRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private final ShoppingListItemRepository shoppingListItemRepository;
    private final RecipeRepository recipeRepository;

    @Transactional(readOnly = true)
    public String formatCurrentList() {
        List<ShoppingListItem> items = shoppingListItemRepository.findAll(Sort.by("ingredientName", "id"));
        if (items.isEmpty()) {
            return "🛒 Список покупок пуст.\n\nДобавляй ингредиенты кнопкой «В покупки» на карточке рецепта.";
        }

        Map<String, List<ShoppingListItem>> byName = new LinkedHashMap<>();
        for (ShoppingListItem item : items) {
            byName.computeIfAbsent(item.getIngredientName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(item);
        }

        StringBuilder sb = new StringBuilder("🛒 Список покупок:\n\n");
        byName.forEach((name, group) -> {
            sb.append("• ").append(capitalize(name)).append('\n');
            for (ShoppingListItem item : group) {
                sb.append("   - ").append(formatAmount(item));
                if (StringUtils.hasText(item.getRecipeName())) {
                    sb.append(" (").append(item.getRecipeName()).append(')');
                }
                sb.append('\n');
            }
        });
        sb.append("\nПозиций: ").append(items.size());
        return sb.toString();
    }

    @Transactional
    public int addFromRecipe(Long recipeId, Long telegramId) {
        Recipe recipe = recipeRepository.findDetailedById(recipeId)
                .orElseThrow(() -> new NotFoundException("Рецепт не найден"));
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            throw new BotBusinessException("У рецепта нет ингредиентов");
        }

        List<ShoppingListItem> toSave = new ArrayList<>();
        for (RecipeIngredient ri : recipe.getIngredients()) {
            ShoppingListItem item = new ShoppingListItem();
            item.setIngredientName(ri.getIngredient().getName().toLowerCase(Locale.ROOT));
            item.setAmount(blankToNull(ri.getAmount()));
            item.setUnit(blankToNull(ri.getUnit()));
            item.setRecipeId(recipe.getId());
            item.setRecipeName(recipe.getName());
            item.setAddedByTelegramId(telegramId);
            toSave.add(item);
        }
        shoppingListItemRepository.saveAll(toSave);
        return toSave.size();
    }

    @Transactional
    public int clearAll() {
        long count = shoppingListItemRepository.count();
        shoppingListItemRepository.deleteAllInBatch();
        return (int) count;
    }

    @Transactional(readOnly = true)
    public long count() {
        return shoppingListItemRepository.count();
    }

    private static String formatAmount(ShoppingListItem item) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(item.getAmount())) {
            sb.append(item.getAmount());
        }
        if (StringUtils.hasText(item.getUnit())) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(item.getUnit());
        }
        return sb.isEmpty() ? "по вкусу" : sb.toString();
    }

    private static String capitalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
