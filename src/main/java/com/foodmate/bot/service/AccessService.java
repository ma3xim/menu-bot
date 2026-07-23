package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.exception.AccessDeniedException;
import com.foodmate.bot.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final BotProperties botProperties;
    private final UserRepository userRepository;

    public boolean isSuper(Long telegramId) {
        if (telegramId == null) {
            return false;
        }
        Set<Long> supers = new HashSet<>(botProperties.getSuperAdminIds());
        return supers.contains(telegramId);
    }

    @Transactional(readOnly = true)
    public void requireAllowed(Long telegramId) {
        assertAllowed(telegramId);
    }

    @Transactional(readOnly = true)
    public void requireSuper(Long telegramId) {
        assertAllowed(telegramId);
        assertSuper(telegramId);
    }

    @Transactional(readOnly = true)
    public void requireCanWrite(Long telegramId) {
        assertAllowed(telegramId);
        assertSuper(telegramId);
    }

    private void assertAllowed(Long telegramId) {
        if (!isAllowed(telegramId)) {
            throw new AccessDeniedException("Этот бот приватный. Доступ ограничен.");
        }
    }

    private void assertSuper(Long telegramId) {
        if (!isSuper(telegramId)) {
            throw new AccessDeniedException("Недостаточно прав. Нужны права суперадмина.");
        }
    }

    private boolean isAllowed(Long telegramId) {
        if (telegramId == null) {
            return false;
        }
        if (isSuper(telegramId)) {
            return true;
        }
        return userRepository.findByTelegramId(telegramId)
                .filter(User::isActive)
                .isPresent();
    }
}
