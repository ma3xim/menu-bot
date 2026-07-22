package com.foodmate.bot.telegram;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PendingAddUserService {

    private final Set<Long> waitingTelegramIds = ConcurrentHashMap.newKeySet();

    public void start(Long telegramId) {
        waitingTelegramIds.add(telegramId);
    }

    public boolean isWaiting(Long telegramId) {
        return waitingTelegramIds.contains(telegramId);
    }

    public void clear(Long telegramId) {
        waitingTelegramIds.remove(telegramId);
    }
}
