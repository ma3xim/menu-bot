package com.foodmate.bot.service;

import com.foodmate.bot.config.BotProperties;
import com.foodmate.bot.telegram.TelegramSender;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final List<String> SKIP_TABLES = List.of(
            "databasechangelog",
            "databasechangeloglock"
    );

    private final BotProperties botProperties;
    private final GroupNotifyService groupNotifyService;
    private final TelegramSender telegramSender;
    private final DataSource dataSource;

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
            dumpFile = exportSqlDump();
            long sizeKb = Math.max(1, Files.size(dumpFile) / 1024);
            String caption = "🗄 Еженедельный бэкап БД FoodMate (" + sizeKb + " KB)\n"
                    + "Сохраните файл себе на диск на всякий случай.";
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

    private Path exportSqlDump() throws Exception {
        String timestamp = ZonedDateTime.now(java.time.ZoneId.of(botProperties.getBackup().getTimezone()))
                .format(FILE_TS);
        Path dumpFile = Files.createTempFile("foodmate-backup-", "-" + timestamp + ".sql");

        try (Connection connection = dataSource.getConnection();
             BufferedWriter writer = Files.newBufferedWriter(dumpFile, StandardCharsets.UTF_8)) {
            writer.write("-- FoodMate backup " + timestamp);
            writer.newLine();
            writer.write("-- Generated automatically");
            writer.newLine();
            writer.newLine();

            List<String> tables = listPublicTables(connection);
            for (String table : tables) {
                if (SKIP_TABLES.contains(table)) {
                    continue;
                }
                dumpTable(connection, writer, table);
            }
        }
        return dumpFile;
    }

    private static List<String> listPublicTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        tables.sort(String::compareToIgnoreCase);
        return tables;
    }

    private static void dumpTable(Connection connection, BufferedWriter writer, String table) throws Exception {
        writer.write("-- table: " + table);
        writer.newLine();
        writer.write("TRUNCATE TABLE " + quoteIdent(table) + " CASCADE;");
        writer.newLine();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + quoteIdent(table))) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(quoteIdent(meta.getColumnName(i)));
            }

            while (rs.next()) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    values.add(sqlLiteral(rs, i, meta.getColumnType(i)));
                }
                writer.write("INSERT INTO " + quoteIdent(table)
                        + " (" + String.join(", ", columns) + ") VALUES ("
                        + String.join(", ", values) + ");");
                writer.newLine();
            }
        }
        writer.newLine();
    }

    private static String sqlLiteral(ResultSet rs, int index, int type) throws Exception {
        Object value = rs.getObject(index);
        if (value == null) {
            return "NULL";
        }
        return switch (type) {
            case Types.BOOLEAN, Types.BIT -> Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value)
                    ? "TRUE" : "FALSE";
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT,
                 Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.REAL -> value.toString();
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                Timestamp ts = rs.getTimestamp(index);
                yield ts == null ? "NULL" : "'" + ts.toInstant() + "'";
            }
            default -> "'" + value.toString().replace("'", "''") + "'";
        };
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
