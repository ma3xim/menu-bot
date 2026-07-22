package com.foodmate.bot.scheduler;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.entity.User;
import com.foodmate.bot.service.AccessService;
import com.foodmate.bot.service.UserService;
import com.foodmate.bot.telegram.TelegramSender;
import com.foodmate.bot.telegram.keyboards.KeyboardFactory;
import java.util.List;
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
    private final UserService userService;
    private final AccessService accessService;

    @Scheduled(cron = "${bot.reminder.cron:0 0 10 * * *}", zone = "${bot.reminder.timezone:Asia/Novosibirsk}")
    public void sendDailyReminder() {
        if (!botProperties.getReminder().isEnabled()) {
            return;
        }
        List<User> users = userService.listActiveUsersForReminders();
        if (users.isEmpty() && botProperties.getSuperAdminIds().isEmpty()) {
            log.debug("Reminder skipped: no active users");
            return;
        }
        String text = "Напоминание FoodMate: пора выбрать блюдо на сегодня 🍽";
        int sent = 0;
        for (User user : users) {
            try {
                boolean superUser = accessService.isSuper(user.getTelegramId());
                telegramSender.sendText(user.getTelegramId(), text, KeyboardFactory.mainMenu(superUser));
                sent++;
            } catch (Exception e) {
                log.warn("Failed to send reminder to {}: {}", user.getTelegramId(), e.getMessage());
            }
        }
        for (Long telegramId : botProperties.getSuperAdminIds()) {
            boolean already = users.stream().anyMatch(u -> telegramId.equals(u.getTelegramId()));
            if (already) {
                continue;
            }
            try {
                telegramSender.sendText(telegramId, text, KeyboardFactory.mainMenu(true));
                sent++;
            } catch (Exception e) {
                log.warn("Failed to send reminder to {}: {}", telegramId, e.getMessage());
            }
        }
        log.info("Daily reminders sent to {} users", sent);
    }
}
