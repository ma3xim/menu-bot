package com.foodmate.bot.service;

import com.foodmate.bot.entity.CookingHistory;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.CookingHistoryRepository;
import com.foodmate.bot.repository.RecipeRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CookingHistoryService {

    private final CookingHistoryRepository cookingHistoryRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;

    @Transactional
    public CookingHistory markCooked(User user, Long recipeId, Integer rating) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NotFoundException("Рецепт не найден"));
        CookingHistory history = new CookingHistory();
        history.setUser(user);
        history.setRecipe(recipe);
        history.setCookedAt(Instant.now());
        history.setRating(rating);
        CookingHistory saved = cookingHistoryRepository.save(history);
        if (rating != null) {
            recipeService.applyRating(recipe, rating);
        }
        return saved;
    }

    @Transactional
    public CookingHistory addComment(Long historyId, String comment) {
        CookingHistory history = cookingHistoryRepository.findById(historyId)
                .orElseThrow(() -> new NotFoundException("Запись истории не найдена"));
        history.setComment(comment);
        return cookingHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public CookingHistory getWithRecipe(Long historyId) {
        return cookingHistoryRepository.findById(historyId)
                .orElseThrow(() -> new NotFoundException("Запись истории не найдена"));
    }

    @Transactional(readOnly = true)
    public Page<CookingHistory> list(Long userId, int page, int size) {
        return cookingHistoryRepository.findByUserIdOrderByCookedAtDesc(userId, PageRequest.of(page, size));
    }
}
