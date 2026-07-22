package com.foodmate.bot.scheduler;

import com.foodmate.bot.service.DatabaseBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupScheduler {

    private final DatabaseBackupService databaseBackupService;

    @Scheduled(cron = "${bot.backup.cron:0 0 3 * * SUN}", zone = "${bot.backup.timezone:Asia/Novosibirsk}")
    public void weeklyBackup() {
        log.info("Starting weekly database backup");
        databaseBackupService.createAndSendWeeklyBackup();
    }
}
