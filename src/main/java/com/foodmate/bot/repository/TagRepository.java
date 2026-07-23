package com.foodmate.bot.repository;

import com.foodmate.bot.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    @Query("""
            SELECT t FROM Tag t
            WHERE EXISTS (
                SELECT 1 FROM Recipe r JOIN r.tags rt WHERE rt = t
            )
            ORDER BY t.name ASC
            """)
    List<Tag> findAllUsedByOrderByNameAsc();

    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM Tag t
            WHERE NOT EXISTS (
                SELECT 1 FROM Recipe r JOIN r.tags rt WHERE rt = t
            )
            """)
    int deleteUnused();
}
