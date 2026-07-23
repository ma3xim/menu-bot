package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.entity.Recipe;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.repository.RecipeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecipeRepository recipeRepository;
    private final BotProperties botProperties;

    @Transactional(readOnly = true)
    public Recipe requireRandom(Long tagId) {
        return pickRandom(tagId)
                .orElseThrow(() -> new BotBusinessException("Пока нет рецептов. Добавьте первый через меню."));
    }

    private Optional<Recipe> pickRandom(Long tagId) {
        Instant since = Instant.now().minus(botProperties.getRecentDays(), ChronoUnit.DAYS);
        List<Recipe> candidates = tagId == null
                ? recipeRepository.findRecommendable(since)
                : recipeRepository.findRecommendableByTag(tagId, since);

        if (candidates.isEmpty()) {
            candidates = tagId == null
                    ? recipeRepository.findAll()
                    : recipeRepository.findByTagId(tagId, Pageable.unpaged()).getContent();
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Recipe picked = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return recipeRepository.findDetailedById(picked.getId());
    }
}
