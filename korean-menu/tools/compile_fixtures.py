"""Minimal Bukkit signature fixtures for offline compilation and event tests.

These are not a server and are NEVER packaged in the plugin JAR.
Set BUKKIT_API_JAR for a separate build against a real API JAR.
"""
from pathlib import Path

SOURCES = {
    'org/bukkit/Material.java': 'public enum Material {GRAY_STAINED_GLASS_PANE,OAK_DOOR,LIME_CONCRETE,RED_CONCRETE,BOOK,GOLD_INGOT,EMERALD,CHEST,GOLDEN_SHOVEL,RED_BED,COMPASS,WRITABLE_BOOK,BARRIER}',
    'org/bukkit/Server.java': '''import java.util.Collection; import org.bukkit.entity.Player; import org.bukkit.inventory.*; import org.bukkit.plugin.PluginManager; import org.bukkit.scheduler.BukkitScheduler;
public interface Server { PluginManager getPluginManager(); BukkitScheduler getScheduler(); Inventory createInventory(InventoryHolder h,int size,String title); Collection<? extends Player> getOnlinePlayers(); }''',
    'org/bukkit/command/Command.java': 'public class Command {}',
    'org/bukkit/command/CommandSender.java': 'public interface CommandSender {void sendMessage(String message); boolean hasPermission(String permission);}',
    'org/bukkit/entity/HumanEntity.java': '''import org.bukkit.command.CommandSender; import org.bukkit.inventory.*;
public interface HumanEntity extends CommandSender {InventoryView getOpenInventory(); InventoryView openInventory(Inventory inventory); void closeInventory();}''',
    'org/bukkit/entity/Player.java': '''import java.util.UUID;
public interface Player extends HumanEntity { UUID getUniqueId(); boolean isSneaking(); boolean isOnline(); boolean performCommand(String command); String getName(); }''',
    'org/bukkit/plugin/Plugin.java': 'public interface Plugin {}',
    'org/bukkit/plugin/PluginManager.java': '''import org.bukkit.event.Listener;
public interface PluginManager {void registerEvents(Listener listener,Plugin plugin); boolean isPluginEnabled(String name);}''',
    'org/bukkit/plugin/java/JavaPlugin.java': '''import java.util.logging.Logger; import org.bukkit.Server; import org.bukkit.plugin.Plugin; import org.bukkit.command.*;
public class JavaPlugin implements Plugin {public void onEnable(){} public void onDisable(){} public Server getServer(){return null;} public Logger getLogger(){return Logger.getGlobal();} public boolean onCommand(CommandSender s,Command c,String l,String[] a){return false;}}''',
    'org/bukkit/scheduler/BukkitTask.java': 'public interface BukkitTask {}',
    'org/bukkit/scheduler/BukkitScheduler.java': '''import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {BukkitTask runTask(Plugin plugin,Runnable task);}''',
    'org/bukkit/event/Listener.java': 'public interface Listener {}',
    'org/bukkit/event/EventPriority.java': 'public enum EventPriority {LOWEST,LOW,NORMAL,HIGH,HIGHEST,MONITOR}',
    'org/bukkit/event/EventHandler.java': '''import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface EventHandler {EventPriority priority() default EventPriority.NORMAL; boolean ignoreCancelled() default false;}''',
    'org/bukkit/inventory/InventoryHolder.java': 'public interface InventoryHolder {Inventory getInventory();}',
    'org/bukkit/inventory/Inventory.java': 'public interface Inventory {InventoryHolder getHolder(); void setItem(int slot,ItemStack item);}',
    'org/bukkit/inventory/InventoryView.java': '''public interface InventoryView {Inventory getTopInventory();}''',
    'org/bukkit/inventory/ItemStack.java': '''import org.bukkit.Material; import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {public ItemStack(Material m){} public ItemMeta getItemMeta(){return new ItemMeta() {public void setDisplayName(String n){} public void setLore(java.util.List<String> l){}};} public boolean setItemMeta(ItemMeta m){return true;}}''',
    'org/bukkit/inventory/meta/ItemMeta.java': '''import java.util.List;
public interface ItemMeta {void setDisplayName(String name); void setLore(List<String> lore);}''',
    'org/bukkit/event/player/PlayerSwapHandItemsEvent.java': '''import org.bukkit.entity.Player;
public class PlayerSwapHandItemsEvent {private final Player player; public boolean cancelled; public PlayerSwapHandItemsEvent(Player p){player=p;} public Player getPlayer(){return player;} public void setCancelled(boolean c){cancelled=c;}}''',
    'org/bukkit/event/player/PlayerQuitEvent.java': '''import org.bukkit.entity.Player;
public class PlayerQuitEvent {private final Player player; public PlayerQuitEvent(Player p){player=p;} public Player getPlayer(){return player;}}''',
    'org/bukkit/event/inventory/InventoryClickEvent.java': '''import org.bukkit.inventory.InventoryView; import org.bukkit.entity.HumanEntity;
public class InventoryClickEvent {public boolean cancelled; private final InventoryView view; private final HumanEntity who; private final int slot; public InventoryClickEvent(InventoryView v,HumanEntity p,int s){view=v;who=p;slot=s;} public InventoryView getView(){return view;} public HumanEntity getWhoClicked(){return who;} public int getRawSlot(){return slot;} public void setCancelled(boolean c){cancelled=c;}}''',
    'org/bukkit/event/inventory/InventoryDragEvent.java': '''import org.bukkit.inventory.InventoryView;
public class InventoryDragEvent {public boolean cancelled; private final InventoryView view; public InventoryDragEvent(InventoryView v){view=v;} public InventoryView getView(){return view;} public void setCancelled(boolean c){cancelled=c;}}''',
}

def write(root):
    for name, source in SOURCES.items():
        path = Path(root) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        package = name.rsplit('/', 1)[0].replace('/', '.')
        path.write_text('package ' + package + ';\n' + source + '\n')

if __name__ == '__main__':
    import sys
    write(sys.argv[1])
