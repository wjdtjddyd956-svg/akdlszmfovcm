import java.util.*;
import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minecraft.survival.farming.*;
import kr.minecraft.survival.economy.KoreanEconomyPlugin;
import kr.minecraft.survival.economy.domain.*;
/** RCON-only instrumentation. Never install on a public/production server. */
public final class FarmProbe extends JavaPlugin {
 Object farm; KoreanEconomyPlugin core; FarmStore plots;
 @Override public void onEnable(){if(!getServer().getIp().equals("127.0.0.1")||getServer().getPort()!=25566||getServer().getOnlineMode()){getServer().getPluginManager().disablePlugin(this);return;}try{farm=getServer().getPluginManager().getPlugin("KoreanFarming");core=(KoreanEconomyPlugin)getServer().getPluginManager().getPlugin("KoreanEconomy");plots=(FarmStore)field(farm,"plots");}catch(Exception e){throw new RuntimeException(e);}}
 Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
 FarmStore.Plot plot(Player p,String crop){return plots.all().stream().filter(q->q.owner().equals(p.getUniqueId().toString())&&q.crop().equals(crop)).findFirst().orElseThrow();}
 void render()throws Exception{Object r=field(farm,"renderer");call(r,"all",new Class[0]);}
 @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] a){if(!(sender instanceof RemoteConsoleCommandSender))return true;try{
  if(a[0].equals("fault")){core.getEconomyStateStore().execute(a[1].equals("on")?"CREATE TRIGGER farm_probe_failure BEFORE UPDATE ON receipts WHEN NEW.state='DONE' AND NEW.kind='FARM_HARVEST' BEGIN SELECT RAISE(ABORT,'isolated runtime fault'); END":"DROP TRIGGER IF EXISTS farm_probe_failure");sender.sendMessage("fault="+a[1]);return true;}
  Player p=getServer().getPlayerExact(a[1]);if(p==null)throw new IllegalArgumentException("test player offline");var db=core.getEconomyStateStore();
  switch(a[0]){
   case "ready"->{((Set<UUID>)field(farm,"ready")).add(p.getUniqueId());call(field(farm,"renderer"),"visibility",new Class[]{Player.class},p);}
   case "prepare"->{core.prepareFarmingPlayer(p);db.transaction(()->{db.selectJobInside(p.getUniqueId(),Rules.Job.FARMER,0);return null;});p.setGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.saveData();}
   case "level"->db.grantXp(p.getUniqueId(),Rules.Job.FARMER,Integer.parseInt(a[2]),core.farmingDay(),10000);
   case "give","hand"->{if(a[0].equals("hand"))p.getInventory().clear();ItemStack s=((KoreanFarmingPlugin)farm).previewItem(a[2],p);s.setAmount(Integer.parseInt(a[3]));if(a[0].equals("hand")){p.getInventory().setItem(0,s);p.getInventory().setHeldItemSlot(0);}else if(!p.getInventory().addItem(s).isEmpty())throw new IllegalArgumentException("full");p.saveData();}
   case "clear"->{p.getInventory().clear();p.saveData();}
   case "full"->{for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.STONE,64));p.saveData();}
   case "soil"->{int x=Integer.parseInt(a[2]),y=Integer.parseInt(a[3]),z=Integer.parseInt(a[4]);World w=p.getWorld();for(int xx=x-2;xx<=x+2;xx++)for(int zz=z-2;zz<=z+2;zz++){w.getBlockAt(xx,y-1,zz).setType(Material.STONE);w.getBlockAt(xx,y,zz).setType(Material.DIRT);w.getBlockAt(xx,y+1,zz).setType(Material.AIR);w.getBlockAt(xx,y+2,zz).setType(Material.AIR);w.getBlockAt(xx,y+3,zz).setType(Material.AIR);}w.getBlockAt(x-1,y,z).setType(Material.WATER);Block b=w.getBlockAt(x,y,z);b.setType(Material.FARMLAND);Farmland f=(Farmland)b.getBlockData();f.setMoisture(7);b.setBlockData(f);}
   case "plant"->{Block b=p.getWorld().getBlockAt(Integer.parseInt(a[3]),Integer.parseInt(a[4]),Integer.parseInt(a[5]));call(farm,"plant",new Class[]{Player.class,Block.class,String.class},p,b,a[2]);}
   case "progress"->{var q=plot(p,a[2]);plots.grow(Map.of(q.id(),Long.parseLong(a[3])));render();}
   case "harvest"->{var q=plot(p,a[2]);call(farm,"harvest",new Class[]{Player.class,String.class,boolean.class},p,q.id(),false);}
   case "steal"->{var q=plot(getServer().getPlayerExact(a[2]),a[3]);call(farm,"harvest",new Class[]{Player.class,String.class,boolean.class},p,q.id(),false);}
   case "reclaim"->{var q=plot(p,a[2]);call(farm,"harvest",new Class[]{Player.class,String.class,boolean.class},p,q.id(),true);}
   case "cook"->call(farm,"cook",new Class[]{Player.class,String.class},p,a[2]);
   case "dry"->{var q=plot(p,a[2]);Block b=p.getWorld().getBlockAt(q.x(),q.y()-1,q.z());Farmland f=(Farmland)b.getBlockData();f.setMoisture(Integer.parseInt(a[3]));b.setBlockData(f);}
   case "break"->{var q=plot(p,a[2]);var e=new BlockBreakEvent(p.getWorld().getBlockAt(q.x(),q.y()-1,q.z()),p);getServer().getPluginManager().callEvent(e);sender.sendMessage("cancelled="+e.isCancelled());}
   case "state"->{Map<String,Object> s=new LinkedHashMap<>();s.put("uuid",p.getUniqueId().toString());s.put("ready",((Set<?>)field(farm,"ready")).contains(p.getUniqueId()));s.put("plots",plots.all().stream().filter(q->q.owner().equals(p.getUniqueId().toString())).toList());Map<String,Integer> items=new TreeMap<>();for(var stack:p.getInventory().getStorageContents())if(stack!=null){String id=stack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("koreanfarming","item_id"),PersistentDataType.STRING);if(id!=null)items.merge(id,stack.getAmount(),Integer::sum);}s.put("items",items);s.put("xp",db.progress(p.getUniqueId(),Rules.Job.FARMER).xp());s.put("owed",db.owed(p.getUniqueId()));s.put("day",core.farmingDay());s.put("unresolved",db.unresolved(p.getUniqueId()));s.put("farmHealthy",field(farm,"healthy"));s.put("engineHealthy",core.getEconomyActionEngine().healthy());s.put("sales",db.rows("SELECT * FROM daily_use WHERE uuid=? AND product LIKE 'farm:%'",p.getUniqueId()));s.put("entities",p.getWorld().getEntities().stream().filter(e->e.getPersistentDataContainer().has(new NamespacedKey("koreanfarming","plot_id"))).map(e->e.getType()+":"+e.getLocation().toVector()+":"+(e instanceof ItemDisplay d?d.getItemStack().getItemMeta().getItemModel():"hitbox")).toList());sender.sendMessage(new com.google.gson.Gson().toJson(s));return true;}
   default->throw new IllegalArgumentException("unknown probe action");
  }sender.sendMessage("probe-ok="+a[0]);
 }catch(Exception e){Throwable cause=e instanceof InvocationTargetException?e.getCause():e;sender.sendMessage("probe-denied="+cause);}return true;}
}
