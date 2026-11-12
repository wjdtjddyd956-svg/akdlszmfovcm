package kr.minecraft.survival.farming;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Two nonpersistent entities per loaded plot; rejected packs never see missing models. */
final class FarmRenderer implements Listener {
    record Pair(ItemDisplay display,Interaction hitbox,int stage){}
    final KoreanFarmingPlugin p;final NamespacedKey entityKey;final Map<String,Pair> entities=new HashMap<>();
    FarmRenderer(KoreanFarmingPlugin p){this.p=p;entityKey=new NamespacedKey(p,"plot_id");}
    boolean isFarmEntity(Entity e){return e.getPersistentDataContainer().has(entityKey,PersistentDataType.STRING);}
    String plot(Entity e){return e.getPersistentDataContainer().get(entityKey,PersistentDataType.STRING);}
    void remove(String id){Pair pair=entities.remove(id);if(pair!=null){pair.display.remove();pair.hitbox.remove();}}
    void sync(FarmStore.Plot plot){World w=p.getServer().getWorld(UUID.fromString(plot.world()));if(w==null||!w.isChunkLoaded(plot.x()>>4,plot.z()>>4)){remove(plot.id());return;}
        int stage=FarmRules.crop(plot.crop()).stage(plot.progress());Pair old=entities.get(plot.id());if(old!=null&&old.display.isValid()&&old.hitbox.isValid()&&stage==old.stage)return;remove(plot.id());
        var crop=FarmRules.crop(plot.crop());Location base=p.location(plot);ItemStack model=new ItemStack(Material.PAPER);var meta=model.getItemMeta();meta.setItemModel(NamespacedKey.fromString("tycoon:crop/"+crop.id()+"_stage_"+stage));model.setItemMeta(meta);
        ItemDisplay display=w.spawn(base.clone().add(.5,.5,.5),ItemDisplay.class,e->{init(e,plot.id());e.setItemStack(model);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);e.setViewRange(3);e.setShadowRadius(0);e.setDisplayWidth(1);e.setDisplayHeight(crop.height());});
        Interaction hit=w.spawn(base.clone().add(.5,0,.5),Interaction.class,e->{init(e,plot.id());e.setInteractionWidth(.8f);e.setInteractionHeight(crop.height());e.setResponsive(true);});
        entities.put(plot.id(),new Pair(display,hit,stage));for(Player player:w.getPlayers())visibility(player,display,hit);
    }
    void init(Entity e,String id){e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setVisibleByDefault(false);e.getPersistentDataContainer().set(entityKey,PersistentDataType.STRING,id);}
    void visibility(Player player,Entity... es){for(Entity e:es)if(p.ready.contains(player.getUniqueId()))player.showEntity(p,e);else player.hideEntity(p,e);}
    void visibility(Player player){for(Pair pair:entities.values())visibility(player,pair.display,pair.hitbox);}
    void all(){Set<String> known=new HashSet<>();for(var plot:p.plots.all()){known.add(plot.id());sync(plot);}for(String id:new ArrayList<>(entities.keySet()))if(!known.contains(id))remove(id);}
    void close(){for(String id:new ArrayList<>(entities.keySet()))remove(id);}
    void stale(Chunk c){for(Entity e:c.getEntities())if(isFarmEntity(e))e.remove();}
    @EventHandler public void load(ChunkLoadEvent e){stale(e.getChunk());for(var plot:p.plots.all())if(plot.world().equals(e.getWorld().getUID().toString())&&(plot.x()>>4)==e.getChunk().getX()&&(plot.z()>>4)==e.getChunk().getZ())sync(plot);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unload(ChunkUnloadEvent e){for(var pair:new ArrayList<>(entities.entrySet())){Location l=pair.getValue().display.getLocation();if(l.getWorld()==e.getWorld()&&(l.getBlockX()>>4)==e.getChunk().getX()&&(l.getBlockZ()>>4)==e.getChunk().getZ())remove(pair.getKey());}}
}
