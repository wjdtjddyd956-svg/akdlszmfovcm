package kr.minecraft.survival.economy;

import java.text.NumberFormat;
import java.time.*;
import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public class KoreanEconomyPlugin extends JavaPlugin implements Listener {
    record Product(Material material,String name,double buy,double sell,int limit) {}
    private List<Product> products=List.of();
    private DailyClaims claims;
    private ZoneId zone=ZoneId.of("Asia/Seoul");
    private double daily=200;
    private boolean remoteMarket;
    private LifeProgress progress;
    private ServerTools tools;
    private final Map<UUID,Long> cooldown=new HashMap<>();
    NpcShops npcs;

    static final class Market implements InventoryHolder {
        final UUID owner;
        final boolean buying;
        final List<Product> prices;
        final Inventory inventory;
        final ShopCategory category;
        final Entity npc;
        Market(KoreanEconomyPlugin plugin,Player player,boolean buying,ShopCategory category,Entity npc) {
            owner=player.getUniqueId();this.buying=buying;this.category=category;this.npc=npc;
            prices=plugin.products.stream().filter(p->category.includes(p.material())).toList();
            inventory=plugin.getServer().createInventory(this,54,category.title+(buying?" | 물건 구매":" | 물건 판매"));
        }
        @Override public Inventory getInventory(){return inventory;}
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        var lifeSettings=LifeProgress.Settings.read(getConfig());
        readSettings();
        if(!getConfig().isSet("직업") || !getConfig().isSet("임무")
                || products.stream().anyMatch(product->!getConfig().isSet("상점."+product.name+".하루매입한도"))) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        progress=new LifeProgress(this,lifeSettings);
        tools=new ServerTools(this);
        claims=new DailyClaims(getDataFolder().toPath().resolve("보상기록"));
        npcs=new NpcShops(this,(player,category,npc)->{
            if(category==ShopCategory.QUEST) progress.open(player,false,npc);
            else open(player,false,category,npc);
        });
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(npcs,this);
        getServer().getPluginManager().registerEvents(progress,this);
        getServer().getPluginManager().registerEvents(new LifeActivity(this,progress),this);
        getLogger().info("NPC 상점 활성화: /상인 생성 잡화상인 전체 · 플레이어는 상인을 우클릭하세요.");
    }
    @Override public void onDisable() {
        closeMarkets();
        if(progress!=null) {progress.closeMenus();progress.flush();}
        cooldown.clear();
    }
    private void closeMarkets() {
        for (Player player:getServer().getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Market) player.closeInventory();
    }
    private void readSettings() {
        ZoneId nextZone=ZoneId.of(getConfig().getString("보상-시간대","Asia/Seoul"));
        double nextDaily=validPrice(getConfig().getDouble("일일보상",200));
        Material[] materials={Material.WHEAT,Material.CARROT,Material.POTATO,Material.COBBLESTONE,Material.OAK_LOG,Material.COAL,Material.IRON_INGOT,Material.DIAMOND};
        String[] names={"밀","당근","감자","조약돌","참나무원목","석탄","철주괴","다이아몬드"};
        double[] buys={10,8,8,4,12,24,80,800}, sells={2,1,1,1,3,6,20,200};
        int[] limits={128,128,128,128,96,64,48,12};
        List<Product> next=new ArrayList<>();
        for (int i=0;i<materials.length;i++) {
            double buy=validPrice(getConfig().getDouble("상점."+names[i]+".구매가",buys[i]));
            double sell=validPrice(getConfig().getDouble("상점."+names[i]+".판매가",sells[i]));
            if (sell>buy) throw new IllegalArgumentException(names[i]+": 판매가는 구매가보다 클 수 없습니다.");
            int limit=LifeProgress.integer(getConfig(),"상점."+names[i]+".하루매입한도",limits[i],0,1_000_000);
            next.add(new Product(materials[i],names[i],buy,sell,limit));
        }
        products=List.copyOf(next);
        zone=nextZone;
        daily=nextDaily;
        remoteMarket=getConfig().getBoolean("상점-명령어열기",false);
    }
    static double validPrice(double value) {
        if (!Double.isFinite(value) || value<1 || value>1_000_000 || Math.rint(value)!=value) throw new IllegalArgumentException("가격과 보상은 1~1000000 사이 정수여야 합니다.");
        return value;
    }
    Economy wallet() {
        RegisteredServiceProvider<Economy> registration=getServer().getServicesManager().getRegistration(Economy.class);
        return registration==null?null:registration.getProvider();
    }
    String money(double value) {return NumberFormat.getIntegerInstance(Locale.KOREA).format(value)+"원";}
    ZoneId zone() {return zone;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String root=command.getName();
        if(root.equals("상인"))return npcs.command(sender,args);
        if(root.equals("경제거래")) {progress.review(sender,args);return true;}
        if(ServerTools.COMMANDS.contains(root)) {tools.command(sender,root,args);return true;}
        if (root.equals("경제설정")) {
            if (!sender.hasPermission("koreaneconomy.admin")) {sender.sendMessage("§c운영자만 경제 설정을 변경할 수 있습니다.");return true;}
            if (args.length!=1 || !args[0].equals("새로고침")) {sender.sendMessage("§e사용법: /경제설정 새로고침");return true;}
            try {
                reloadConfig();
                var settings=LifeProgress.Settings.read(getConfig());
                readSettings();
                progress.settings=settings;
                closeMarkets();
                progress.closeMenus();
                sender.sendMessage("§a경제 설정을 다시 읽었습니다. 상점과 직업 메뉴를 다시 여세요.");
            } catch (RuntimeException e) {sender.sendMessage("§c설정이 올바르지 않아 적용을 취소했습니다.");getLogger().warning(e.toString());}
            return true;
        }
        if (!(sender instanceof Player player)) {sender.sendMessage("게임 안에서 사용하세요.");return true;}
        if (!player.hasPermission("koreaneconomy.use")) {player.sendMessage("§c경제 기능을 사용할 권한이 없습니다.");return true;}
        switch(root) {
            case "상점" -> {
                if(remoteMarket)open(player,false,ShopCategory.ALL,null);
                else player.sendMessage("§e마을의 상점 NPC를 우클릭하면 구매·판매 화면이 열립니다.");
            }
            case "일일보상" -> reward(player);
            case "직업" -> progress.open(player,true,null);
            case "임무" -> progress.open(player,false,null);
            default -> {
                player.sendMessage("§6[생활 경제 안내]");
                player.sendMessage("§e/돈 §f잔액 · §e/송금 닉네임 금액 §f송금");
                player.sendMessage("§e상점 NPC 우클릭 §f농산물·광물 거래 · §e/일일보상 §f하루 한 번 보상");
                player.sendMessage("§e/직업 §f생활 직업 · §e/임무 §f일일 임무 · §e/땅보호 §f내 땅 관리");
                player.sendMessage("§e/거래소 생성 가격 §f유저 상점 · §e/거래소 직원 추가 닉네임");
                player.sendMessage("§e/집저장 §f집 저장 · §e/집 §f집 이동 · §e/스폰 §f마을 이동");
                player.sendMessage("§e/이동요청 닉네임 §f이동 요청 · §e/이동수락 §f수락");
                player.sendMessage("§7쉬프트+F 또는 /메뉴로 메뉴를 열 수 있습니다.");
            }
        }
        return true;
    }
    ItemStack icon(Material type,String name,String... lines) {
        ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lines));item.setItemMeta(meta);return item;
    }
    void open(Player player,boolean buying,ShopCategory category,Entity npc) {
        if (!player.isOnline() || !player.hasPermission("koreaneconomy.use")) return;
        Market menu=new Market(this,player,buying,category,npc);
        for(int i=0;i<54;i++) menu.inventory.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," "));
        menu.inventory.setItem(4,icon(Material.BOOK,buying?"§a서버에서 물건 구매":"§6서버에 물건 판매","§7좌클릭 1개 · 쉬프트 클릭 16개 · 우클릭 64개","§7이름·설명·마법이 없는 기본 아이템만 판매됩니다.","§7장비칸과 보조 손 아이템은 거래하지 않습니다."));
        for(int i=0;i<menu.prices.size();i++) {
            Product p=menu.prices.get(i);
            menu.inventory.setItem(19+i,productIcon(player,p,buying));
        }
        menu.inventory.setItem(45,icon(Material.EMERALD,"§a구매 모드","§7서버에 돈을 내고 아이템을 받습니다."));
        menu.inventory.setItem(53,icon(Material.GOLD_INGOT,"§6판매 모드","§7아이템을 팔고 돈을 받습니다."));
        menu.inventory.setItem(49,icon(Material.BARRIER,"§c닫기"));
        player.openInventory(menu.inventory);
    }
    private ItemStack productIcon(Player player,Product product,boolean buying) {
        return icon(product.material,"§e"+product.name,"§7내가 살 때: §a"+money(product.buy),"§7내가 팔 때: §6"+money(product.sell),
                "§7오늘 서버에 팔 수 있는 수량: §e"+progress.remaining(player,product.material,product.limit)+"/"+product.limit+"개",
                "§7초기화 "+progress.reset(player),"§7현재: "+(buying?"구매 모드":"판매 모드"));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Market menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return;
        int slot=event.getRawSlot();
        int amount=event.isShiftClick()?16:(event.isRightClick()?64:1);
        getServer().getScheduler().runTask(this,()->{
            if (!player.isOnline() || !player.hasPermission("koreaneconomy.use") || player.getOpenInventory().getTopInventory()!=menu.inventory) return;
            if(menu.npc!=null&&!npcs.usable(player,menu.npc)){player.closeInventory();player.sendMessage("§c상인과 멀어졌거나 상인이 사라졌습니다. 다시 우클릭하세요.");return;}
            if(slot==45) open(player,true,menu.category,menu.npc);
            else if(slot==53) open(player,false,menu.category,menu.npc);
            else if(slot==49) player.closeInventory();
            else if(slot>=19 && slot<19+menu.prices.size()) {
                Product product=menu.prices.get(slot-19);
                trade(player,product,amount,menu.buying);
                menu.inventory.setItem(slot,productIcon(player,product,menu.buying));
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Market) event.setCancelled(true);
    }
    private void trade(Player player,Product product,int amount,boolean buying) {
        long now=System.currentTimeMillis();Long last=cooldown.put(player.getUniqueId(),now);
        if(last!=null && now-last<250) return;
        if(!LifeProgress.eligible(player)) {player.sendMessage("§c생존 또는 모험 모드에서 거래하세요.");return;}
        ItemStack[] before=player.getInventory().getStorageContents();
        ItemStack[] after=InventoryPlan.plan(before,product.material,amount,buying);
        if(after==null) {player.sendMessage(buying?"§c가방 공간이 부족합니다.":"§c판매할 기본 아이템이 부족합니다. 이름·설명·마법이 붙은 물건은 제외됩니다.");return;}
        double total=(buying?product.buy:product.sell)*amount;
        if(!progress.trade(player,product.material,product.name,amount,product.limit,buying,total,before,after))return;
        player.sendMessage("§a"+product.name+" "+amount+"개를 "+(buying?"구매":"판매")+"했습니다. §e"+money(total));
        getLogger().info("상점 거래 | "+player.getUniqueId()+" | "+(buying?"구매":"판매")+" | "+product.name+" | "+amount+" | "+total);
    }
    private void reward(Player player) {
        Economy economy=wallet();
        if(economy==null) {player.sendMessage("§c돈 관리 기능이 연결되지 않았습니다.");return;}
        try {
            DailyClaims.Result result=claims.claim(player.getUniqueId(),LocalDate.now(zone),()->{
                EconomyResponse r=economy.depositPlayer(player,daily);return r!=null && r.transactionSuccess();
            });
            player.sendMessage(switch(result) {
                case PAID -> "§a오늘의 보상 "+money(daily)+"을 받았습니다.";
                case ALREADY -> "§e오늘 보상은 이미 받았습니다. 보상 날짜가 바뀌면 다시 받을 수 있습니다.";
                case FAILED -> "§c보상을 지급하지 못했습니다. 잠시 후 다시 시도하세요.";
                case REVIEW -> "§c처리 중인 보상 기록이 있습니다. 중복 지급을 막기 위해 운영자 확인이 필요합니다.";
            });
        } catch(Exception e) {getLogger().severe("보상 처리 확인 필요: "+player.getUniqueId()+" "+e);player.sendMessage("§c보상 기록 처리 중 오류가 발생했습니다. 운영자에게 문의하세요.");}
    }
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {cooldown.remove(event.getPlayer().getUniqueId());}
}
