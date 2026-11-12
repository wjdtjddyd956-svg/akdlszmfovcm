package kr.minecraft.survival.citytextures;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Separate configuration keeps existing city-pack settings and manual choices intact. */
final class InterfacePack {
    final CityTexturesPlugin plugin;final Map<String,CityTexturesPlugin.Icon> icons=new LinkedHashMap<>();final Set<UUID> ready=new HashSet<>(),manual=new HashSet<>();YamlConfiguration config;
    InterfacePack(CityTexturesPlugin plugin)throws IOException{this.plugin=plugin;reload();try(var input=plugin.getResource("ui-icons.tsv")){if(input==null)throw new IOException("메뉴 아이콘 목록이 없습니다.");for(String line:new String(input.readAllBytes(),StandardCharsets.UTF_8).split("\\R")){if(line.isBlank())continue;String[] row=line.split("\t");if(row.length!=3||!row[0].matches("ui/[a-z0-9_]+"))throw new IOException("메뉴 아이콘 형식 오류");var icon=new CityTexturesPlugin.Icon(row[0],row[1],Material.valueOf(row[2]));if(icons.put(icon.id(),icon)!=null)throw new IOException("중복 메뉴 아이콘");}if(icons.size()!=24)throw new IOException("메뉴 아이콘 24종이 필요합니다.");}}
    void reload(){File f=new File(plugin.getDataFolder(),"ui.yml");if(!f.exists())plugin.saveResource("ui.yml",false);config=YamlConfiguration.loadConfiguration(f);manual.clear();for(String raw:config.getStringList("직접설치-확인"))try{manual.add(UUID.fromString(raw));}catch(IllegalArgumentException ex){plugin.getLogger().warning("메뉴팩의 잘못된 UUID를 건너뜁니다.");}}
    boolean enabled(){return config.getBoolean("사용",true);}
    CityTexturesPlugin.Request request(){String url=config.getString("팩-주소","").trim(),hash=config.getString("체크섬-SHA1","").trim();URI uri=URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||!hash.matches("[a-fA-F0-9]{40}"))throw new IllegalArgumentException("CityTextures/ui.yml의 통합팩 HTTPS 주소와 SHA1을 확인하세요.");return new CityTexturesPlugin.Request(url,HexFormat.of().parseHex(hash));}
    boolean auto(Player p){if(!enabled()||!config.getBoolean("접속시-요청",true)||!p.hasPermission("citytextures.use"))return false;try{request();return true;}catch(IllegalArgumentException ex){return false;}}
    void request(Player p){if(!enabled()){plugin.request(p);return;}var r=request();p.addResourcePack(CityTexturesPlugin.PACK_ID,r.url(),r.sha1(),"메뉴·도감 아이콘과 농작물 통합팩을 적용할까요?",false);p.sendMessage("§b통합 메뉴팩 다운로드를 허용하면 새 아이콘이 적용됩니다.");}
    void mark(Player p,boolean value){if(value)ready.add(p.getUniqueId());else ready.remove(p.getUniqueId());var farm=plugin.getServer().getPluginManager().getPlugin("KoreanFarming");if(farm==null||!farm.isEnabled())return;try{farm.getClass().getMethod("setMenuPackReady",Player.class,boolean.class).invoke(farm,p,value);}catch(NoSuchMethodException ignored){/* Older farming remains on its own pack. */}catch(ReflectiveOperationException ex){plugin.getLogger().warning("농장 통합팩 연결: "+ex);}}
    void manual(Player p,boolean value){if(value)manual.add(p.getUniqueId());else manual.remove(p.getUniqueId());config.set("직접설치-확인",manual.stream().map(UUID::toString).sorted().toList());try{config.save(new File(plugin.getDataFolder(),"ui.yml"));}catch(IOException e){throw new IllegalArgumentException("메뉴팩 개인 설정 저장에 실패했습니다.",e);}mark(p,value);}
    void join(Player p){if(enabled()&&manual.contains(p.getUniqueId()))mark(p,true);}
    void status(PlayerResourcePackStatusEvent e){if(!CityTexturesPlugin.PACK_ID.equals(e.getID())||!enabled())return;switch(e.getStatus()){case SUCCESSFULLY_LOADED->mark(e.getPlayer(),e.getPlayer().hasPermission("citytextures.use"));case DECLINED,FAILED_DOWNLOAD,INVALID_URL,FAILED_RELOAD,DISCARDED->mark(e.getPlayer(),false);default->{}}}
}
