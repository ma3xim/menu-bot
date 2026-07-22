package com.foodmate.bot.repository;

import com.foodmate.bot.entity.DishOfTheDay;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DishOfTheDayRepository extends JpaRepository<DishOfTheDay, Long> {

    @EntityGraph(attributePaths = {"recipe", "setBy"})
    Optional<DishOfTheDay> findFirstByOrderBySetAtDesc();
}
