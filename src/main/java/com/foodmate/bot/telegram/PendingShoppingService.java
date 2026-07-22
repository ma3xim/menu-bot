package com.foodmate.bot.telegram;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PendingShoppingService {

    public enum Mode {
        ADD,
        EDIT
    }

    private final Map<Long, Mode> modeByTelegramId = new ConcurrentHashMap<>();

    public void startAdd(Long telegramId) {
        modeByTelegramId.put(telegramId, Mode.ADD);
    }

    public void startEdit(Long telegramId) {
        modeByTelegramId.put(telegramId, Mode.EDIT);
    }

    public Optional<Mode> getMode(Long telegramId) {
        return Optional.ofNullable(modeByTelegramId.get(telegramId));
    }

    public void clear(Long telegramId) {
        modeByTelegramId.remove(telegramId);
    }
}
