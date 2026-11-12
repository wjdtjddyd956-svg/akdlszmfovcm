package kr.minecraft.survival.economy;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/** The main server thread owns the cache and every transition of a player's ledger. */
final class LifeLedger {
    private final Path directory;
    private final Map<UUID, Properties> cached = new HashMap<>();
    private final Map<UUID, Properties> dirty = new HashMap<>();

    LifeLedger(Path directory) { this.directory = directory; }

    Properties get(UUID player) throws IOException {
        Properties data = cached.get(player);
        if (data != null) return data;
        data = new Properties();
        Path file = directory.resolve(player + ".properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                data.load(reader);
            }
        }
        cached.put(player, data);
        return data;
    }

    static Properties copy(Properties source) {
        Properties result = new Properties();
        result.putAll(source);
        return result;
    }

    static boolean locked(Properties data) { return data.containsKey("pending.id"); }

    static LocalDate day(Properties data, LocalDate clockDay) {
        String saved = data.getProperty("day");
        return saved != null && LocalDate.parse(saved).isAfter(clockDay) ? LocalDate.parse(saved) : clockDay;
    }

    static boolean advance(Properties data, LocalDate today) {
        if (today.toString().equals(data.getProperty("day"))) return false;
        data.keySet().removeIf(key -> key.toString().startsWith("daily."));
        data.setProperty("day", today.toString());
        return true;
    }

    static long number(Properties data, String key) {
        return Long.parseLong(data.getProperty(key, "0"));
    }

    static void number(Properties data, String key, long value) {
        data.setProperty(key, Long.toString(value));
    }

    void queue(UUID player, Properties data) {
        cached.put(player, data);
        dirty.put(player, data);
    }

    void flush(Logger logger) {
        for (UUID player : dirty.keySet().toArray(UUID[]::new)) {
            try { persist(player, dirty.get(player), false); }
            catch (IOException e) { logger.warning("생활 진행도 저장 실패: " + player + " " + e); }
        }
    }

    void unload(UUID player, Logger logger) {
        try {
            if (dirty.containsKey(player)) persist(player, dirty.get(player), false);
            cached.remove(player);
        } catch (IOException e) {
            logger.warning("접속 종료 시 진행도 저장 실패: " + player + " " + e);
        }
    }

    void persist(UUID player, Properties data, boolean durable) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(player + ".properties");
        Path temporary = Files.createTempFile(directory, player.toString(), ".tmp");
        try {
            var text = new java.io.StringWriter();
            data.store(text, "KoreanEconomy life ledger");
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(text.toString());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                while (bytes.hasRemaining()) channel.write(bytes);
                if (durable) channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cached.put(player, data);
            dirty.remove(player);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Properties reserve(UUID player, Properties before, Properties after, String detail,
                       double balance, double delta) throws IOException {
        if (locked(before)) throw new IllegalStateException("미결 거래가 있습니다.");
        Properties pending = copy(before);
        pending.setProperty("pending.id", UUID.randomUUID().toString());
        pending.setProperty("pending.detail", detail);
        pending.setProperty("pending.balance", Double.toString(balance));
        pending.setProperty("pending.delta", Double.toString(delta));
        after.forEach((key, value) -> pending.put("next." + key, value));
        persist(player, pending, true);
        return pending;
    }

    void resolve(UUID player, Properties pending, boolean complete) throws IOException {
        if (!locked(pending)) throw new IllegalArgumentException("미결 거래가 없습니다.");
        Properties result = new Properties();
        for (String key : pending.stringPropertyNames()) {
            if (complete && key.startsWith("next.")) {
                result.setProperty(key.substring(5), pending.getProperty(key));
            } else if (!complete && !key.startsWith("next.") && !key.startsWith("pending.")) {
                result.setProperty(key, pending.getProperty(key));
            }
        }
        persist(player, result, true);
    }
}
