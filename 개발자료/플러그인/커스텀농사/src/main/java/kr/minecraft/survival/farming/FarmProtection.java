package kr.minecraft.survival.farming;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import kr.minecraft.survival.economy.domain.StateStore;

/** Authoritative protection checks; no event cancellation is ever reversed. */
final class FarmProtection implements Listener {
    final KoreanFarmingPlugin p;
    FarmProtection(KoreanFarmingPlugin p){this.p=p;}
    void build(Player player,Location l){
        if(!p.core.canFarmAt(player,l))throw new StateStore.Denied("다른 사람의 보호 구역입니다.");
        try{
            if(p.getServer().getPluginManager().isPluginEnabled("WorldGuard")&&!WgAccess.allowed(player,l))throw new StateStore.Denied("이 구역에서는 농사를 할 수 없습니다.");
            var gp=p.getServer().getPluginManager().getPlugin("GriefPrevention");if(gp!=null&&gp.isEnabled()){
                Object answer=gp.getClass().getMethod("allowBuild",Player.class,Location.class,Material.class).invoke(gp,player,l,Material.WHEAT);
                if(answer!=null)throw new StateStore.Denied("땅 주인의 건축 허가가 필요합니다.");
            }
        }catch(ReflectiveOperationException|LinkageError e){p.getLogger().warning("농장 보호 연동 확인 필요: "+e);throw new StateStore.Denied("보호 플러그인 연동을 확인할 때까지 농사를 중지합니다.");}
    }
    void access(Player player,FarmStore.Plot plot){
        if(!plot.owner().equals(player.getUniqueId().toString())&&!player.hasPermission("koreanfarming.admin"))throw new StateStore.Denied("작물 주인만 수확·회수할 수 있습니다.");
        build(player,p.location(plot));build(player,p.location(plot).subtract(0,1,0));
    }
    boolean reserved(Block b){return p.touching(b)!=null;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockBreak(BlockBreakEvent e){if(reserved(e.getBlock())){e.setCancelled(true);e.getPlayer().sendMessage("§e작물은 우클릭으로 수확하고 쉬프트+우클릭으로 회수하세요.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(reserved(e.getBlock())||(e instanceof BlockMultiPlaceEvent m&&m.getReplacedBlockStates().stream().anyMatch(b->reserved(b.getBlock()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void change(EntityChangeBlockEvent e){if(reserved(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fade(BlockFadeEvent e){if(reserved(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fromTo(BlockFromToEvent e){if(reserved(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(reserved(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(reserved(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(reserved(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::reserved);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explodeBlock(BlockExplodeEvent e){e.blockList().removeIf(this::reserved);}
    boolean piston(Block base,List<Block> moved,BlockFace direction){return reserved(base.getRelative(direction))||moved.stream().anyMatch(b->reserved(b)||reserved(b.getRelative(direction)));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){if(piston(e.getBlock(),e.getBlocks(),e.getDirection()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(piston(e.getBlock(),e.getBlocks(),e.getDirection())||piston(e.getBlock(),e.getBlocks(),e.getDirection().getOppositeFace()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grow(StructureGrowEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(p.renderer.isFarmEntity(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void feed(PlayerInteractEntityEvent e){String id=p.items.id(e.getPlayer().getInventory().getItem(e.getHand()));if(id!=null&&id.endsWith("_seed")&&!p.renderer.isFarmEntity(e.getRightClicked()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void craft(PrepareItemCraftEvent e){if(Arrays.stream(e.getInventory().getMatrix()).anyMatch(s->p.items.id(s)!=null))e.getInventory().setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cook(FurnaceSmeltEvent e){if(p.items.id(e.getSource())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void brew(BrewEvent e){if(Arrays.stream(e.getContents().getContents()).anyMatch(s->p.items.id(s)!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void prepareSmith(PrepareSmithingEvent e){for(var s:e.getInventory().getContents())if(p.items.id(s)!=null){e.setResult(null);return;}}
}
