package com.foodmate.bot.repository;

import com.foodmate.bot.entity.User;
import com.foodmate.bot.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    List<User> findByRoleAndActiveTrueOrderByTelegramIdAsc(UserRole role);

    List<User> findByActiveTrue();
}
