package com.smart.exam.auth.tool;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyPasswordMigrationTool {

    private static final String BCRYPT_PREFIX = "$2";
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int DEFAULT_SAMPLE_SIZE = 20;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private LegacyPasswordMigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        MigrationConfig config = MigrationConfig.parse(args);
        Instant startedAt = Instant.now();
        try {
            MigrationResult result = execute(config);
            if (config.reportFile() != null) {
                writeJson(config.reportFile(), result.toReport(config, startedAt, Instant.now(), null));
            }
            printSummary(result, config);
        } catch (Exception ex) {
            if (config.reportFile() != null) {
                writeJson(config.reportFile(), MigrationResult.failureReport(config, startedAt, Instant.now(), ex.getMessage()));
            }
            System.err.println("Password migration failed: " + ex.getMessage());
            throw ex;
        } finally {
            shutdownMysqlCleanupThread();
        }
    }

    private static MigrationResult execute(MigrationConfig config) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(
                config.jdbcUrl(),
                config.dbUser(),
                config.dbPassword()
        )) {
            List<LegacyUserRecord> sampleBefore = loadLegacyUsers(connection, config.sampleSize(), false);
            long legacyUserCountBefore = countLegacyUsers(connection);

            if (config.mode() == Mode.DRY_RUN || legacyUserCountBefore == 0) {
                return new MigrationResult(
                        config.mode(),
                        legacyUserCountBefore,
                        0,
                        legacyUserCountBefore,
                        sampleUsernames(sampleBefore),
                        sampleUsernames(sampleBefore),
                        config.rollbackSql() == null ? null : config.rollbackSql().toAbsolutePath().toString(),
                        null
                );
            }

            List<LegacyUserRecord> usersToMigrate = loadLegacyUsers(connection, config.limit(), true);
            if (usersToMigrate.isEmpty()) {
                return new MigrationResult(
                        config.mode(),
                        legacyUserCountBefore,
                        0,
                        legacyUserCountBefore,
                        sampleUsernames(sampleBefore),
                        sampleUsernames(sampleBefore),
                        config.rollbackSql() == null ? null : config.rollbackSql().toAbsolutePath().toString(),
                        null
                );
            }

            connection.setAutoCommit(false);
            int migratedCount = 0;
            Path rollbackTempFile = null;
            try {
                if (config.rollbackSql() != null) {
                    Path rollbackSqlFile = config.rollbackSql().toAbsolutePath();
                    Path parent = rollbackSqlFile.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    rollbackTempFile = rollbackSqlFile.resolveSibling(rollbackSqlFile.getFileName() + ".tmp");
                    Files.writeString(rollbackTempFile, "START TRANSACTION;\n", StandardCharsets.UTF_8);
                }

                try (PreparedStatement updateStatement = connection.prepareStatement(
                        "UPDATE sys_user SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                )) {
                    for (LegacyUserRecord user : usersToMigrate) {
                        updateStatement.setString(1, PASSWORD_ENCODER.encode(user.password()));
                        updateStatement.setLong(2, user.id());
                        updateStatement.addBatch();
                        migratedCount++;

                        if (rollbackTempFile != null) {
                            appendRollbackSql(rollbackTempFile, user);
                        }

                        if (migratedCount % config.batchSize() == 0) {
                            updateStatement.executeBatch();
                        }
                    }
                    updateStatement.executeBatch();
                }

                connection.commit();
                if (rollbackTempFile != null) {
                    Files.writeString(rollbackTempFile, "COMMIT;\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                    Files.move(rollbackTempFile, config.rollbackSql().toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                connection.rollback();
                if (rollbackTempFile != null) {
                    Files.deleteIfExists(rollbackTempFile);
                }
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }

            List<LegacyUserRecord> sampleAfter = loadLegacyUsers(connection, config.sampleSize(), false);
            long legacyUserCountAfter = countLegacyUsers(connection);
            return new MigrationResult(
                    config.mode(),
                    legacyUserCountBefore,
                    migratedCount,
                    legacyUserCountAfter,
                    sampleUsernames(sampleBefore),
                    sampleUsernames(sampleAfter),
                    config.rollbackSql() == null ? null : config.rollbackSql().toAbsolutePath().toString(),
                    null
            );
        }
    }

    private static long countLegacyUsers(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(1) FROM sys_user WHERE password_hash IS NOT NULL AND password_hash NOT LIKE ?"
        )) {
            statement.setString(1, BCRYPT_PREFIX + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static List<LegacyUserRecord> loadLegacyUsers(Connection connection, Integer limit, boolean includePassword) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT id, username, password_hash
                FROM sys_user
                WHERE password_hash IS NOT NULL
                  AND password_hash NOT LIKE ?
                ORDER BY id
                """);
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
        }

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, BCRYPT_PREFIX + "%");
            if (limit != null && limit > 0) {
                statement.setInt(2, limit);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LegacyUserRecord> users = new ArrayList<>();
                while (resultSet.next()) {
                    users.add(new LegacyUserRecord(
                            resultSet.getLong("id"),
                            resultSet.getString("username"),
                            includePassword ? resultSet.getString("password_hash") : null
                    ));
                }
                return users;
            }
        }
    }

    private static void appendRollbackSql(Path rollbackTempFile, LegacyUserRecord user) throws IOException {
        String encodedPassword = Base64.getEncoder()
                .encodeToString(user.password().getBytes(StandardCharsets.UTF_8));
        String sql = "UPDATE sys_user SET password_hash = CONVERT(FROM_BASE64('" + encodedPassword
                + "') USING utf8mb4), updated_at = CURRENT_TIMESTAMP WHERE id = " + user.id() + ";\n";
        Files.writeString(rollbackTempFile, sql, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static List<String> sampleUsernames(List<LegacyUserRecord> users) {
        return users.stream().map(LegacyUserRecord::username).toList();
    }

    private static void printSummary(MigrationResult result, MigrationConfig config) {
        System.out.println("mode=" + config.mode().value());
        System.out.println("legacyUserCountBefore=" + result.legacyUserCountBefore());
        System.out.println("migratedUserCount=" + result.migratedUserCount());
        System.out.println("legacyUserCountAfter=" + result.legacyUserCountAfter());
        System.out.println("sampleLegacyUsernamesBefore=" + result.sampleLegacyUsernamesBefore());
        System.out.println("sampleLegacyUsernamesAfter=" + result.sampleLegacyUsernamesAfter());
        if (result.rollbackSqlFile() != null) {
            System.out.println("rollbackSqlFile=" + result.rollbackSqlFile());
        }
        if (config.reportFile() != null) {
            System.out.println("reportFile=" + config.reportFile().toAbsolutePath());
        }
    }

    private static void writeJson(Path path, Map<String, Object> content) throws IOException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(absolutePath.toFile(), content);
    }

    private static void shutdownMysqlCleanupThread() {
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception ignored) {
            // ignore cleanup failures to preserve the original migration result
        }
    }

    private enum Mode {
        DRY_RUN("dry-run"),
        MIGRATE("migrate");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        private static Mode parse(String rawValue) {
            for (Mode mode : values()) {
                if (mode.value.equalsIgnoreCase(rawValue)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported --mode value: " + rawValue);
        }

        private String value() {
            return value;
        }
    }

    private record LegacyUserRecord(Long id, String username, String password) {
    }

    private record MigrationResult(
            Mode mode,
            long legacyUserCountBefore,
            int migratedUserCount,
            long legacyUserCountAfter,
            List<String> sampleLegacyUsernamesBefore,
            List<String> sampleLegacyUsernamesAfter,
            String rollbackSqlFile,
            String error
    ) {
        private Map<String, Object> toReport(MigrationConfig config, Instant startedAt, Instant finishedAt, String failureMessage) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", mode.value());
            payload.put("jdbcUrl", config.jdbcUrl());
            payload.put("dbUser", config.dbUser());
            payload.put("limit", config.limit());
            payload.put("batchSize", config.batchSize());
            payload.put("sampleSize", config.sampleSize());
            payload.put("legacyUserCountBefore", legacyUserCountBefore);
            payload.put("migratedUserCount", migratedUserCount);
            payload.put("legacyUserCountAfter", legacyUserCountAfter);
            payload.put("sampleLegacyUsernamesBefore", sampleLegacyUsernamesBefore);
            payload.put("sampleLegacyUsernamesAfter", sampleLegacyUsernamesAfter);
            payload.put("rollbackSqlFile", rollbackSqlFile);
            payload.put("cutoverReady", legacyUserCountAfter == 0);
            payload.put("startedAt", startedAt.toString());
            payload.put("finishedAt", finishedAt.toString());
            payload.put("error", failureMessage == null ? error : failureMessage);
            return payload;
        }

        private static Map<String, Object> failureReport(MigrationConfig config,
                                                         Instant startedAt,
                                                         Instant finishedAt,
                                                         String failureMessage) {
            return new MigrationResult(
                    config.mode(),
                    -1,
                    0,
                    -1,
                    List.of(),
                    List.of(),
                    config.rollbackSql() == null ? null : config.rollbackSql().toAbsolutePath().toString(),
                    failureMessage
            ).toReport(config, startedAt, finishedAt, failureMessage);
        }
    }

    private record MigrationConfig(
            Mode mode,
            String jdbcUrl,
            String dbUser,
            String dbPassword,
            Integer limit,
            int batchSize,
            int sampleSize,
            Path reportFile,
            Path rollbackSql
    ) {
        private static MigrationConfig parse(String[] args) {
            Map<String, String> options = new LinkedHashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("Arguments must use --key=value format: " + arg);
                }
                int separator = arg.indexOf('=');
                options.put(arg.substring(2, separator), arg.substring(separator + 1));
            }

            Mode mode = Mode.parse(required(options, "mode"));
            String jdbcUrl = required(options, "jdbc-url");
            String dbUser = required(options, "db-user");
            String dbPassword = options.getOrDefault("db-password", "");
            Integer limit = parseOptionalPositiveInt(options.get("limit"), "limit");
            int batchSize = parsePositiveInt(options.getOrDefault("batch-size", String.valueOf(DEFAULT_BATCH_SIZE)), "batch-size");
            int sampleSize = parsePositiveInt(options.getOrDefault("sample-size", String.valueOf(DEFAULT_SAMPLE_SIZE)), "sample-size");
            Path reportFile = options.containsKey("report-file") ? Path.of(options.get("report-file")) : null;
            Path rollbackSql = options.containsKey("rollback-sql") ? Path.of(options.get("rollback-sql")) : null;
            if (mode == Mode.DRY_RUN && rollbackSql != null) {
                throw new IllegalArgumentException("--rollback-sql is only valid with --mode=migrate");
            }
            return new MigrationConfig(mode, jdbcUrl, dbUser, dbPassword, limit, batchSize, sampleSize, reportFile, rollbackSql);
        }

        private static String required(Map<String, String> options, String key) {
            String value = options.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required argument --" + key);
            }
            return value;
        }

        private static int parsePositiveInt(String rawValue, String fieldName) {
            try {
                int value = Integer.parseInt(rawValue);
                if (value < 1) {
                    throw new IllegalArgumentException(fieldName + " must be greater than zero");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid integer for --" + fieldName + ": " + rawValue);
            }
        }

        private static Integer parseOptionalPositiveInt(String rawValue, String fieldName) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            return parsePositiveInt(rawValue, fieldName);
        }
    }
}
