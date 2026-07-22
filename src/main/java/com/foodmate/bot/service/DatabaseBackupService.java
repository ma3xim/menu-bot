package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.telegram.TelegramSender;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupService {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final BotProperties botProperties;
    private final GroupNotifyService groupNotifyService;
    private final TelegramSender telegramSender;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    public void createAndSendWeeklyBackup() {
        if (!botProperties.getBackup().isEnabled()) {
            log.debug("DB backup skipped: disabled");
            return;
        }

        var target = groupNotifyService.resolveTarget();
        if (target.isEmpty()) {
            log.warn("DB backup skipped: notify chat is not configured/learned");
            return;
        }

        Path dumpFile = null;
        try {
            dumpFile = runPgDump();
            long sizeKb = Files.size(dumpFile) / 1024;
            String caption = "🗄 Еженедельный бэкап БД FoodMate (" + sizeKb + " KB)\n"
                    + "Файл можно сохранить себе на диск на всякий случай.";
            telegramSender.sendFileTo(
                    target.get().chatId(),
                    target.get().threadId(),
                    dumpFile,
                    caption
            );
            log.info("DB backup sent to chat {} ({} KB)", target.get().chatId(), sizeKb);
        } catch (Exception e) {
            log.error("DB backup failed", e);
            groupNotifyService.notify("⚠️ Не удалось сделать бэкап БД: " + e.getMessage());
        } finally {
            if (dumpFile != null) {
                try {
                    Files.deleteIfExists(dumpFile);
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
    }

    private Path runPgDump() throws IOException, InterruptedException {
        DbEndpoint db = parseJdbcUrl(datasourceUrl);
        String timestamp = ZonedDateTime.now(java.time.ZoneId.of(botProperties.getBackup().getTimezone()))
                .format(FILE_TS);
        Path dumpFile = Files.createTempFile("foodmate-backup-", "-" + timestamp + ".sql");

        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("-h");
        command.add(db.host());
        command.add("-p");
        command.add(String.valueOf(db.port()));
        command.add("-U");
        command.add(datasourceUsername);
        command.add("-d");
        command.add(db.database());
        command.add("--no-owner");
        command.add("--no-acl");
        command.add("-f");
        command.add(dumpFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", datasourcePassword);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("pg_dump timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("pg_dump failed: " + output);
        }
        return dumpFile;
    }

    private static DbEndpoint parseJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalStateException("Invalid JDBC URL");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath();
        if (!StringUtils.hasText(path) || path.equals("/")) {
            throw new IllegalStateException("JDBC URL has no database name");
        }
        String database = path.startsWith("/") ? path.substring(1) : path;
        return new DbEndpoint(host, port, database);
    }

    private record DbEndpoint(String host, int port, String database) {
    }
}
