package kr.minecraft.survival.menu;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/** Behavioral tests using local signature fixtures, not an actual Paper server. */
public final class MenuEventTest {
    static int assertions;
    static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    static class Inv implements Inventory {
        final InventoryHolder holder;
        final Map<Integer, ItemStack> contents = new HashMap<>();
        Inv(InventoryHolder h) { holder=h; }
        public InventoryHolder getHolder(){return holder;}
        public void setItem(int slot, ItemStack item){contents.put(slot,item);}
    }
    static InventoryView view(Inventory inv) { return new InventoryView(){public Inventory getTopInventory(){return inv;}}; }
    static class User implements Player {
        final UUID id = UUID.randomUUID();
        boolean sneaking=true, online=true, allowed=true;
        InventoryView current=view(new Inv(null));
        List<String> commands=new ArrayList<>(), messages=new ArrayList<>();
        public UUID getUniqueId(){return id;}
        public String getName(){return "Steve";}
        public boolean isSneaking(){return sneaking;}
        public boolean isOnline(){return online;}
        public boolean hasPermission(String p){return allowed;}
        public void sendMessage(String s){messages.add(s);}
        public boolean performCommand(String c){commands.add(c);return true;}
        public InventoryView getOpenInventory(){return current;}
        public InventoryView openInventory(Inventory i){current=view(i);return current;}
        public void closeInventory(){current=view(new Inv(null));}
    }
    static class World implements Server {
        Queue<Runnable> tasks=new ArrayDeque<>();
        List<User> users=new ArrayList<>();
        Set<String> dependencies=new HashSet<>(List.of("Essentials","EssentialsSpawn","QuickShop-Hikari","KoreanEconomy","KoreanCommands"));
        public PluginManager getPluginManager(){return new PluginManager(){
            public void registerEvents(Listener l,Plugin p){}
            public boolean isPluginEnabled(String n){return dependencies.contains(n);}
        };}
        public BukkitScheduler getScheduler(){return (p,t)->{tasks.add(t);return null;};}
        public Inventory createInventory(InventoryHolder h,int s,String title){check(s==54,"six rows");return new Inv(h);}
        public Collection<? extends Player> getOnlinePlayers(){return users;}
        void tick(){while(!tasks.isEmpty()) tasks.remove().run();}
    }
    static class TestPlugin extends KoreanMenuPlugin {
        World world=new World();
        public Server getServer(){return world;}
        User user(){User u=new User();world.users.add(u);return u;}
        User open(){User u=user();onSwap(new PlayerSwapHandItemsEvent(u));world.tick();return u;}
        InventoryClickEvent click(User u,int slot){InventoryClickEvent e=new InventoryClickEvent(u.current,u,slot);onClick(e);return e;}
    }
    public static void main(String[] args) {
        TestPlugin p=new TestPlugin();
        User normal=p.user(); normal.sneaking=false;
        PlayerSwapHandItemsEvent f=new PlayerSwapHandItemsEvent(normal);p.onSwap(f);
        check(!f.cancelled && p.world.tasks.isEmpty(),"ordinary F unchanged");
        User denied=p.user();denied.allowed=false;
        PlayerSwapHandItemsEvent d=new PlayerSwapHandItemsEvent(denied);p.onSwap(d);
        check(!d.cancelled && p.world.tasks.isEmpty(),"no permission cannot open");
        User u=p.open();
        check(u.current.getTopInventory().getHolder() instanceof KoreanMenuPlugin.Menu,"shift F opens owned menu");
        check(((Inv)u.current.getTopInventory()).contents.size()==54,"all slots filled");
        PlayerSwapHandItemsEvent repeated=new PlayerSwapHandItemsEvent(u);p.onSwap(repeated);
        check(repeated.cancelled && p.world.tasks.isEmpty(),"cooldown still prevents hand swap");
        check(p.click(u,60).cancelled,"bottom inventory clicks blocked");
        check(p.click(u,-999).cancelled,"outside click blocked");
        check(p.click(u,0).cancelled && u.commands.isEmpty(),"decorative item cannot trigger commands");
        InventoryDragEvent drag=new InventoryDragEvent(u.current);p.onDrag(drag);check(drag.cancelled,"drag blocked");
        User stranger=p.user();
        InventoryClickEvent forged=new InventoryClickEvent(u.current,stranger,10);p.onClick(forged);p.world.tick();
        check(forged.cancelled && stranger.commands.isEmpty(),"different owner cannot act");
        InventoryClickEvent unrelated=new InventoryClickEvent(normal.current,normal,1);p.onClick(unrelated);
        check(!unrelated.cancelled,"other inventory untouched");
        p.click(u,10);check(u.commands.isEmpty(),"command deferred until next tick");p.world.tick();
        check(u.commands.equals(List.of("essentials:balance")),"balance dispatched as player");
        User market=p.open();p.click(market,14);p.world.tick();check(market.commands.equals(List.of("상점")),"server market is actionable");
        User daily=p.open();p.click(daily,22);p.world.tick();check(daily.commands.equals(List.of("일일보상")),"daily reward button dispatches as player");
        User save=p.open();p.click(save,28);p.world.tick();
        check(save.commands.isEmpty(),"save first opens confirmation");
        check(((KoreanMenuPlugin.Menu)save.current.getTopInventory().getHolder()).confirmation,"confirmation inventory");
        p.click(save,30);p.world.tick();check(save.commands.equals(List.of("essentials:sethome default")),"confirm saves named home");
        User cancel=p.open();p.click(cancel,28);p.world.tick();p.click(cancel,32);p.world.tick();
        check(cancel.commands.isEmpty(),"cancel never saves home");
        User revoked=p.open();p.click(revoked,30);revoked.allowed=false;p.world.tick();
        check(revoked.commands.isEmpty(),"permission checked again before command");
        User missing=p.open();p.world.dependencies.remove("Essentials");p.click(missing,10);p.world.tick();
        check(missing.commands.isEmpty() && !missing.messages.isEmpty(),"missing plugin sends help");
        User offline=p.user();p.onSwap(new PlayerSwapHandItemsEvent(offline));offline.online=false;p.world.tick();
        check(offline.current.getTopInventory().getHolder()==null,"logout cancels scheduled open");
        User closing=p.open();p.onDisable();check(closing.current.getTopInventory().getHolder()==null,"disable closes menu");
        System.out.println("PASS: " + assertions + " menu event assertions (offline fixtures)");
    }
}
