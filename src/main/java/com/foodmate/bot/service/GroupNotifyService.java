package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.telegram.TelegramSender;
import com.foodmate.bot.telegram.UpdateContext;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupNotifyService {

    public record Target(Long chatId, Integer threadId) {
    }

    private final BotProperties botProperties;
    private final TelegramSender telegramSender;
    private final UpdateContext updateContext;
    private final BotSettingsService botSettingsService;

    private final AtomicReference<Long> learnedChatId = new AtomicReference<>();
    private final AtomicReference<Integer> learnedThreadId = new AtomicReference<>();

    @PostConstruct
    void loadPersistedTarget() {
        botSettingsService.getLong(BotSettingsService.NOTIFY_CHAT_ID)
                .ifPresent(chatId -> {
                    learnedChatId.set(chatId);
                    log.info("Loaded persisted notify chatId={}", chatId);
                });
        botSettingsService.getInteger(BotSettingsService.NOTIFY_THREAD_ID)
                .ifPresent(threadId -> {
                    learnedThreadId.set(threadId);
                    log.info("Loaded persisted notify threadId={}", threadId);
                });
    }

    public void rememberGroupTarget(Long chatId, Integer threadId) {
        if (chatId == null || chatId >= 0) {
            return;
        }
        Long configuredChat = botProperties.getNotifyChatId();
        if (configuredChat != null && !configuredChat.equals(chatId)) {
            return;
        }

        learnedChatId.set(chatId);
        if (threadId != null) {
            learnedThreadId.set(threadId);
        }
        botSettingsService.saveNotifyTarget(chatId, threadId);
        log.info("Persisted group notify target chatId={}, threadId={}", chatId, threadId);
    }

    public Optional<Target> resolveTarget() {
        Long chatId = firstNonNull(
                botProperties.getNotifyChatId(),
                learnedChatId.get()
        );
        if (chatId == null) {
            return Optional.empty();
        }
        Integer threadId = firstNonNull(
                botProperties.getNotifyThreadId(),
                learnedThreadId.get()
        );
        return Optional.of(new Target(chatId, threadId));
    }

    public void notify(String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Optional<Target> targetOpt = resolveTarget();
        if (targetOpt.isEmpty()) {
            log.debug("Group notify skipped: chat id not configured/learned");
            return;
        }
        Target target = targetOpt.get();

        Long currentChat = updateContext.chatId();
        Integer currentThread = updateContext.threadId();
        if (Objects.equals(currentChat, target.chatId()) && Objects.equals(currentThread, target.threadId())) {
            return;
        }

        telegramSender.sendTextTo(target.chatId(), target.threadId(), text, null);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
