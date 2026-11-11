package kr.minecraft.survival.citytextures;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Cosmetic bridge only: never reads money, removes items, or dispatches economy commands. */
public class CityTexturesPlugin extends JavaPlugin implements Listener {
    public static final UUID PACK_ID=UUID.nameUUIDFromBytes("tycoon:city-economy:1.0".getBytes(StandardCharsets.UTF_8));
    record Icon(String id,String label,Material base){}
    private final Map<String,Icon> icons=new LinkedHashMap<>();
    private final Set<UUID> active=new HashSet<>(),manual=new HashSet<>();
    private NamespacedKey previousModel;
    static final class Preview implements InventoryHolder {
        final UUID owner;final Inventory inventory;
        Preview(CityTexturesPlugin plugin,Player p){owner=p.getUniqueId();inventory=plugin.getServer().createInventory(this,54,"시티 이코노미 | 아이콘 미리보기");}
        public Inventory getInventory(){return inventory;}
    }
    @Override public void onEnable(){
        saveDefaultConfig();previousModel=new NamespacedKey(this,"previous_model");
        try(InputStream input=getResource("icons.tsv")){
            if(input==null)throw new IOException("내장 아이콘 목록이 없습니다.");
            for(String line:new String(input.readAllBytes(),StandardCharsets.UTF_8).split("\\R")){
                if(line.isBlank())continue;String[] row=line.split("\t",3);if(row.length!=3||!row[0].matches("[a-z0-9_]+"))throw new IOException("아이콘 목록 형식 오류");
                Icon icon=new Icon(row[0],row[1],Material.valueOf(row[2]));if(icons.put(icon.id,icon)!=null)throw new IOException("중복 아이콘");
            }
            if(icons.size()!=32)throw new IOException("아이콘 32종이 필요합니다.");
        }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"도시팩 시작 실패",e);getServer().getPluginManager().disablePlugin(this);return;}
        loadManual();getServer().getPluginManager().registerEvents(this,this);
        for(Player p:getServer().getOnlinePlayers())if(getConfig().getBoolean("수동설정-기억",true)&&!getConfig().getBoolean("자동요청",false)&&manual.contains(p.getUniqueId()))active.add(p.getUniqueId());
        getLogger().info("시티 이코노미 아이콘 32종 준비 완료. ZIP 적용 후 /도시팩 켜기");
    }
    private void loadManual(){manual.clear();for(String raw:getConfig().getStringList("수동-이용자"))try{manual.add(UUID.fromString(raw));}catch(IllegalArgumentException e){getLogger().warning("잘못된 수동 이용자 UUID를 건너뜁니다.");}}
    private void remember(Player p,boolean enabled){if(enabled)manual.add(p.getUniqueId());else manual.remove(p.getUniqueId());if(getConfig().getBoolean("수동설정-기억",true)){getConfig().set("수동-이용자",manual.stream().map(UUID::toString).sorted().toList());saveConfig();}}
    @Override public void onDisable(){for(Player p:getServer().getOnlinePlayers()){Inventory top=p.getOpenInventory().getTopInventory();if(top==null)continue;if(top.getHolder() instanceof Preview)p.closeInventory();else clear(top);}active.clear();manual.clear();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("citytextures.use")){sender.sendMessage("§c도시팩 사용 권한이 없습니다.");return true;}
        if(args.length==1&&Set.of("관리","새로고침").contains(args[0])){
            if(!sender.hasPermission("citytextures.admin")){sender.sendMessage("§c운영자 전용입니다.");return true;}
            if(args[0].equals("새로고침")){reloadConfig();loadManual();sender.sendMessage("§a도시팩 설정을 다시 읽었습니다.");}
            sender.sendMessage("§6[도시팩] 아이콘 32종 · 자동 요청 "+getConfig().getBoolean("자동요청",false));
            try{requestSettings();sender.sendMessage("§aHTTPS ZIP 주소와 SHA-1 형식이 준비됐습니다. 외부 다운로드 성공을 보장하는 검사는 아닙니다.");}catch(IllegalArgumentException e){sender.sendMessage("§e"+e.getMessage());}return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 /도시팩을 사용하세요.");return true;}
        if(args.length==1)switch(args[0]){
            case "미리보기"->{preview(p);return true;}
            case "켜기"->{active.add(p.getUniqueId());remember(p,true);decorate(p,p.getOpenInventory().getTopInventory(),p.getOpenInventory().getTitle());p.sendMessage("§a도시 메뉴 아이콘을 켰습니다. ZIP을 적용하지 않았다면 게임 설정에서 먼저 선택하세요.");return true;}
            case "끄기"->{active.remove(p.getUniqueId());remember(p,false);clear(p.getOpenInventory().getTopInventory());p.sendMessage("§e도시 메뉴 아이콘을 껐습니다. 클라이언트 리소스팩 선택은 게임 설정에서 변경하세요.");return true;}
            case "받기"->{try{request(p);}catch(IllegalArgumentException e){p.sendMessage("§c"+e.getMessage());}return true;}
            default->{}
        }
        p.sendMessage("§6[시티 이코노미] 도시 생활 경제 리소스팩");p.sendMessage("§e/도시팩 미리보기 §7아이콘 보기");p.sendMessage("§e/도시팩 켜기 · /도시팩 끄기 §7내 메뉴 아이콘 설정");p.sendMessage("§e/도시팩 받기 §7서버 팩 다운로드 요청");return true;
    }
    record Request(String url,byte[] sha1){}
    Request requestSettings(){
        String url=getConfig().getString("팩-주소","").trim(),hex=getConfig().getString("체크섬-SHA1","").trim();
        URI uri;try{uri=URI.create(url);}catch(IllegalArgumentException e){throw new IllegalArgumentException("운영자가 공개 HTTPS ZIP 주소를 설정해야 합니다.");}
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("운영자가 공개 HTTPS ZIP 주소를 설정해야 합니다.");
        if(!hex.matches("[a-fA-F0-9]{40}"))throw new IllegalArgumentException("운영자가 배포 ZIP의 SHA-1을 설정해야 합니다.");
        return new Request(url,HexFormat.of().parseHex(hex));
    }
    void request(Player p){Request request=requestSettings();p.addResourcePack(PACK_ID,request.url,request.sha1,getConfig().getString("요청-문구","도시 경제 팩을 적용할까요?"),false);p.sendMessage("§e도시팩 다운로드 요청을 보냈습니다. 적용이 완료되면 메뉴 아이콘이 켜집니다.");}
    @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer();if(!getConfig().getBoolean("자동요청",false)){if(getConfig().getBoolean("수동설정-기억",true)&&manual.contains(p.getUniqueId()))active.add(p.getUniqueId());return;}
        getServer().getScheduler().runTaskLater(this,()->{if(!p.isOnline()||!p.hasPermission("citytextures.use"))return;try{request(p);}catch(IllegalArgumentException failure){getLogger().warning(failure.getMessage());}},40L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){active.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void packStatus(PlayerResourcePackStatusEvent e){
        if(!PACK_ID.equals(e.getID()))return;Player p=e.getPlayer();
        switch(e.getStatus()){
            case SUCCESSFULLY_LOADED->{if(p.hasPermission("citytextures.use")){active.add(p.getUniqueId());decorate(p,p.getOpenInventory().getTopInventory(),p.getOpenInventory().getTitle());p.sendMessage("§a도시팩 적용 완료. /메뉴 또는 쉬프트+F로 확인하세요.");}}
            case DECLINED,FAILED_DOWNLOAD,INVALID_URL,FAILED_RELOAD,DISCARDED->{active.remove(p.getUniqueId());clear(p.getOpenInventory().getTopInventory());p.sendMessage("§e도시팩이 적용되지 않아 기본 메뉴 아이콘을 사용합니다. 게임은 계속 이용할 수 있습니다.");}
            default->{}
        }
    }
    boolean trusted(Inventory inventory){
        if(inventory==null||inventory.getHolder()==null)return false;Class<?> holder=inventory.getHolder().getClass();String name=holder.getName();
        String owner=switch(name){case "kr.minecraft.survival.menu.KoreanMenuPlugin$Menu"->"KoreanMenu";case "kr.minecraft.survival.economy.KoreanEconomyPlugin$Market","kr.minecraft.survival.economy.Tycoon$Screen","kr.minecraft.survival.economy.LifeProgress$Menu"->"KoreanEconomy";default->null;};
        if(owner==null)return false;Plugin plugin=getServer().getPluginManager().getPlugin(owner);if(plugin==null||!plugin.isEnabled())return false;try{return Class.forName(plugin.getDescription().getMain(),false,holder.getClassLoader()).isInstance(plugin);}catch(ClassNotFoundException e){return false;}
    }
    String model(String title,int slot,ItemStack item){
        if(item==null||!item.hasItemMeta())return null;ItemMeta meta=item.getItemMeta();if(!meta.hasDisplayName())return null;String name=ChatColor.stripColor(meta.getDisplayName());
        // The upgrade slot contains a copy of the actual tool. Only decorate interface controls.
        if(title.contains("도구 강화")&&slot==22)return null;
        for(Icon icon:icons.values())if(name.equals(icon.label))return icon.id;
        if(name.contains("닫기")||name.equals("취소"))return "close";
        if(name.startsWith("확인 ·"))return "confirm";
        if(name.contains("마을 수첩"))return "business_ledger";
        if(name.contains("적립")&&name.contains("받기"))return "coin";
        if(name.startsWith("농부"))return "job_farmer";if(name.startsWith("광부"))return "job_miner";if(name.startsWith("낚시꾼"))return "job_fisher";
        if(name.contains("강화 시도"))return "upgrade_anvil";
        if(title.contains("일일 임무")&&Set.of(11,13,15).contains(slot))return "quest_clipboard";
        if(title.contains("오늘의 임무")&&((slot>=19&&slot<=24)||(slot>=37&&slot<=39)))return "quest_clipboard";
        return switch(name){
            case "내 돈 확인"->"wallet";case "송금 안내"->"transfer";case "서버 상점"->"shop";case "내 땅 보호"->"land_deed";
            case "집 위치 저장","이곳을 집으로 저장할까요?"->"home_key";case "일일 보상"->"daily_reward";case "유저 거래소"->"seller_crate";case "집으로 이동"->"home_door";
            case "스폰으로 이동"->"city_spawn";case "생활 직업","직업 선택·숙련도","직업 선택 · 진행도"->"jobs_case";case "요리 만들기"->"cooking_pan";case "도구 강화"->"upgrade_anvil";
            case "오늘의 임무","일일 임무"->"quest_clipboard";case "서버 도움말"->"help_book";case "구매 모드"->"wallet";case "판매 모드"->"seller_crate";default->null;
        };
    }
    void decorate(Player p,Inventory inventory,String title){
        if(!active.contains(p.getUniqueId())||!p.hasPermission("citytextures.use")||!trusted(inventory))return;
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack original=inventory.getItem(slot);String model=model(title,slot,original);if(model==null)continue;
            ItemStack shown=original.clone();ItemMeta meta=shown.getItemMeta();if(!meta.getPersistentDataContainer().has(previousModel,PersistentDataType.STRING))meta.getPersistentDataContainer().set(previousModel,PersistentDataType.STRING,meta.hasItemModel()?meta.getItemModel().toString():"");
            meta.setItemModel(new NamespacedKey("tycoon",model));shown.setItemMeta(meta);inventory.setItem(slot,shown);
        }
    }
    void clear(Inventory inventory){
        if(!trusted(inventory))return;
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack item=inventory.getItem(slot);if(item==null||!item.hasItemMeta())continue;ItemMeta meta=item.getItemMeta();String old=meta.getPersistentDataContainer().get(previousModel,PersistentDataType.STRING);if(old==null)continue;
            ItemStack restored=item.clone();meta.setItemModel(old.isEmpty()?null:NamespacedKey.fromString(old));meta.getPersistentDataContainer().remove(previousModel);restored.setItemMeta(meta);inventory.setItem(slot,restored);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player p)||!trusted(e.getInventory()))return;Inventory top=e.getInventory();getServer().getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==top)decorate(p,top,p.getOpenInventory().getTitle());});}
    @EventHandler(priority=EventPriority.MONITOR) public void close(InventoryCloseEvent e){clear(e.getInventory());}
    void preview(Player p){
        Preview menu=new Preview(this,p);int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,45,46,47,48};int index=0;
        for(Icon icon:icons.values()){ItemStack item=new ItemStack(icon.base);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§f"+icon.label);meta.setLore(List.of("§7시티 이코노미 · 디자인 미리보기"));meta.setItemModel(new NamespacedKey("tycoon",icon.id));item.setItemMeta(meta);menu.inventory.setItem(slots[index++],item);}
        ItemStack close=new ItemStack(Material.BARRIER);ItemMeta meta=close.getItemMeta();meta.setDisplayName("§c닫기");close.setItemMeta(meta);menu.inventory.setItem(49,close);p.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Preview menu))return;e.setCancelled(true);if(e.getRawSlot()==49&&e.getClick()==ClickType.LEFT&&e.getWhoClicked() instanceof Player p&&menu.owner.equals(p.getUniqueId()))getServer().getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==menu.inventory)p.closeInventory();});}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Preview)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length!=1)return List.of();return List.of("미리보기","켜기","끄기","받기").stream().filter(v->v.startsWith(args[0])).toList();}
}
