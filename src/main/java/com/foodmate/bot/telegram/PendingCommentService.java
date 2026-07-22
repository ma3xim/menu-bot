package com.foodmate.bot.telegram;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PendingCommentService {

    private final Map<Long, Long> historyByTelegramId = new ConcurrentHashMap<>();

    public void start(Long telegramId, Long historyId) {
        historyByTelegramId.put(telegramId, historyId);
    }

    public Optional<Long> getHistoryId(Long telegramId) {
        return Optional.ofNullable(historyByTelegramId.get(telegramId));
    }

    public void clear(Long telegramId) {
        historyByTelegramId.remove(telegramId);
    }
}
