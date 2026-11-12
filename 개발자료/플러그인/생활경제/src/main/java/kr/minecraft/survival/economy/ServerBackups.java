package kr.minecraft.survival.economy;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.bukkit.command.CommandSender;

/** Automatic consistent snapshots of this plugin only. Offline installer backs up whole server. */
final class ServerBackups implements AutoCloseable {
    final Tycoon t;private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread th=new Thread(r,"생활경제-백업");th.setDaemon(true);return th;});private volatile boolean running,closed;
    ServerBackups(Tycoon t){this.t=t;long minutes=Math.max(5,t.plugin.getConfig().getLong("자동백업-분",30));t.plugin.getServer().getScheduler().runTaskTimer(t.plugin,()->start(null),minutes*1200,minutes*1200);}
    void command(CommandSender sender,String[] args){if(!sender.hasPermission("koreaneconomy.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}start(sender);}
    synchronized void start(CommandSender sender){if(running||closed){if(sender!=null)sender.sendMessage("§e백업이 이미 진행 중입니다.");return;}running=true;if(sender!=null)sender.sendMessage("§e생활경제 DB·설정 백업을 시작합니다. 월드 전체 백업은 서버 종료 후 설치 도구를 사용하세요.");
        worker.submit(()->{Path staging=null;try{Path directory=t.plugin.getDataFolder().toPath().resolve("백업");Files.createDirectories(directory);staging=Files.createTempDirectory(directory,"snapshot-");Path db=staging.resolve("생활경제.db");t.store.snapshot(db);if(!kr.minecraft.survival.economy.domain.StateStore.validSnapshot(db))throw new IOException("SQLite 무결성 검사 실패");
            Path config=t.plugin.getDataFolder().toPath().resolve("config.yml");if(Files.exists(config))Files.copy(config,staging.resolve("config.yml"));
            Path zip=directory.resolve("생활경제-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.of("Asia/Seoul")).format(Instant.now())+".zip");Path part=zip.resolveSibling(zip.getFileName()+".part");Properties manifest=new Properties();
            try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(part))){for(String name:List.of("생활경제.db","config.yml")){Path f=staging.resolve(name);if(!Files.exists(f))continue;byte[] bytes=Files.readAllBytes(f);manifest.setProperty(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();}out.putNextEntry(new ZipEntry("SHA256.properties"));manifest.store(out,"KoreanEconomy only; SQLite consistent snapshot; worlds/Vault balances not included");out.closeEntry();}
            try{Files.move(part,zip,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(part,zip);}try(var files=Files.list(directory)){List<Path> zips=files.filter(f->f.getFileName().toString().endsWith(".zip")).sorted(Comparator.reverseOrder()).toList();for(int i=14;i<zips.size();i++)Files.deleteIfExists(zips.get(i));}
            t.plugin.getLogger().info("생활경제 백업 완료: "+zip.getFileName()+" (월드·Vault 잔액 제외)");
        }catch(Exception e){t.plugin.getLogger().log(java.util.logging.Level.SEVERE,"백업 실패",e);}finally{if(staging!=null)try(var files=Files.walk(staging)){for(Path f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}catch(IOException ignored){}running=false;}});
    }
    @Override public void close(){closed=true;worker.shutdown();try{if(!worker.awaitTermination(30,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();worker.shutdownNow();}}
}
