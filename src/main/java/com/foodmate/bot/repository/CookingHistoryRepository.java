package com.foodmate.bot.repository;

import com.foodmate.bot.entity.CookingHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingHistoryRepository extends JpaRepository<CookingHistory, Long> {

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByUserIdOrderByCookedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByCommentIsNotNullOrderByCookedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"recipe", "user"})
    Page<CookingHistory> findByRatingIsNotNullOrderByCookedAtDesc(Pageable pageable);

    long countByRecipeId(Long recipeId);

    List<CookingHistory> findByCookedAtAfter(Instant since);

    boolean existsByUserIdAndRecipeIdAndCookedAtAfter(Long userId, Long recipeId, Instant since);
}
