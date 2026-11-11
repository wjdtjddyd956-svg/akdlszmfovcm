package kr.minecraft.survival.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

final class LifeLedgerTest {
    private static void check(boolean result, String test) {
        if (!result) throw new AssertionError(test);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("life-ledger-");
        UUID player = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 11, 11);
        try {
            var store = new LifeLedger(root);
            var before = store.get(player);
            LifeLedger.advance(before, day);
            before.setProperty("daily.quest.DELIVERY", "accepted");
            LifeLedger.number(before, "daily.sale.DIAMOND", 11);
            LifeLedger.number(before, "job.xp.MINER", 20);
            var next = LifeLedger.copy(before);
            next.setProperty("daily.quest.DELIVERY", "claimed");
            LifeLedger.number(next, "daily.sale.DIAMOND", 12);
            store.reserve(player, before, next, "테스트 거래", 1000, 48);
            var restart = new LifeLedger(root);
            var pending = restart.get(player);
            check(LifeLedger.locked(pending), "uncertain transaction locks after restart");
            check(LifeLedger.number(pending, "daily.sale.DIAMOND") == 11, "pending operation does not spend limit");
            try {
                restart.reserve(player, pending, next, "duplicate", 1000, 48);
                throw new AssertionError("duplicate reservation");
            } catch (IllegalStateException expected) { }
            restart.resolve(player, pending, false);
            var restored = new LifeLedger(root).get(player);
            check(!LifeLedger.locked(restored) && restored.getProperty("daily.quest.DELIVERY").equals("accepted"), "known failure leaves claim available");
            var newPending = restart.reserve(player, restored, next, "retry", 1000, 48);
            restart.resolve(player, newPending, true);
            var completed = new LifeLedger(root).get(player);
            check(completed.getProperty("daily.quest.DELIVERY").equals("claimed"), "paid claim persists");
            check(LifeLedger.number(completed, "daily.sale.DIAMOND") == 12, "successful limit persists");
            check(LifeLedger.day(completed, day.minusDays(1)).equals(day), "clock rollback cannot open a new day");
            check(!LifeLedger.advance(completed, day), "same day keeps state");
            check(LifeLedger.advance(completed, day.plusDays(1)) && !completed.containsKey("daily.quest.DELIVERY"), "new day resets quest");
            check(LifeLedger.number(completed, "daily.sale.DIAMOND") == 0, "new day resets sale quota");
            check(LifeLedger.number(completed, "job.xp.MINER") == 20, "lifetime level survives reset");
            System.out.println("PASS: 11 ledger invariants");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
