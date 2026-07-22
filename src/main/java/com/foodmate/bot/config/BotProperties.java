package com.foodmate.bot.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String token = "CHANGE_ME";
    /** Super-admin telegram IDs (full access + user management). */
    private List<Long> superAdminIds = new ArrayList<>();
    private int recentDays = 3;
    /** Family group chat id (e.g. -100...). Optional — can be learned from first group message. */
    private Long notifyChatId;
    /** Forum topic id inside the group. Optional — learned from group messages. */
    private Integer notifyThreadId;
    /** Display / schedule timezone (Novosibirsk = MSK+4). */
    private String timezone = "Asia/Novosibirsk";
    private Reminder reminder = new Reminder();
    private Backup backup = new Backup();

    @Data
    public static class Reminder {
        private boolean enabled = true;
        private String cron = "0 0 10 * * *";
        private String timezone = "Asia/Novosibirsk";
    }

    @Data
    public static class Backup {
        private boolean enabled = true;
        /** Default: every Sunday at 03:00 */
        private String cron = "0 0 3 * * SUN";
        private String timezone = "Asia/Novosibirsk";
    }
}
