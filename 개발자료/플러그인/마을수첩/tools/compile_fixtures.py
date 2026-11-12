"""Minimal Bukkit signature fixtures for offline compilation and event tests.

These are not a server and are NEVER packaged in the plugin JAR.
Set BUKKIT_API_JAR for a separate build against a real API JAR.
"""
from pathlib import Path

SOURCES = {
    'org/bukkit/Material.java': 'public enum Material {GRAY_STAINED_GLASS_PANE,OAK_DOOR,LIME_CONCRETE,RED_CONCRETE,BOOK,GOLD_INGOT,EMERALD,CHEST,GOLDEN_SHOVEL,GOLDEN_HOE,PAPER,RED_BED,COMPASS,WRITABLE_BOOK,BARRIER,SUNFLOWER,ENDER_CHEST,WHEAT,CARROT,POTATO,COBBLESTONE,OAK_LOG,COAL,IRON_INGOT,DIAMOND,AIR; public int getMaxStackSize(){return 64;}}',
    'org/bukkit/Server.java': '''import java.util.Collection; import org.bukkit.entity.Player; import org.bukkit.inventory.*; import org.bukkit.plugin.PluginManager; import org.bukkit.scheduler.BukkitScheduler;
public interface Server { PluginManager getPluginManager(); BukkitScheduler getScheduler(); Inventory createInventory(InventoryHolder h,int size,String title); Collection<? extends Player> getOnlinePlayers(); default org.bukkit.plugin.ServicesManager getServicesManager(){return null;} }''',
    'org/bukkit/command/Command.java': 'public class Command {private final String name; public Command(){this("");} public Command(String name){this.name=name;} public String getName(){return name;}}',
    'org/bukkit/command/CommandSender.java': 'public interface CommandSender {void sendMessage(String message); boolean hasPermission(String permission);}',
    'org/bukkit/entity/HumanEntity.java': '''import org.bukkit.command.CommandSender; import org.bukkit.inventory.*;
public interface HumanEntity extends CommandSender {InventoryView getOpenInventory(); InventoryView openInventory(Inventory inventory); void closeInventory();}''',
    'org/bukkit/entity/Player.java': '''import java.util.UUID;
public interface Player extends HumanEntity, org.bukkit.OfflinePlayer { UUID getUniqueId(); boolean isSneaking(); boolean isOnline(); boolean performCommand(String command); String getName(); default org.bukkit.inventory.PlayerInventory getInventory(){return null;} default org.bukkit.GameMode getGameMode(){return org.bukkit.GameMode.SURVIVAL;} }''',
    'org/bukkit/OfflinePlayer.java': 'public interface OfflinePlayer {java.util.UUID getUniqueId(); String getName();}',
    'org/bukkit/GameMode.java': 'public enum GameMode {SURVIVAL,CREATIVE,ADVENTURE,SPECTATOR}',
    'org/bukkit/plugin/ServicesManager.java': 'public interface ServicesManager {<T> RegisteredServiceProvider<T> getRegistration(Class<T> service);}',
    'org/bukkit/plugin/RegisteredServiceProvider.java': 'public class RegisteredServiceProvider<T> {private final T provider; public RegisteredServiceProvider(T provider){this.provider=provider;} public T getProvider(){return provider;}}',
    'org/bukkit/configuration/file/FileConfiguration.java': '''public class FileConfiguration {private final java.util.Map<String,Object> values=new java.util.HashMap<>(); public void set(String key,Object value){values.put(key,value);} public double getDouble(String key,double fallback){Object v=values.get(key); return v instanceof Number n?n.doubleValue():fallback;} public String getString(String key,String fallback){Object v=values.get(key);return v instanceof String s?s:fallback;}}''',
    'org/bukkit/plugin/Plugin.java': 'public interface Plugin {}',
    'org/bukkit/plugin/PluginManager.java': '''import org.bukkit.event.Listener;
public interface PluginManager {void registerEvents(Listener listener,Plugin plugin); boolean isPluginEnabled(String name);}''',
    'org/bukkit/plugin/java/JavaPlugin.java': '''import java.util.logging.Logger; import org.bukkit.Server; import org.bukkit.plugin.Plugin; import org.bukkit.command.*;
public class JavaPlugin implements Plugin {public void onEnable(){} public void onDisable(){} public Server getServer(){return null;} public Logger getLogger(){return Logger.getGlobal();} public boolean onCommand(CommandSender s,Command c,String l,String[] a){return false;} public java.io.File getDataFolder(){return new java.io.File("build/test-data");} public void saveDefaultConfig(){} public void reloadConfig(){} public org.bukkit.configuration.file.FileConfiguration getConfig(){return new org.bukkit.configuration.file.FileConfiguration();}}''',
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
public class ItemStack implements Cloneable {private Material type; private int amount; private ItemMeta meta; public ItemStack(Material m){this(m,1);} public ItemStack(Material m,int n){type=m;amount=n;} public Material getType(){return type;} public int getAmount(){return amount;} public void setAmount(int n){amount=n;} public int getMaxStackSize(){return type.getMaxStackSize();} public boolean isSimilar(ItemStack s){return s!=null && type==s.type && java.util.Objects.equals(meta,s.meta);} public ItemStack clone(){ItemStack s=new ItemStack(type,amount);s.meta=meta;return s;} public ItemMeta getItemMeta(){return meta==null?new ItemMeta() {public void setDisplayName(String n){} public void setLore(java.util.List<String> l){}}:meta;} public boolean setItemMeta(ItemMeta m){meta=m;return true;}}''',
    'org/bukkit/inventory/PlayerInventory.java': 'public interface PlayerInventory extends Inventory {ItemStack[] getStorageContents(); void setStorageContents(ItemStack[] contents);}',
    'org/bukkit/inventory/meta/ItemMeta.java': '''import java.util.List;
public interface ItemMeta {void setDisplayName(String name); void setLore(List<String> lore);}''',
    'org/bukkit/event/player/PlayerSwapHandItemsEvent.java': '''import org.bukkit.entity.Player;
public class PlayerSwapHandItemsEvent {private final Player player; public boolean cancelled; public PlayerSwapHandItemsEvent(Player p){player=p;} public Player getPlayer(){return player;} public void setCancelled(boolean c){cancelled=c;}}''',
    'org/bukkit/event/player/PlayerQuitEvent.java': '''import org.bukkit.entity.Player;
public class PlayerQuitEvent {private final Player player; public PlayerQuitEvent(Player p){player=p;} public Player getPlayer(){return player;}}''',
    'org/bukkit/event/inventory/InventoryClickEvent.java': '''import org.bukkit.inventory.InventoryView; import org.bukkit.entity.HumanEntity;
public class InventoryClickEvent {public boolean cancelled,right,shift; private final InventoryView view; private final HumanEntity who; private final int slot; public InventoryClickEvent(InventoryView v,HumanEntity p,int s){view=v;who=p;slot=s;} public InventoryView getView(){return view;} public HumanEntity getWhoClicked(){return who;} public int getRawSlot(){return slot;} public void setCancelled(boolean c){cancelled=c;} public boolean isRightClick(){return right;} public boolean isShiftClick(){return shift;}}''',
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
