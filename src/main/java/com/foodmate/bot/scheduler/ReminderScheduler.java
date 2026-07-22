package com.foodmate.bot.scheduler;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.telegram.TelegramSender;
import com.foodmate.bot.telegram.keyboards.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final BotProperties botProperties;
    private final TelegramSender telegramSender;

    @Scheduled(cron = "${bot.reminder.cron:0 0 10 * * *}", zone = "${bot.reminder.timezone:Asia/Novosibirsk}")
    public void sendDailyReminder() {
        if (!botProperties.getReminder().isEnabled()) {
            return;
        }
        if (botProperties.getWhitelistIds() == null || botProperties.getWhitelistIds().isEmpty()) {
            log.debug("Reminder skipped: whitelist is empty");
            return;
        }
        String text = "Напоминание FoodMate: пора выбрать блюдо на сегодня 🍽";
        for (Long telegramId : botProperties.getWhitelistIds()) {
            try {
                telegramSender.sendText(telegramId, text, KeyboardFactory.mainMenu());
            } catch (Exception e) {
                log.warn("Failed to send reminder to {}: {}", telegramId, e.getMessage());
            }
        }
        log.info("Daily reminders sent to {} users", botProperties.getWhitelistIds().size());
    }
}
