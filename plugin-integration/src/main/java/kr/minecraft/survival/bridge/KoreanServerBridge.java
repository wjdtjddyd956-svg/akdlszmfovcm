package kr.minecraft.survival.bridge;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import net.milkbowl.vault.economy.Economy;
import net.luckperms.api.*;
import net.luckperms.api.node.types.*;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import kr.minecraft.survival.economy.KoreanEconomyPlugin;
import kr.minecraft.survival.economy.domain.Rules;
import kr.minecraft.survival.codex.KoreanCodexPlugin;
import eu.decentsoftware.holograms.api.DHAPI;

/** No wallet, inventory, reward, claim or teleport mutations. Original commands retain their sender. */
public final class KoreanServerBridge extends JavaPlugin implements Listener {
    private volatile Map<UUID,DisplayProfile> profiles=Map.of();
    private volatile int online;
    private final Map<UUID,Long> clicks=new HashMap<>();
    private KoreanEconomyPlugin economyPlugin;
    private Economy wallet;
    private LuckPerms permissions;
    private Expansion expansion;
    private volatile boolean rolesReady;
    private String lastFailure="";
    private NamespacedKey button;
    private final class Screen implements InventoryHolder {
        final UUID owner; final Inventory inventory;
        Screen(Player p){owner=p.getUniqueId();inventory=Bukkit.createInventory(this,54,"마을 안내 | 실제 생활 정보");}
        @Override public Inventory getInventory(){return inventory;}
    }
    @Override public void onEnable(){
        saveDefaultConfig();button=new NamespacedKey(this,"action");
        economyPlugin=(KoreanEconomyPlugin)Bukkit.getPluginManager().getPlugin("KoreanEconomy");
        var provider=Bukkit.getServicesManager().getRegistration(Economy.class);
        if(provider==null)throw new IllegalStateException("Vault 경제 공급자가 없습니다.");
        wallet=provider.getProvider();permissions=LuckPermsProvider.get();
        expansion=new Expansion();if(!expansion.register())throw new IllegalStateException("koreanlife 표시 키가 이미 등록됐습니다.");
        Bukkit.getPluginManager().registerEvents(this,this);
        configureRoles();refresh();
        Bukkit.getScheduler().runTaskTimer(this,this::refresh,20,Math.max(20,getConfig().getInt("정보갱신틱",40)));
        Bukkit.getScheduler().runTaskLater(this,this::holograms,100);
        getLogger().info("실제 Vault·직업·도감 정보 연결 준비. 야생 이동은 검증 대기 상태입니다.");
    }
    @Override public void onDisable(){
        if(expansion!=null)expansion.unregister();
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Screen)p.closeInventory();
        profiles=Map.of();clicks.clear();
    }
    /** Only add baseline display permissions. Never remove existing groups, assign OP, '*' or bypasses. */
    private void configureRoles(){
        File marker=new File(getDataFolder(),"권한초기화완료.txt");
        try{if(marker.isFile()&&Files.readString(marker.toPath()).startsWith("2\n")){rolesReady=true;return;}}catch(IOException e){getLogger().warning("권한 표식을 읽지 못해 안전한 추가 설정을 다시 확인합니다.");}
        var gm=permissions.getGroupManager();
        CompletableFuture.allOf(gm.createAndLoadGroup("default"),gm.createAndLoadGroup("resident"),gm.createAndLoadGroup("admin"))
            .thenCompose(v->{
                var base=gm.getGroup("default");var resident=gm.getGroup("resident");var admin=gm.getGroup("admin");
                // New positive grants are limited to the new display feature.
                base.data().add(net.luckperms.api.node.Node.builder("koreanbridge.use").build());
                base.data().add(net.luckperms.api.node.Node.builder("tab.scoreboard.toggle").build());
                if(base.getCachedData().getMetaData().getPrefix()==null)base.data().add(PrefixNode.builder("&7[주민] ",10).build());
                resident.data().add(InheritanceNode.builder("default").build());
                if(resident.getCachedData().getMetaData().getPrefix()==null)resident.data().add(PrefixNode.builder("&a[단골] ",20).build());
                // Explicit operator rank: no money creation, wildcard, OP, claim bypass or rank editing.
                admin.data().add(InheritanceNode.builder("default").build());
                if(admin.getCachedData().getMetaData().getPrefix()==null)admin.data().add(PrefixNode.builder("&6[운영자] ",30).build());
                for(String node:List.of("koreanbridge.admin","koreaneconomy.operations","koreancodex.admin","chunky.command.progress","chunky.command.pause","chunky.command.continue","dh.command.list"))
                    admin.data().add(net.luckperms.api.node.Node.builder(node).build());
                if(base.getWeight().isEmpty())base.data().add(WeightNode.builder(1).build());
                if(resident.getWeight().isEmpty())resident.data().add(WeightNode.builder(20).build());
                if(admin.getWeight().isEmpty())admin.data().add(WeightNode.builder(30).build());
                return CompletableFuture.allOf(gm.saveGroup(base),gm.saveGroup(resident),gm.saveGroup(admin));
            }).whenComplete((v,error)->{
                if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"기본 등급 저장 실패. 기존 권한은 유지합니다.",error);return;}
                try{Files.writeString(marker.toPath(),"2\n주민·단골·운영자 등급. 운영자 등급은 제한된 운영 권한만 갖습니다. 돈 생성·권한 편집·OP·보호 우회는 주지 않습니다.\n");rolesReady=true;}
                catch(IOException e){getLogger().log(java.util.logging.Level.SEVERE,"권한 초기화 표식 저장 실패",e);}
            });
    }
    private void refresh(){
        var result=new HashMap<UUID,DisplayProfile>();online=Bukkit.getOnlinePlayers().size();
        for(Player p:Bukkit.getOnlinePlayers())try{
            var id=p.getUniqueId();var store=economyPlugin.getEconomyStateStore();
            Rules.Job job=store.job(id);var user=permissions.getUserManager().getUser(id);
            String group=user==null?"":user.getPrimaryGroup();
            String rank=p.isOp()?"운영자":DisplayProfile.rank(group);
            String prefix=user==null?null:user.getCachedData().getMetaData().getPrefix();
            Map<String,String> fields=new HashMap<>();
            double amount=wallet.getBalance(p);
            fields.put("money",DisplayProfile.won(amount));fields.put("balance",Double.toString(amount));
            fields.put("job",job==null?"미선택":job.label);
            fields.put("job_level",job==null?"-":Integer.toString(store.progress(id,job).level()));
            fields.put("farmer_level",Integer.toString(store.progress(id,Rules.Job.FARMER).level()));
            fields.put("owed",DisplayProfile.won(store.owed(id)/100d));
            fields.put("rank",rank);fields.put("prefix",p.isOp()?"&6[운영자] ":prefix==null?"&7["+rank+"] ":prefix);
            fields.put("online",Integer.toString(online));
            if(Bukkit.getPluginManager().getPlugin("KoreanCodex") instanceof KoreanCodexPlugin codex && codex.isEnabled()){
                int[] counts=codex.collectionSummary(p);
                fields.put("codex",counts[0]+"/"+counts[1]);fields.put("codex_count",Integer.toString(counts[0]));
                fields.put("achievements",counts[3]+"/"+counts[4]);
            }else{fields.put("codex","연결 대기");fields.put("codex_count","연결 대기");fields.put("achievements","연결 대기");}
            result.put(id,new DisplayProfile(fields));
        }catch(RuntimeException ex){
            // Never replace unavailable persisted data with a misleading zero.
            String detail=ex.getClass().getSimpleName()+":"+ex.getMessage();
            if(!detail.equals(lastFailure)){getLogger().warning("생활 정보 조회 실패: "+detail);lastFailure=detail;}
        }
        profiles=Map.copyOf(result);
    }
    public String display(UUID id,String key){
        if(key.equals("online"))return Integer.toString(online);
        DisplayProfile p=profiles.get(id);return p==null?"확인 중":p.value(key);
    }
    private final class Expansion extends PlaceholderExpansion {
        @Override public String getIdentifier(){return "koreanlife";}
        @Override public String getAuthor(){return "wjdtjddyd956-svg";}
        @Override public String getVersion(){return KoreanServerBridge.this.getDescription().getVersion();}
        @Override public boolean persist(){return true;}
        @Override public String onRequest(OfflinePlayer p,String key){return p==null?(key.equals("online")?Integer.toString(online):"접속 후 확인"):display(p.getUniqueId(),key);}
    }
    @EventHandler public void quit(PlayerQuitEvent e){clicks.remove(e.getPlayer().getUniqueId());var copy=new HashMap<>(profiles);copy.remove(e.getPlayer().getUniqueId());profiles=Map.copyOf(copy);}
    private ItemStack icon(Material material,String name,String action,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));
        meta.getPersistentDataContainer().set(button,PersistentDataType.STRING,action);item.setItemMeta(meta);return item;
    }
    private String value(Player p,String key){String v=display(p.getUniqueId(),key);return v==null?"연결 대기":v;}
    private void open(Player p){
        if(!p.hasPermission("koreanbridge.use")){p.sendMessage("§c마을 정보를 열 권한이 없습니다.");return;}
        refresh();Screen s=new Screen(p);
        s.inventory.setItem(4,icon(Material.COMPASS,"§b마을 안내·내 생활 정보","info","§7실제 저장 기록을 읽습니다. 갱신: 최대 2초","§7현재 등급: "+value(p,"rank")));
        s.inventory.setItem(19,icon(Material.GOLD_INGOT,"§6내 잔액: "+value(p,"money"),"money","§7Vault 기존 잔액 · 별도 화폐 없음"));
        s.inventory.setItem(20,icon(Material.IRON_PICKAXE,"§b직업: "+value(p,"job"),"job","§7현재 직업 레벨: "+value(p,"job_level"),"§7농부 저장 레벨: "+value(p,"farmer_level"),"§7직업 적립금: "+value(p,"owed"),"§a클릭: 직업 메뉴"));
        s.inventory.setItem(21,icon(Material.BOOK,"§b도감: "+value(p,"codex"),"codex","§7업적: "+value(p,"achievements"),"§a클릭: 아이템 도감"));
        s.inventory.setItem(23,icon(Material.PLAYER_HEAD,"§a접속 중: "+online+"명","info","§7현재 서버의 실제 접속자 수"));
        s.inventory.setItem(24,icon(Material.MAP,"§e야생 이동 · 검증 대기","wild", "§7현재 이동·비용 차감 없음","§7마을 NPC에서 상점·임무·씨앗을 이용하세요."));
        s.inventory.setItem(25,icon(Material.OAK_SIGN,"§b정보판 켜기·끄기","toggle","§7TAB 사이드바를 개인별로 전환합니다."));
        s.inventory.setItem(31,icon(Material.CHEST,"§a상점·마을 NPC 안내","shop","§7마을의 기존 상인을 우클릭하세요.","§7농산물·광물·잡화·요리·임무 NPC 유지"));
        s.inventory.setItem(40,icon(Material.SHIELD,"§b보호·기록·백업 안내","help","§7땅: /땅보호 · 친구 초대·권한 회수","§7기록 조회·복구·백업은 운영자 전용","§7자동 백업: 기존 Windows 관리자 사용"));
        s.inventory.setItem(45,icon(Material.ARROW,"§b이전 화면 · 마을 수첩","back","§7쉬프트+F 메뉴로 돌아갑니다."));
        s.inventory.setItem(49,icon(Material.BARRIER,"§c닫기","close"));p.openInventory(s.inventory);
    }
    private boolean mainMenu(Inventory inventory){return inventory.getHolder()!=null&&inventory.getHolder().getClass().getName().equals("kr.minecraft.survival.menu.KoreanMenuPlugin$Menu");}
    @EventHandler(priority=EventPriority.HIGHEST) public void inject(InventoryOpenEvent e){
        if(!(e.getPlayer() instanceof Player)||!mainMenu(e.getInventory()))return;
        // Empty slot only. Do not overwrite future menus or user buttons.
        ItemStack existing=e.getInventory().getItem(47);
        boolean blank=existing==null||(existing.getType()==Material.GRAY_STAINED_GLASS_PANE&&existing.hasItemMeta()&&" ".equals(existing.getItemMeta().getDisplayName()));
        if(e.getView().getTitle().contains("마을 수첩")&&blank){
            ItemStack item=icon(Material.COMPASS,"§b마을 안내·서버 정보","open","§7내 잔액·직업·도감·접속자","§7정보판 전환과 야생 이동 준비 상태");
            ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("tycoon","ui_icon"),PersistentDataType.STRING,"help_book");item.setItemMeta(meta);
            e.getInventory().setItem(47,item);
        }
    }
    @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){
        Inventory top=e.getView().getTopInventory();if(!(e.getWhoClicked() instanceof Player p))return;
        boolean own=top.getHolder() instanceof Screen;
        boolean entry=mainMenu(top)&&e.getRawSlot()==47&&e.getCurrentItem()!=null&&e.getCurrentItem().hasItemMeta()&&"open".equals(e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(button,PersistentDataType.STRING));
        if(!own&&!entry)return;e.setCancelled(true);
        if(own&&!((Screen)top.getHolder()).owner.equals(p.getUniqueId()))return;
        if(e.getRawSlot()<0||e.getRawSlot()>=top.getSize()||e.getClick()!=ClickType.LEFT)return;
        ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta())return;
        String action=item.getItemMeta().getPersistentDataContainer().get(button,PersistentDataType.STRING);if(action==null)return;
        long now=System.nanoTime();Long previous=clicks.put(p.getUniqueId(),now);if(previous!=null&&now-previous<250_000_000L)return;
        Bukkit.getScheduler().runTask(this,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=top)return;
            p.closeInventory();switch(action){
                case "open"->open(p);case "job"->p.performCommand("직업");case "codex"->p.performCommand("도감");
                case "money"->p.performCommand("돈");case "back"->p.performCommand("메뉴");case "shop"->p.performCommand("상점");
                case "toggle"->toggle(p);case "wild"->wild(p);case "help"->p.sendMessage("§b땅보호 메뉴에서 땅·친구 권한을 관리하세요. 기록 조회·복구·백업은 운영자에게 요청하세요.");
                default->{} }
        });
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
    private void wild(CommandSender p){p.sendMessage("§e"+getConfig().getString("야생이동안내"));}
    private void toggle(Player p){if(!p.hasPermission("tab.scoreboard.toggle")){p.sendMessage("§c정보판 전환 권한이 없습니다.");return;}p.performCommand("tab scoreboard toggle");}
    private boolean admin(CommandSender sender){if(!(sender instanceof Player p))return true;if(!p.hasPermission("koreanbridge.admin")){p.sendMessage("§c운영자 전용 명령입니다.");return false;}return true;}
    private void forward(CommandSender sender,String command,String permission){
        if(sender instanceof Player p){if(!p.hasPermission(permission)){p.sendMessage("§c원래 플러그인의 권한이 없어 실행하지 않았습니다.");return;}p.performCommand(command);}
        else Bukkit.dispatchCommand(sender,command);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        switch(command.getName()){
            case "마을정보"->{if(sender instanceof Player p)open(p);else sender.sendMessage("한국어 마을 정보 연결 활성. 권한 초기화: "+rolesReady+" · 접속자 "+online+"명");}
            case "야생"->wild(sender);
            case "정보표시"->{if(sender instanceof Player p)toggle(p);else sender.sendMessage("게임 안에서 사용하세요.");}
            case "권한관리"->{if(!admin(sender))return true;
                if(args.length==1&&args[0].equals("목록"))forward(sender,"lp listgroups","luckperms.listgroups");
                else if(args.length==2&&args[0].equals("확인")&&validName(args[1]))forward(sender,"lp user "+args[1]+" info","luckperms.user.info");
                else if(args.length==3&&args[0].equals("등급")&&validName(args[1])&&Map.of("주민","default","단골","resident","운영자","admin").containsKey(args[2])){
                    // ADD a parent instead of SET: existing ranks/permissions are never erased.
                    forward(sender,"lp user "+args[1]+" parent add "+Map.of("주민","default","단골","resident","운영자","admin").get(args[2]),"luckperms.user.parent.add");
                    sender.sendMessage("§7기존 등급을 보존했습니다. 운영자 등급도 OP·돈 생성·권한 편집·보호 우회는 받지 않습니다.");
                }else sender.sendMessage("§b/권한관리 목록 | 확인 닉네임 | 등급 닉네임 주민·단골·운영자");
            }
            case "지형생성"->{if(!admin(sender))return true;String sub=args.length==1?Map.of("상태","progress","일시정지","pause","계속","continue","시작","start").get(args[0]):null;
                if(sub==null)sender.sendMessage("§b/지형생성 상태|일시정지|계속|시작 · 시작 전 월드 경계와 Chunky 선택 영역을 확인하세요.");
                else forward(sender,"chunky "+sub,"chunky.command."+sub);
            }
            case "안내판관리"->{if(!admin(sender))return true;if(args.length==1&&args[0].equals("연결")){holograms();sender.sendMessage("§b고정 이름의 안내판 연결을 확인했습니다. 기존 이름 충돌은 덮어쓰지 않습니다.");}
                else if(args.length==1&&args[0].equals("목록"))forward(sender,"dh list","dh.command.list");
                else sender.sendMessage("§b/안내판관리 연결|목록");
            }
        }return true;
    }
    private static boolean validName(String name){return name.matches("[A-Za-z0-9_]{1,16}");}
    /** Exactly one saved welcome hologram, independent of NPC entity creation. No NPCs are spawned. */
    private void holograms(){
        if(!getConfig().getBoolean("마을안내판",true)||!Bukkit.getPluginManager().isPluginEnabled("DecentHolograms"))return;
        try{
            File file=new File(getDataFolder(),"안내판소유.yml");YamlConfiguration state=YamlConfiguration.loadConfiguration(file);
            String name=state.getString("이름");
            if(name==null){name="koreanlife_town";int suffix=1;while(DHAPI.getHologram(name)!=null)name="koreanlife_town_"+suffix++;
                // Save ownership before external side effects; on failure no hologram is created.
                state.set("이름",name);state.save(file);
            }
            var lines=List.of("&3&l생활 경제 마을","&f쉬프트+F &7→ &b마을 수첩","&7상인 우클릭: &f구매·판매·씨앗·요리·임무","&7내 잔액 &6%koreanlife_money% &8| &7도감 &b%koreanlife_codex%","&7야생 이동: &e검증 대기 (비용 차감 없음)");
            var hologram=DHAPI.getHologram(name);
            if(hologram==null){World world=Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==World.Environment.NORMAL).findFirst().orElseThrow();
                Location at=world.getSpawnLocation().clone().add(0,3,0);
                // The nominal spawn Y may be below a roof. Display above the highest solid surface.
                at.setY(Math.max(at.getY(),world.getHighestBlockYAt(at)+2.5));
                DHAPI.createHologram(name,at,true,lines);
            }else DHAPI.setHologramLines(hologram,lines);
            getLogger().info("한국어 마을 안내판 연결: "+name);
        }catch(Exception e){getLogger().log(java.util.logging.Level.WARNING,"안내판 연결 실패. 기존 NPC와 안내판은 보존합니다.",e);}
    }
}
