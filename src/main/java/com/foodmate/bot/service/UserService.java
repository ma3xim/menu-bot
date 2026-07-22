package com.foodmate.bot.service;

import com.foodmate.bot.entity.User;
import com.foodmate.bot.entity.UserRole;
import com.foodmate.bot.exception.BotBusinessException;
import com.foodmate.bot.exception.NotFoundException;
import com.foodmate.bot.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccessService accessService;

    @Transactional
    public User getOrCreate(Long telegramId, String displayName) {
        User user = userRepository.findByTelegramId(telegramId)
                .map(existing -> {
                    if (displayName != null && !displayName.equals(existing.getDisplayName())) {
                        existing.setDisplayName(displayName);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    User created = new User();
                    created.setTelegramId(telegramId);
                    created.setDisplayName(displayName);
                    created.setRole(UserRole.VIEWER);
                    created.setActive(true);
                    return created;
                });

        if (accessService.isSuper(telegramId)) {
            user.setRole(UserRole.SUPER);
            user.setActive(true);
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> listActiveViewers() {
        return userRepository.findByRoleAndActiveTrueOrderByTelegramIdAsc(UserRole.VIEWER);
    }

    @Transactional
    public User addViewer(Long telegramId) {
        if (telegramId == null || telegramId <= 0) {
            throw new BotBusinessException("Нужен положительный Telegram ID.");
        }
        if (accessService.isSuper(telegramId)) {
            throw new BotBusinessException("Этот ID уже суперадмин, добавлять как viewer не нужно.");
        }
        User user = userRepository.findByTelegramId(telegramId).orElseGet(() -> {
            User created = new User();
            created.setTelegramId(telegramId);
            created.setRole(UserRole.VIEWER);
            return created;
        });
        user.setRole(UserRole.VIEWER);
        user.setActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public User deactivateViewer(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (user.getRole() == UserRole.SUPER || accessService.isSuper(telegramId)) {
            throw new BotBusinessException("Нельзя отключить суперадмина.");
        }
        user.setActive(false);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> listActiveUsersForReminders() {
        return userRepository.findByActiveTrue();
    }
}
