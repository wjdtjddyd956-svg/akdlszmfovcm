package kr.minecraft.survival.setup;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

public class KoreanPackSetupPlugin extends JavaPlugin {
    private static final String MARKER="적용완료-v1.txt";
    private String state="아직 적용되지 않았습니다.";
    @Override public void onLoad(){apply(true);}
    @Override public void onEnable(){getLogger().info(state+" /경제팩설정으로 확인할 수 있습니다.");}

    private byte[] resource(String path)throws Exception {
        try(InputStream in=getClass().getResourceAsStream("/"+path)) {
            if(in==null)throw new IllegalStateException("내장 설정을 찾지 못했습니다: "+path);
            return in.readAllBytes();
        }
    }
    private void apply(boolean once) {
        try {
            Path data=getDataFolder().toPath().toAbsolutePath();
            Path root=data.getParent();
            Path marker=data.resolve(MARKER);
            if(once&&Files.exists(marker)){state="이전에 한 번 적용한 설정을 유지합니다.";return;}
            LinkedHashMap<String,byte[]> plan=new LinkedHashMap<>();
            Path essentials=root.resolve("Essentials/config.yml"),quickshop=root.resolve("QuickShop-Hikari/config.yml");
            String e=Files.exists(essentials)?Files.readString(essentials,StandardCharsets.UTF_8):new String(resource("defaults/Essentials.yml"),StandardCharsets.UTF_8);
            String q=Files.exists(quickshop)?Files.readString(quickshop,StandardCharsets.UTF_8):new String(resource("defaults/QuickShop.yml"),StandardCharsets.UTF_8);
            // Parse all configurations before changing anything on disk.
            plan.put("Essentials/config.yml",ConfigPatch.essentials(e).getBytes(StandardCharsets.UTF_8));
            plan.put("QuickShop-Hikari/config.yml",ConfigPatch.quickshop(q).getBytes(StandardCharsets.UTF_8));
            plan.put("Essentials/messages_ko.properties",resource("locales/Essentials.properties"));
            plan.put("QuickShop-Hikari/overrides/ko_kr/messages.yml",resource("locales/QuickShop.yml"));
            Path backup=data.resolve("백업-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now())+"-"+UUID.randomUUID().toString().substring(0,6));
            String receipt="한글 경제 기본 설정 적용 완료\n신규 유저 시작 잔액 1000원 / 기존 유저 잔액 변경 없음\n원본 백업: "+backup+"\n";
            plan.put(root.relativize(marker).toString(),receipt.getBytes(StandardCharsets.UTF_8));
            SafeFiles.apply(root,backup,plan);
            state="한글·신규 잔액·원화·상점 표시 설정을 적용했습니다. 백업: "+backup;
            getLogger().info(state);
            getLogger().info("EssentialsX 본체의 26.3 호환성은 별개입니다. 26.3을 지원하는 공식 개발 빌드로 교체해야 합니다.");
        } catch(Exception e) {
            state="설정 적용에 실패했습니다. 기존 파일을 보존하거나 복구했습니다. 로그와 백업을 확인하세요.";
            getLogger().severe(state+" "+e);
            for(Throwable suppressed:e.getSuppressed())getLogger().severe("자동 복구 확인 필요: "+suppressed);
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("koreanpacksetup.admin")){sender.sendMessage("§c운영자만 확인할 수 있습니다.");return true;}
        if(args.length==1&&args[0].equals("다시적용")) {
            apply(false);sender.sendMessage(state);sender.sendMessage("§e파일 설정이므로 서버를 완전히 다시 시작해야 반영됩니다.");
        } else {
            sender.sendMessage("§6[경제 팩 파일 설정] "+state);
            sender.sendMessage("§e적용 목표: 신규 유저 1000원 · 원화 · 한국어 · 기본 표시 엔티티");
            sender.sendMessage("§7기존 유저 잔액은 수정하지 않습니다. /경제팩설정 다시적용 후에는 서버를 재시작하세요.");
            sender.sendMessage("§7이 플러그인은 EssentialsX JAR를 업그레이드하거나 실제 송금·이동 성공 여부를 검사하지 않습니다.");
        }
        return true;
    }
}
