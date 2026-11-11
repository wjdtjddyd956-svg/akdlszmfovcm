package kr.minecraft.survival.economy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** A reserved receipt is persisted before calling an external economy provider. */
final class DailyClaims {
    enum Result { PAID, ALREADY, FAILED, REVIEW }
    private final Path directory;
    DailyClaims(Path directory) {this.directory=directory;}

    synchronized Result claim(UUID player, LocalDate date, BooleanSupplier deposit) throws IOException {
        Files.createDirectories(directory);
        Path file=directory.resolve(player+".properties");
        Properties old=new Properties();
        if (Files.exists(file)) {
            try (Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {old.load(in);}
            String state=old.getProperty("상태");
            String day=old.getProperty("날짜");
            if (state==null || day==null) throw new IOException("보상 기록이 손상되었습니다: "+file);
            if (state.equals("처리중")) return Result.REVIEW;
            if (!state.equals("완료") && !state.equals("실패")) throw new IOException("알 수 없는 보상 상태");
            if (state.equals("완료") && !LocalDate.parse(day).isBefore(date)) return Result.ALREADY;
        }
        write(file,date,"처리중");
        // If deposit throws or the JVM crashes, keep the receipt pending for manual review.
        boolean paid=deposit.getAsBoolean();
        write(file,date,paid?"완료":"실패");
        return paid?Result.PAID:Result.FAILED;
    }

    private void write(Path file,LocalDate date,String state) throws IOException {
        Properties data=new Properties();data.setProperty("날짜",date.toString());data.setProperty("상태",state);
        Path temp=Files.createTempFile(directory,"보상-",".tmp");
        try {
            try (Writer out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)) {data.store(out,"일일 보상 기록 - 처리중 상태는 운영자 확인 필요");}
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch (AtomicMoveNotSupportedException e) {Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
}
