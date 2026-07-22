package com.foodmate.bot.repository;

import com.foodmate.bot.entity.FavoriteRecipe;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRecipeRepository extends JpaRepository<FavoriteRecipe, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    Optional<FavoriteRecipe> findByUserIdAndRecipeId(Long userId, Long recipeId);

    void deleteByUserIdAndRecipeId(Long userId, Long recipeId);

    @EntityGraph(attributePaths = "recipe")
    Page<FavoriteRecipe> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
