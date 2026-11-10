package kr.minecraft.survival.economy;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.event.inventory.*;
import net.milkbowl.vault.economy.*;

public final class EconomyTest {
    static int checks;
    static void ok(boolean x,String reason){checks++;if(!x)throw new AssertionError(reason);}
    static class Storage implements PlayerInventory {
        ItemStack[] contents=new ItemStack[36];
        public ItemStack[] getStorageContents(){return contents;}
        public void setStorageContents(ItemStack[] c){contents=c;}
        public InventoryHolder getHolder(){return null;}
        public void setItem(int slot,ItemStack item){contents[slot]=item;}
    }
    static class User implements Player {
        UUID uuid=UUID.randomUUID();Storage storage=new Storage();
        boolean admin=false;GameMode mode=GameMode.SURVIVAL;
        InventoryView view=new InventoryView(){public Inventory getTopInventory(){return storage;}};
        List<String> messages=new ArrayList<>();
        public UUID getUniqueId(){return uuid;}
        public String getName(){return "시험사용자";}
        public boolean isSneaking(){return false;}
        public boolean isOnline(){return true;}
        public boolean hasPermission(String p){return !p.equals("koreaneconomy.admin")||admin;}
        public GameMode getGameMode(){return mode;}
        public boolean performCommand(String c){return true;}
        public void sendMessage(String s){messages.add(s);}
        public InventoryView getOpenInventory(){return view;}
        public InventoryView openInventory(Inventory inv){view=new InventoryView(){public Inventory getTopInventory(){return inv;}};return view;}
        public void closeInventory(){view=new InventoryView(){public Inventory getTopInventory(){return storage;}};}
        public PlayerInventory getInventory(){return storage;}
    }
    static class Plugin extends KoreanEconomyPlugin {
        final Path data;final Queue<Runnable> tasks=new ArrayDeque<>();
        final org.bukkit.configuration.file.FileConfiguration config=new org.bukkit.configuration.file.FileConfiguration();
        double balance=0;boolean reject=false;final User user=new User();
        final Economy economy=(Economy)Proxy.newProxyInstance(Economy.class.getClassLoader(),new Class<?>[]{Economy.class},(p,m,a)->{
            if(m.getName().equals("has"))return balance>=(double)a[1];
            if(m.getName().equals("depositPlayer")||m.getName().equals("withdrawPlayer")){
                double amount=(double)a[1];boolean deposit=m.getName().equals("depositPlayer");
                if(reject||(!deposit&&balance<amount))return new EconomyResponse(0,balance,EconomyResponse.ResponseType.FAILURE,"실패");
                balance+=deposit?amount:-amount;return new EconomyResponse(amount,balance,EconomyResponse.ResponseType.SUCCESS,"");
            }
            if(m.getReturnType()==boolean.class)return true;
            if(m.getReturnType()==double.class)return balance;
            return null;
        });
        Plugin(Path path){data=path;}
        public java.io.File getDataFolder(){return data.toFile();}
        public org.bukkit.configuration.file.FileConfiguration getConfig(){return config;}
        final Server server=new Server(){
            public PluginManager getPluginManager(){return new PluginManager(){public void registerEvents(org.bukkit.event.Listener l,org.bukkit.plugin.Plugin p){}public boolean isPluginEnabled(String name){return true;}};}
            public BukkitScheduler getScheduler(){return (p,t)->{tasks.add(t);return null;};}
            public Inventory createInventory(InventoryHolder h,int size,String title){return new Inventory(){public InventoryHolder getHolder(){return h;}public void setItem(int s,ItemStack i){}};}
            public Collection<? extends Player> getOnlinePlayers(){return List.of(user);}
            public ServicesManager getServicesManager(){return new ServicesManager(){public <T> RegisteredServiceProvider<T> getRegistration(Class<T> c){return new RegisteredServiceProvider<>(c.cast(economy));}};}
        };
        public Server getServer(){return server;}
        void tick(){while(!tasks.isEmpty())tasks.remove().run();}
        void open(){onCommand(user,new Command("상점"),"상점",new String[0]);}
        InventoryClickEvent click(int slot){InventoryClickEvent e=new InventoryClickEvent(user.view,user,slot);onClick(e);return e;}
    }
    public static void main(String[] args)throws Exception {
        ItemStack[] items={new ItemStack(Material.WHEAT,60),null};
        ItemStack[] added=InventoryPlan.plan(items,Material.WHEAT,16,true);
        ok(items[0].getAmount()==60&&items[1]==null,"plan never mutates original");
        ok(added[0].getAmount()==64&&added[1].getAmount()==12,"fill existing stack and empty slot");
        ok(InventoryPlan.plan(new ItemStack[]{new ItemStack(Material.WHEAT,64)},Material.WHEAT,1,true)==null,"full bag cannot buy");
        ok(InventoryPlan.plan(items,Material.WHEAT,64,false)==null,"insufficient amount cannot sell");
        ItemStack named=new ItemStack(Material.WHEAT,64);named.setItemMeta(named.getItemMeta());
        ok(InventoryPlan.plan(new ItemStack[]{named},Material.WHEAT,1,false)==null,"custom items excluded");
        ItemStack[] emptied=InventoryPlan.plan(new ItemStack[]{new ItemStack(Material.WHEAT,16)},Material.WHEAT,16,false);
        ok(emptied[0]==null,"selling exact amount clears stack");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,0.5,1_000_001}){
            try{KoreanEconomyPlugin.validPrice(bad);throw new AssertionError("invalid price accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        Path root=Files.createTempDirectory("korean-economy-test-");
        AtomicInteger payouts=new AtomicInteger();UUID uuid=UUID.randomUUID();LocalDate today=LocalDate.of(2026,11,10);
        DailyClaims daily=new DailyClaims(root.resolve("daily"));
        ok(daily.claim(uuid,today,()->{payouts.incrementAndGet();return true;})==DailyClaims.Result.PAID,"first reward paid");
        ok(new DailyClaims(root.resolve("daily")).claim(uuid,today,()->{payouts.incrementAndGet();return true;})==DailyClaims.Result.ALREADY,"restart cannot duplicate reward");
        ok(payouts.get()==1,"one payout only");
        ok(daily.claim(uuid,today.plusDays(1),()->true)==DailyClaims.Result.PAID,"next calendar day can claim");
        UUID retry=UUID.randomUUID();ok(daily.claim(retry,today,()->false)==DailyClaims.Result.FAILED,"rejected deposit recorded failed");
        ok(daily.claim(retry,today,()->true)==DailyClaims.Result.PAID,"failed reward can retry");
        UUID crash=UUID.randomUUID();try{daily.claim(crash,today,()->{throw new IllegalStateException("simulated provider crash");});}catch(IllegalStateException expected){}
        ok(new DailyClaims(root.resolve("daily")).claim(crash,today,()->true)==DailyClaims.Result.REVIEW,"crash receipt prevents duplicate");
        ok(daily.claim(crash,today.plusDays(1),()->true)==DailyClaims.Result.REVIEW,"unresolved receipt not overwritten next day");
        Plugin p=new Plugin(root.resolve("plugin"));p.onEnable();p.user.storage.contents[0]=new ItemStack(Material.WHEAT,2);p.open();
        ok(p.click(19).cancelled,"market click blocked");p.tick();
        ok(p.balance==2&&p.user.storage.contents[0].getAmount()==1,"sale credits correct amount and removes one");
        ok(p.click(60).cancelled,"bottom shift clicks blocked");p.tick();
        InventoryDragEvent drag=new InventoryDragEvent(p.user.view);p.onDrag(drag);ok(drag.cancelled,"market drag blocked");
        p.click(45);p.tick();ok(((KoreanEconomyPlugin.Market)p.user.view.getTopInventory().getHolder()).buying,"buy mode switch");
        // Use a fresh account to avoid the deliberate trade cooldown.
        Plugin insufficient=new Plugin(root.resolve("insufficient"));insufficient.onEnable();insufficient.open();insufficient.click(45);insufficient.tick();insufficient.click(19);insufficient.tick();
        ok(insufficient.balance==0&&insufficient.user.storage.contents[0]==null,"failed withdrawal does not grant item");
        Plugin buyer=new Plugin(root.resolve("buyer"));buyer.onEnable();buyer.balance=20;buyer.open();buyer.click(45);buyer.tick();buyer.click(19);buyer.tick();
        ok(buyer.balance==10&&buyer.user.storage.contents[0].getType()==Material.WHEAT&&buyer.user.storage.contents[0].getAmount()==1,"buy debits money and adds the requested item");
        Plugin full=new Plugin(root.resolve("full"));full.onEnable();full.balance=100;
        Arrays.fill(full.user.storage.contents,new ItemStack(Material.DIAMOND,64));full.open();full.click(45);full.tick();full.click(19);full.tick();
        ok(full.balance==100,"full inventory never charged");
        Plugin stale=new Plugin(root.resolve("stale"));stale.onEnable();stale.open();stale.click(19);stale.user.closeInventory();stale.tick();ok(stale.balance==0,"queued stale click ignored");
        Plugin declined=new Plugin(root.resolve("declined"));declined.onEnable();declined.reject=true;declined.user.storage.contents[0]=new ItemStack(Material.WHEAT,3);declined.open();declined.click(19);declined.tick();
        ok(declined.balance==0&&declined.user.storage.contents[0].getAmount()==3,"rejected deposit preserves items");
        Plugin creative=new Plugin(root.resolve("creative"));creative.onEnable();creative.user.mode=GameMode.CREATIVE;creative.user.storage.contents[0]=new ItemStack(Material.WHEAT,64);creative.open();creative.click(19);creative.tick();
        ok(creative.balance==0&&creative.user.storage.contents[0].getAmount()==64,"creative items cannot mint currency");
        Plugin reload=new Plugin(root.resolve("reload"));reload.onEnable();reload.open();reload.user.admin=true;reload.config.set("상점.밀.구매가",100.0);reload.click(19);
        reload.onCommand(reload.user,new Command("경제설정"),"경제설정",new String[]{"새로고침"});reload.tick();
        ok(reload.user.view.getTopInventory().getHolder()==null&&reload.balance==0,"price reload closes old offers and cancels queued trades");
        reload.open();reload.config.set("상점.밀.판매가",200.0);reload.onCommand(reload.user,new Command("경제설정"),"경제설정",new String[]{"새로고침"});
        ok(((KoreanEconomyPlugin.Market)reload.user.view.getTopInventory().getHolder()).prices.get(0).buy()==100.0,"invalid price reload retains previous settings");
        System.out.println("PASS: "+checks+" economy assertions (real Vault API, offline Bukkit fixtures)");
    }
}
