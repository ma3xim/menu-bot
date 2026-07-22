package com.foodmate.bot.service;

import com.foodmate.bot.entity.FavoriteRecipe;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.FavoriteRecipeRepository;
import com.foodmate.bot.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final RecipeRepository recipeRepository;

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long recipeId) {
        return favoriteRecipeRepository.existsByUserIdAndRecipeId(userId, recipeId);
    }

    @Transactional
    public boolean toggle(User user, Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NotFoundException("Рецепт не найден"));
        var existing = favoriteRecipeRepository.findByUserIdAndRecipeId(user.getId(), recipeId);
        if (existing.isPresent()) {
            favoriteRecipeRepository.delete(existing.get());
            return false;
        }
        FavoriteRecipe favorite = new FavoriteRecipe();
        favorite.setUser(user);
        favorite.setRecipe(recipe);
        favoriteRecipeRepository.save(favorite);
        return true;
    }

    @Transactional(readOnly = true)
    public Page<FavoriteRecipe> list(Long userId, int page, int size) {
        return favoriteRecipeRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }
}
