package kr.minecraft.survival.setup;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class SetupTest {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static Map<String,Object> nested(Map<String,Object> map,String key){return (Map<String,Object>)map.get(key);}
    static void reject(String text){try{ConfigPatch.read(text);throw new AssertionError("bad YAML accepted");}catch(RuntimeException expected){checks++;}}
    static class Plugin extends KoreanPackSetupPlugin {
        final Path path;Plugin(Path p){path=p;}
        @Override public java.io.File getDataFolder(){return path.toFile();}
    }
    public static void main(String[] args)throws Exception {
        String oldE="locale: en\nstarting-balance: 0\ncurrency-symbol: '$'\nmin-money: -30\ncustom:\n  price: 79\n  names: [alice, bob]\n";
        Map<String,Object> changed=ConfigPatch.read(ConfigPatch.essentials(oldE));
        ok(changed.get("locale").equals("ko"),"Essentials Korean locale");
        ok(changed.get("starting-balance").equals(1000),"new player starting balance 1000");
        ok(changed.get("currency-symbol").equals("원")&&changed.get("currency-symbol-suffix").equals(true),"won suffix");
        ok(changed.get("per-player-locale").equals(false),"same locale for all clients");
        ok(changed.get("min-money").equals(-30),"unrelated economy rule unchanged");
        ok(nested(changed,"custom").get("price").equals(79),"custom prices unchanged");
        ok(nested(changed,"custom").get("names").equals(List.of("alice","bob")),"custom list unchanged");
        String oldQ="game-language: default\nenabled-languages: ['*']\nshop:\n  cost: 12.5\n  display-items: false\n  display-type: 2\n  nested: {retained: true}\ncustom: 'http://example.invalid/a:b'\n";
        Map<String,Object> q=ConfigPatch.read(ConfigPatch.quickshop(oldQ));
        ok(q.get("game-language").equals("ko_kr")&&q.get("enabled-languages").equals(List.of("ko_kr")),"force Korean across clients");
        ok(q.get("use-crowdin-ota").equals(false),"retain bundled custom translations");
        ok(nested(q,"shop").get("display-items").equals(true)&&nested(q,"shop").get("display-type").equals(3),"native item display needs no packet plugin");
        ok(nested(q,"shop").get("cost").equals(12.5)&&nested(nested(q,"shop"),"nested").get("retained").equals(true),"keep nested fees and unknown settings");
        ok(q.get("custom").equals("http://example.invalid/a:b"),"quoted values preserved");
        reject("broken: [");reject("locale: en\nlocale: ko\n");reject("- one\n- two\n");
        try{ConfigPatch.quickshop("shop: scalar\n");throw new AssertionError("invalid section accepted");}catch(IllegalArgumentException expected){checks++;}

        Path root=Files.createTempDirectory("korean-setup-test-");
        Files.writeString(root.resolve("one.yml"),"original-one");Files.writeString(root.resolve("two.yml"),"original-two");
        LinkedHashMap<String,byte[]> files=new LinkedHashMap<>();files.put("one.yml","changed-one".getBytes());files.put("new.yml","new".getBytes());files.put("two.yml","changed-two".getBytes());
        AtomicInteger writes=new AtomicInteger();
        try {SafeFiles.apply(root,root.resolve("backup-failure"),files,(path,bytes)->{SafeFiles.atomic(path,bytes);if(writes.incrementAndGet()==3)throw new IOException("simulated disk failure");});throw new AssertionError("failure not propagated");}
        catch(IOException expected){checks++;}
        ok(Files.readString(root.resolve("one.yml")).equals("original-one")&&Files.readString(root.resolve("two.yml")).equals("original-two"),"restore all original bytes after partial failure");
        ok(!Files.exists(root.resolve("new.yml")),"remove newly created file after partial failure");
        ok(Files.readString(root.resolve("backup-failure/one.yml")).equals("original-one"),"original backup retained");
        SafeFiles.apply(root,root.resolve("backup-success"),files);
        ok(Files.readString(root.resolve("two.yml")).equals("changed-two")&&Files.readString(root.resolve("new.yml")).equals("new"),"successful commit");

        Path plugins=root.resolve("server/plugins");Files.createDirectories(plugins.resolve("Essentials/userdata"));Files.createDirectories(plugins.resolve("QuickShop-Hikari"));
        Files.writeString(plugins.resolve("Essentials/config.yml"),oldE,StandardCharsets.UTF_8);Files.writeString(plugins.resolve("QuickShop-Hikari/config.yml"),oldQ,StandardCharsets.UTF_8);
        Path userdata=plugins.resolve("Essentials/userdata/player.yml");Files.writeString(userdata,"money: '0.0'\nhomes: {default: saved}\n");byte[] userBytes=Files.readAllBytes(userdata);
        Plugin plugin=new Plugin(plugins.resolve("KoreanPackSetup"));plugin.onLoad();
        ok(Files.exists(plugins.resolve("KoreanPackSetup/적용완료-v1.txt")),"mark only after successful application");
        ok(ConfigPatch.read(Files.readString(plugins.resolve("Essentials/config.yml"))).get("starting-balance").equals(1000),"startup applies real configuration files");
        ok(Arrays.equals(userBytes,Files.readAllBytes(userdata)),"existing user balance and homes byte-for-byte unchanged");
        Files.writeString(plugins.resolve("Essentials/config.yml"),"starting-balance: 777\nlocale: en\n");
        new Plugin(plugins.resolve("KoreanPackSetup")).onLoad();
        ok(ConfigPatch.read(Files.readString(plugins.resolve("Essentials/config.yml"))).get("starting-balance").equals(777),"one-time marker preserves later admin edits");
        Path bad=root.resolve("bad/plugins");Files.createDirectories(bad.resolve("Essentials"));Files.createDirectories(bad.resolve("QuickShop-Hikari"));
        Files.writeString(bad.resolve("Essentials/config.yml"),oldE);Files.writeString(bad.resolve("QuickShop-Hikari/config.yml"),"shop: [");
        new Plugin(bad.resolve("KoreanPackSetup")).onLoad();
        ok(Files.readString(bad.resolve("Essentials/config.yml")).equals(oldE),"invalid second YAML prevents all writes");
        ok(!Files.exists(bad.resolve("KoreanPackSetup/적용완료-v1.txt")),"failed setup has no success marker");
        Path fresh=root.resolve("fresh/plugins/KoreanPackSetup");new Plugin(fresh).onLoad();
        ok(Files.exists(fresh.getParent().resolve("QuickShop-Hikari/config.yml")),"JAR alone creates missing defaults");
        ok(Files.exists(fresh.getParent().resolve("Essentials/messages_ko.properties")),"JAR alone installs translated messages");
        System.out.println("PASS: "+checks+" setup assertions (real SnakeYAML and filesystem, offline Bukkit lifecycle fixture)");
    }
}
