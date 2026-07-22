package com.foodmate.bot.telegram;

import org.springframework.stereotype.Component;

/**
 * Holds chat/thread of the update currently being processed (forum topics support).
 */
@Component
public class UpdateContext {

    private final ThreadLocal<Long> chatId = new ThreadLocal<>();
    private final ThreadLocal<Integer> threadId = new ThreadLocal<>();

    public void set(Long chatId, Integer threadId) {
        this.chatId.set(chatId);
        this.threadId.set(threadId);
    }

    public Long chatId() {
        return chatId.get();
    }

    public Integer threadId() {
        return threadId.get();
    }

    public void clear() {
        chatId.remove();
        threadId.remove();
    }
}
