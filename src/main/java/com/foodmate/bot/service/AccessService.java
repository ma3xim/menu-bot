package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.exception.AccessDeniedException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final BotProperties botProperties;

    public boolean isAllowed(Long telegramId) {
        Set<Long> whitelist = new HashSet<>(botProperties.getWhitelistIds());
        if (whitelist.isEmpty()) {
            return true;
        }
        return whitelist.contains(telegramId);
    }

    public void requireAllowed(Long telegramId) {
        if (!isAllowed(telegramId)) {
            throw new AccessDeniedException("Этот бот приватный. Доступ ограничен.");
        }
    }
}
