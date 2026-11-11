import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

/** Test-copy instrumentation. Not included in the installation ZIP or production JAR. */
public final class CodexProbe extends JavaPlugin {
    @Override public void onEnable(){if(!getServer().getIp().equals("127.0.0.1")||getServer().getPort()!=25566||getServer().getOnlineMode())getServer().getPluginManager().disablePlugin(this);}
    static final Attribute[] ATTRS={Attribute.MOVEMENT_SPEED,Attribute.BLOCK_BREAK_SPEED,Attribute.MAX_HEALTH,Attribute.ATTACK_DAMAGE,Attribute.ARMOR,Attribute.ATTACK_SPEED,Attribute.KNOCKBACK_RESISTANCE,Attribute.LUCK};
    static final String[] NAMES={"MOVE","MINING","HEALTH","DAMAGE","ARMOR","ATTACK_SPEED","KNOCKBACK","LUCK"};
    Object store()throws Exception{var codex=getServer().getPluginManager().getPlugin("KoreanCodex");var f=codex.getClass().getDeclaredField("store");f.setAccessible(true);return f.get(codex);}
    void sql(String text)throws Exception{Object s=store();var method=s.getClass().getDeclaredMethod("execute",String.class,Object[].class);method.setAccessible(true);method.invoke(s,text,new Object[0]);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof RemoteConsoleCommandSender))return true;
        try{
            if(args[0].equals("fail")){sql(args[1].equals("on")?"CREATE TRIGGER fail_runtime_finish BEFORE UPDATE ON registrations WHEN NEW.state='DONE' BEGIN SELECT RAISE(ABORT,'runtime isolated fault'); END":"DROP TRIGGER IF EXISTS fail_runtime_finish");sender.sendMessage("fault="+args[1]);return true;}
            Player p=getServer().getPlayerExact(args[1]);if(p==null)throw new IllegalArgumentException("test player offline");
            if(args[0].equals("external")){
                var speed=p.getAttribute(Attribute.MOVEMENT_SPEED);var key=new NamespacedKey("probe","other_speed");if(speed.getModifier(key)==null)speed.addModifier(new AttributeModifier(key,0.1,AttributeModifier.Operation.ADD_SCALAR));
                var hp=p.getAttribute(Attribute.MAX_HEALTH);var hk=new NamespacedKey("probe","other_health");if(hp.getModifier(hk)==null)hp.addModifier(new AttributeModifier(hk,1,AttributeModifier.Operation.ADD_NUMBER));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,24000,0));sender.sendMessage("external=ready");
            }else if(args[0].equals("injure")){
                for(String rule:p.getWorld().getGameRules())if(rule.toLowerCase(Locale.ROOT).contains("regeneration"))p.getWorld().setGameRuleValue(rule,"false");p.setHealth(11);sender.sendMessage("injured="+p.getHealth());
            }else if(args[0].equals("orphan")){
                p.getPersistentDataContainer().set(new NamespacedKey("koreancodex","pending_registration"),PersistentDataType.STRING,UUID.randomUUID()+":CONSUMED");p.saveData();sender.sendMessage("orphan=saved");
            }else if(args[0].equals("prepare")){
                p.getInventory().setStorageContents(new org.bukkit.inventory.ItemStack[36]);p.getInventory().setItem(0,new org.bukkit.inventory.ItemStack(Material.valueOf(args[2].toUpperCase(Locale.ROOT)),Integer.parseInt(args[3])));sender.sendMessage("prepared="+args[2]);
            }else if(args[0].equals("special")){
                String kind=args[2];p.getInventory().setStorageContents(new org.bukkit.inventory.ItemStack[36]);org.bukkit.inventory.ItemStack item=new org.bukkit.inventory.ItemStack(kind.equals("enchanted")||kind.equals("damaged")?Material.ELYTRA:Material.DIAMOND);
                var meta=item.getItemMeta();switch(kind){case "named","mixed"->meta.setDisplayName("보존할 기념품");case "enchanted"->meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,1,false);case "damaged"->((org.bukkit.inventory.meta.Damageable)meta).setDamage(1);case "data"->meta.getPersistentDataContainer().set(new NamespacedKey("probe","custom"),PersistentDataType.INTEGER,1);case "model"->meta.setItemModel(new NamespacedKey("probe","model"));default->throw new IllegalArgumentException("special kind");}
                item.setItemMeta(meta);p.getInventory().setItem(0,item);if(kind.equals("mixed"))p.getInventory().setItem(1,new org.bukkit.inventory.ItemStack(Material.DIAMOND,3));sender.sendMessage("special="+kind);
            }else if(args[0].equals("attrs")){
                StringJoiner out=new StringJoiner(" ");out.add("hp="+p.getHealth());out.add("speed_potion="+p.hasPotionEffect(PotionEffectType.SPEED));
                for(int i=0;i<ATTRS.length;i++){var attribute=p.getAttribute(ATTRS[i]);if(attribute==null){out.add(NAMES[i]+"=missing");continue;}out.add(NAMES[i]+"="+attribute.getValue());out.add("base_"+NAMES[i]+"="+attribute.getBaseValue());long own=attribute.getModifiers().stream().filter(m->m.getKey().namespace().equals("koreancodex")).count();out.add("own_"+NAMES[i]+"="+own);var modifier=attribute.getModifier(new NamespacedKey("koreancodex","bonus_"+NAMES[i].toLowerCase(Locale.ROOT)));out.add("amount_"+NAMES[i]+"="+(modifier==null?0:modifier.getAmount()));}
                sender.sendMessage(out.toString());
            }
        }catch(Exception e){sender.sendMessage("probe-error="+e);getLogger().log(java.util.logging.Level.WARNING,"isolated test probe",e);}return true;
    }
}
