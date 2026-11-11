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
    record Product(Material material,String name,double buy,double sell) {}
    private List<Product> products=List.of();
    private DailyClaims claims;
    private ZoneId zone=ZoneId.of("Asia/Seoul");
    private double daily=200;
    private final Map<UUID,Long> cooldown=new HashMap<>();
    NpcShops npcs;
    Tycoon tycoon;

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
        saveDefaultConfig();readSettings();
        claims=new DailyClaims(getDataFolder().toPath().resolve("보상기록"));
        npcs=new NpcShops(this,(player,category,npc)->{if(category==ShopCategory.FOOD)tycoon.safe(player,()->tycoon.foodShop(player,npc));else if(category==ShopCategory.QUESTS)tycoon.safe(player,()->tycoon.quests(player,npc));else open(player,false,category,npc);});
        try{tycoon=new Tycoon(this);}catch(RuntimeException e){getLogger().log(java.util.logging.Level.SEVERE,"생활경제 초기화 실패",e);getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(npcs,this);
        getLogger().info("NPC 상점 활성화: /상인 생성 잡화상인 전체 · 플레이어는 상인을 우클릭하세요.");
    }
    @Override public void onDisable() {
        closeMarkets();
        if(tycoon!=null)tycoon.close();
        cooldown.clear();
    }
    private void closeMarkets() {
        for (Player player:getServer().getOnlinePlayers()) if (player.getOpenInventory().getTopInventory()!=null && player.getOpenInventory().getTopInventory().getHolder() instanceof Market) player.closeInventory();
    }
    private void readSettings() {
        zone=ZoneId.of(getConfig().getString("보상-시간대","Asia/Seoul"));
        daily=validPrice(getConfig().getDouble("일일보상",200));
        Material[] materials={Material.WHEAT,Material.CARROT,Material.POTATO,Material.COBBLESTONE,Material.OAK_LOG,Material.COAL,Material.IRON_INGOT,Material.DIAMOND};
        String[] names={"밀","당근","감자","조약돌","참나무원목","석탄","철주괴","다이아몬드"};
        double[] buys={10,8,8,4,12,24,80,800}, sells={2,1,1,1,3,6,20,200};
        List<Product> next=new ArrayList<>();
        for (int i=0;i<materials.length;i++) {
            double buy=validPrice(getConfig().getDouble("상점."+names[i]+".구매가",buys[i]));
            double sell=validPrice(getConfig().getDouble("상점."+names[i]+".판매가",sells[i]));
            if (sell>buy) throw new IllegalArgumentException(names[i]+": 판매가는 구매가보다 클 수 없습니다.");
            next.add(new Product(materials[i],names[i],buy,sell));
        }
        products=List.copyOf(next);
    }
    static double validPrice(double value) {
        if (!Double.isFinite(value) || value<1 || value>1_000_000 || Math.rint(value)!=value) throw new IllegalArgumentException("가격과 보상은 1~1000000 사이 정수여야 합니다.");
        return value;
    }
    private Economy wallet() {
        RegisteredServiceProvider<Economy> registration=getServer().getServicesManager().getRegistration(Economy.class);
        return registration==null?null:registration.getProvider();
    }
    private String money(double value) {return NumberFormat.getIntegerInstance(Locale.KOREA).format(value)+"원";}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String root=command.getName();
        if(root.equals("마을설치"))return npcs.town(sender,args);
        if(root.equals("상인"))return npcs.command(sender,args);
        if(tycoon.command(sender,root,args))return true;
        if (root.equals("경제설정")) {
            if (!sender.hasPermission("koreaneconomy.admin")) {sender.sendMessage("§c운영자만 경제 설정을 변경할 수 있습니다.");return true;}
            if (args.length!=1 || !args[0].equals("새로고침")) {sender.sendMessage("§e사용법: /경제설정 새로고침");return true;}
            List<Product> old=products;double oldDaily=daily;ZoneId oldZone=zone;
            try {reloadConfig();readSettings();closeMarkets();sender.sendMessage("§a경제 설정을 다시 읽었습니다. 바뀐 가격을 적용하려면 상점을 다시 여세요.");}
            catch (RuntimeException e) {products=old;daily=oldDaily;zone=oldZone;sender.sendMessage("§c설정이 올바르지 않아 기존 설정을 유지합니다.");getLogger().warning(e.toString());}
            return true;
        }
        if (!(sender instanceof Player player)) {sender.sendMessage("게임 안에서 사용하세요.");return true;}
        if (!player.hasPermission("koreaneconomy.use")) {player.sendMessage("§c경제 기능을 사용할 권한이 없습니다.");return true;}
        switch(root) {
            case "상점" -> {
                if(getConfig().getBoolean("상점-명령어열기",false))open(player,false,ShopCategory.ALL,null);
                else player.sendMessage("§e마을의 상점 NPC를 우클릭하면 구매·판매 화면이 열립니다.");
            }
            case "일일보상" -> reward(player);
            default -> {
                player.sendMessage("§6[생활 경제 안내]");
                player.sendMessage("§e/돈 §f잔액 · §e/송금 닉네임 금액 §f송금");
                player.sendMessage("§e상점 NPC 우클릭 §f농산물·광물 거래 · §e/일일보상 §f하루 한 번 보상");
                player.sendMessage("§e/거래소 생성 가격 §f유저 상점 · §e/거래소 직원 추가 닉네임");
                player.sendMessage("§e/집저장 §f집 저장 · §e/집 §f집 이동 · §e/스폰 §f마을 이동");
                player.sendMessage("§e/이동요청 닉네임 §f이동 요청 · §e/이동수락 §f수락");
                player.sendMessage("§e/직업 · /요리 · /강화 · /임무 · /땅보호 §f생활 타이쿤");
                player.sendMessage("§7쉬프트+F 또는 /메뉴로 메뉴를 열 수 있습니다.");
            }
        }
        return true;
    }
    private ItemStack icon(Material type,String name,String... lines) {
        ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lines));item.setItemMeta(meta);return item;
    }
    void open(Player player,boolean buying,ShopCategory category,Entity npc) {
        if (!player.isOnline() || !player.hasPermission("koreaneconomy.use")) return;
        Market menu=new Market(this,player,buying,category,npc);
        for(int i=0;i<54;i++) menu.inventory.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," "));
        menu.inventory.setItem(4,icon(Material.BOOK,buying?"§a서버에서 물건 구매":"§6서버에 물건 판매","§7좌클릭 1개 · 쉬프트 클릭 16개 · 우클릭 64개","§7이름·설명·마법이 없는 기본 아이템만 판매됩니다.","§7장비칸·보조 손 제외 / 품목별 하루 매입 256개"));
        for(int i=0;i<menu.prices.size();i++) {
            Product p=menu.prices.get(i);
            menu.inventory.setItem(19+i,icon(p.material,"§e"+p.name,"§7내가 살 때: §a"+money(p.buy),"§7내가 팔 때: §6"+money(p.sell),"§7현재: "+(buying?"구매 모드":"판매 모드")));
        }
        menu.inventory.setItem(45,icon(Material.EMERALD,"§a구매 모드","§7서버에 돈을 내고 아이템을 받습니다."));
        menu.inventory.setItem(53,icon(Material.GOLD_INGOT,"§6판매 모드","§7아이템을 팔고 돈을 받습니다."));
        menu.inventory.setItem(49,icon(Material.BARRIER,"§c닫기"));
        player.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Market menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return;
        if(!Set.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT).contains(event.getClick()))return;
        int slot=event.getRawSlot();
        int amount=event.isShiftClick()?16:(event.isRightClick()?64:1);
        getServer().getScheduler().runTask(this,()->{
            if (!player.isOnline() || !player.hasPermission("koreaneconomy.use") || player.getOpenInventory().getTopInventory()!=menu.inventory) return;
            if(menu.npc!=null&&!npcs.usable(player,menu.npc)){player.closeInventory();player.sendMessage("§c상인과 멀어졌거나 상인이 사라졌습니다. 다시 우클릭하세요.");return;}
            if(slot==45) open(player,true,menu.category,menu.npc);
            else if(slot==53) open(player,false,menu.category,menu.npc);
            else if(slot==49) player.closeInventory();
            else if(slot>=19 && slot<19+menu.prices.size()) trade(player,menu.prices.get(slot-19),amount,menu.buying);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Market) event.setCancelled(true);
    }
    private void trade(Player player,Product product,int amount,boolean buying) {
        long now=System.currentTimeMillis();Long last=cooldown.put(player.getUniqueId(),now);
        if(last!=null && now-last<250) return;
        if(player.getGameMode()==GameMode.CREATIVE || player.getGameMode()==GameMode.SPECTATOR) {player.sendMessage("§c생존 또는 모험 모드에서 거래하세요.");return;}
        tycoon.safe(player,()->{
            ItemStack[] before=ItemPlans.copy(player.getInventory().getStorageContents());
            ItemStack[] after=ItemPlans.move(before,new ItemStack(product.material),amount,buying);
            if(after==null)throw new kr.minecraft.survival.economy.domain.StateStore.Denied(buying?"가방 공간이 부족합니다.":"판매할 기본 아이템이 부족합니다.");
            long cents=kr.minecraft.survival.economy.domain.Money.total(kr.minecraft.survival.economy.domain.Money.cents(buying?product.buy:product.sell),amount);
            var cap=buying?null:new kr.minecraft.survival.economy.domain.StateStore.Quota(tycoon.day(),"raw:"+product.material.name(),amount,256);
            tycoon.result(player,tycoon.engine.execute(player.getUniqueId(),buying?"BUY":"SELL","market:"+UUID.randomUUID(),cents,buying?kr.minecraft.survival.economy.domain.ActionEngine.Direction.DEBIT:kr.minecraft.survival.economy.domain.ActionEngine.Direction.CREDIT,product.name+" x "+amount,cap,tycoon.change(player,before,after),()->{}),product.name+" "+amount+"개를 "+(buying?"구매":"판매")+"했습니다. "+Tycoon.money(cents));
        });
    }
    ZoneId rewardZone(){return zone;}
    boolean transfer(UUID id,long cents,boolean credit){
        Economy economy=wallet();if(economy==null)return false;
        org.bukkit.OfflinePlayer player=getServer().getOfflinePlayer(id);double value=kr.minecraft.survival.economy.domain.Money.value(cents);
        if(!credit&&!economy.has(player,value))return false;
        EconomyResponse result=credit?economy.depositPlayer(player,value):economy.withdrawPlayer(player,value);
        return result!=null&&result.transactionSuccess();
    }
    private void reward(Player player){tycoon.daily(player,kr.minecraft.survival.economy.domain.Money.cents(daily));}
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {cooldown.remove(event.getPlayer().getUniqueId());}
}
