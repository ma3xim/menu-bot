package com.foodmate.bot.repository;

import com.foodmate.bot.entity.Recipe;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("""
            SELECT DISTINCT r FROM Recipe r
            LEFT JOIN FETCH r.ingredients ri
            LEFT JOIN FETCH ri.ingredient
            WHERE r.id = :id
            """)
    Optional<Recipe> findDetailedById(@Param("id") Long id);

    @Query("""
            SELECT r FROM Recipe r
            WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(COALESCE(r.cookingInstructions, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY r.name
            """)
    Page<Recipe> search(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT DISTINCT r FROM Recipe r
            JOIN r.tags t
            WHERE t.id = :tagId
            ORDER BY r.name
            """)
    Page<Recipe> findByTagId(@Param("tagId") Long tagId, Pageable pageable);

    @Query("""
            SELECT r FROM Recipe r
            WHERE r.id NOT IN (
                SELECT ch.recipe.id FROM CookingHistory ch
                WHERE ch.cookedAt >= :since
            )
            ORDER BY r.averageRating DESC, r.name ASC
            """)
    List<Recipe> findRecommendable(@Param("since") Instant since);

    @Query("""
            SELECT DISTINCT r FROM Recipe r
            JOIN r.tags t
            WHERE t.id = :tagId
              AND r.id NOT IN (
                SELECT ch.recipe.id FROM CookingHistory ch
                WHERE ch.cookedAt >= :since
              )
            ORDER BY r.averageRating DESC, r.name ASC
            """)
    List<Recipe> findRecommendableByTag(@Param("tagId") Long tagId, @Param("since") Instant since);

    Page<Recipe> findAllByOrderByNameAsc(Pageable pageable);

    @Query("""
            SELECT r FROM Recipe r
            WHERE r.ratingsCount > 0
            ORDER BY r.averageRating DESC, r.ratingsCount DESC, r.name ASC
            """)
    List<Recipe> findTopRated(Pageable pageable);
}
