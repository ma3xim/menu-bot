package com.foodmate.bot.service;

import com.foodmate.bot.dto.IngredientLineDto;
import com.foodmate.bot.dto.RecipeDraftDto;
import com.foodmate.bot.entity.Ingredient;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.RecipeIngredient;
import com.foodmate.bot.entity.Tag;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.IngredientRepository;
import com.foodmate.bot.repository.RecipeRepository;
import com.foodmate.bot.repository.TagRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public Recipe getDetailed(Long id) {
        Recipe recipe = recipeRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Рецепт не найден"));
        recipe.getTags().size();
        return recipe;
    }

    @Transactional(readOnly = true)
    public Page<Recipe> list(int page, int size) {
        return recipeRepository.findAllByOrderByNameAsc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Recipe> search(String query, int page, int size) {
        if (!StringUtils.hasText(query)) {
            return list(page, size);
        }
        return recipeRepository.search(query.trim(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Recipe> findByTag(Long tagId, int page, int size) {
        return recipeRepository.findByTagId(tagId, PageRequest.of(page, size));
    }

    @Transactional
    public Recipe create(RecipeDraftDto draft, User author) {
        validateDraft(draft);
        Recipe recipe = new Recipe();
        applyDraft(recipe, draft);
        recipe.setCreatedBy(author);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe update(Long id, RecipeDraftDto draft) {
        validateDraft(draft);
        Recipe recipe = getDetailed(id);
        recipe.clearIngredients();
        recipe.getTags().clear();
        applyDraft(recipe, draft);
        Recipe saved = recipeRepository.saveAndFlush(recipe);
        tagRepository.deleteUnused();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!recipeRepository.existsById(id)) {
            throw new NotFoundException("Рецепт не найден");
        }
        recipeRepository.deleteById(id);
        recipeRepository.flush();
        tagRepository.deleteUnused();
    }

    @Transactional
    public void applyRating(Recipe recipe, int rating) {
        if (rating < 1 || rating > 5) {
            throw new BotBusinessException("Оценка должна быть от 1 до 5");
        }
        double total = recipe.getAverageRating() * recipe.getRatingsCount() + rating;
        int count = recipe.getRatingsCount() + 1;
        recipe.setRatingsCount(count);
        recipe.setAverageRating(total / count);
        recipeRepository.save(recipe);
    }

    @Transactional(readOnly = true)
    public List<Recipe> findAllDetailed() {
        return recipeRepository.findAll().stream()
                .map(r -> getDetailed(r.getId()))
                .toList();
    }

    private void applyDraft(Recipe recipe, RecipeDraftDto draft) {
        recipe.setName(draft.name().trim());
        recipe.setDescription(draft.description() == null ? null : draft.description().trim());
        recipe.setCookingTimeMinutes(draft.cookingTimeMinutes());
        recipe.setCookingInstructions(blankToNull(draft.cookingInstructions()));
        recipe.setVideoFileId(blankToNull(draft.videoFileId()));
        recipe.setVideoFileUniqueId(blankToNull(draft.videoFileUniqueId()));
        recipe.setVideoKind(blankToNull(draft.videoKind()));

        if (draft.ingredients() != null) {
            for (IngredientLineDto line : draft.ingredients()) {
                Ingredient ingredient = findOrCreateIngredient(line.name());
                RecipeIngredient ri = new RecipeIngredient();
                ri.setIngredient(ingredient);
                ri.setAmount(blankToNull(line.amount()));
                ri.setUnit(blankToNull(line.unit()));
                recipe.addIngredient(ri);
            }
        }

        Set<Tag> tags = new HashSet<>();
        if (draft.tags() != null) {
            for (String tagName : draft.tags()) {
                if (StringUtils.hasText(tagName)) {
                    tags.add(findOrCreateTag(tagName.trim()));
                }
            }
        }
        recipe.setTags(tags);
    }

    private Ingredient findOrCreateIngredient(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return ingredientRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Ingredient ingredient = new Ingredient();
                    ingredient.setName(normalized);
                    return ingredientRepository.save(ingredient);
                });
    }

    private Tag findOrCreateTag(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return tagRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setName(normalized);
                    return tagRepository.save(tag);
                });
    }

    private void validateDraft(RecipeDraftDto draft) {
        if (draft == null || !StringUtils.hasText(draft.name())) {
            throw new BotBusinessException("Название рецепта обязательно");
        }
        if (draft.cookingTimeMinutes() != null && draft.cookingTimeMinutes() < 0) {
            throw new BotBusinessException("Время приготовления не может быть отрицательным");
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
