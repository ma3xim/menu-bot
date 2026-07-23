package com.foodmate.bot.repository;

import com.foodmate.bot.entity.CookingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CookingHistoryRepository extends JpaRepository<CookingHistory, Long> {

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByUserIdOrderByCookedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByCommentIsNotNullOrderByCookedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByRatingIsNotNullOrderByCookedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "recipe"})
    @Query("""
            SELECT h FROM CookingHistory h
            WHERE h.recipe.id = :recipeId
              AND (h.rating IS NOT NULL OR (h.comment IS NOT NULL AND TRIM(h.comment) <> ''))
            ORDER BY h.cookedAt DESC
            """)
    Page<CookingHistory> findReviewsByRecipeId(@Param("recipeId") Long recipeId, Pageable pageable);

    long countByRecipeId(Long recipeId);
}
