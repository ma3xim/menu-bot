package com.foodmate.bot.service;

import com.foodmate.bot.dto.IngredientLineDto;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.RecipeIngredient;
import com.foodmate.bot.entity.ShoppingListItem;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.RecipeRepository;
import com.foodmate.bot.repository.ShoppingListItemRepository;
import java.util.ArrayList;
import java.util.Arrays;
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
            return "🛒 Список покупок пуст.\n\n"
                    + "Добавь из рецепта («В покупки») или нажми «Добавить» / «Редактировать» и напиши руками.";
        }

        Map<String, List<ShoppingListItem>> byName = new LinkedHashMap<>();
        for (ShoppingListItem item : items) {
            byName.computeIfAbsent(item.getIngredientName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(item);
        }

        StringBuilder sb = new StringBuilder("🛒 Список покупок:\n\n");
        byName.forEach((name, group) -> {
            if (group.size() == 1) {
                ShoppingListItem item = group.get(0);
                sb.append("• ").append(capitalize(name));
                String amount = formatAmount(item);
                if (StringUtils.hasText(amount)) {
                    sb.append(" — ").append(amount);
                }
                if (StringUtils.hasText(item.getRecipeName())) {
                    sb.append(" (").append(item.getRecipeName()).append(')');
                }
                sb.append('\n');
                return;
            }
            sb.append("• ").append(capitalize(name)).append('\n');
            for (ShoppingListItem item : group) {
                sb.append("   - ");
                String amount = formatAmount(item);
                if (StringUtils.hasText(amount)) {
                    sb.append(amount);
                } else {
                    sb.append("—");
                }
                if (StringUtils.hasText(item.getRecipeName())) {
                    sb.append(" (").append(item.getRecipeName()).append(')');
                }
                sb.append('\n');
            }
        });
        sb.append("\nПозиций: ").append(items.size());
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String formatEditableText() {
        List<ShoppingListItem> items = shoppingListItemRepository.findAll(Sort.by("ingredientName", "id"));
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ShoppingListItem item : items) {
            sb.append(item.getIngredientName());
            if (StringUtils.hasText(item.getAmount()) || StringUtils.hasText(item.getUnit())) {
                sb.append(" | ").append(item.getAmount() == null ? "" : item.getAmount());
                sb.append(" | ").append(item.getUnit() == null ? "" : item.getUnit());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
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
    public int addManual(String text, Long telegramId) {
        List<IngredientLineDto> lines = parseLines(text);
        if (lines.isEmpty()) {
            throw new BotBusinessException("Не вижу позиций. Пример:\nмолоко | 1 | л\nхлеб");
        }
        List<ShoppingListItem> toSave = lines.stream()
                .map(line -> toItem(line, telegramId))
                .toList();
        shoppingListItemRepository.saveAll(toSave);
        return toSave.size();
    }

    @Transactional
    public int replaceFromText(String text, Long telegramId) {
        List<IngredientLineDto> lines = parseLines(text);
        shoppingListItemRepository.deleteAllInBatch();
        if (lines.isEmpty()) {
            return 0;
        }
        List<ShoppingListItem> toSave = lines.stream()
                .map(line -> toItem(line, telegramId))
                .toList();
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

    private ShoppingListItem toItem(IngredientLineDto line, Long telegramId) {
        ShoppingListItem item = new ShoppingListItem();
        item.setIngredientName(line.name().toLowerCase(Locale.ROOT));
        item.setAmount(blankToNull(line.amount()));
        item.setUnit(blankToNull(line.unit()));
        item.setAddedByTelegramId(telegramId);
        return item;
    }

    private static List<IngredientLineDto> parseLines(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<IngredientLineDto> result = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (!StringUtils.hasText(line) || line.startsWith("#")) {
                continue;
            }
            IngredientLineDto parsed = parseLine(line);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static IngredientLineDto parseLine(String text) {
        String[] parts = Arrays.stream(text.split("\\|")).map(String::trim).toArray(String[]::new);
        if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
            return null;
        }
        String amount = parts.length > 1 ? parts[1] : null;
        String unit = parts.length > 2 ? parts[2] : null;
        return new IngredientLineDto(parts[0].toLowerCase(Locale.ROOT), amount, unit);
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
        return sb.toString();
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
