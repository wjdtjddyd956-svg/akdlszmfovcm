package kr.minecraft.survival.economy;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import kr.minecraft.survival.economy.domain.*;
import static kr.minecraft.survival.economy.domain.ActionEngine.*;

/** Chunk claims apply at every height. Other plugins' cancellations are never reversed. */
final class LandProtection implements Listener {
    final Tycoon t;private Map<String,StateStore.Land> claims=Map.of();private final Map<String,Set<String>> trusts=new HashMap<>();
    LandProtection(Tycoon t){this.t=t;reload();}
    static String key(Location l){return l.getWorld().getUID()+":"+(l.getBlockX()>>4)+":"+(l.getBlockZ()>>4);}
    void reload(){Map<String,StateStore.Land> map=new HashMap<>();trusts.clear();for(var l:t.store.lands()){map.put(l.world()+":"+l.x()+":"+l.z(),l);trusts.computeIfAbsent(l.owner(),t.store::trusted);}claims=Map.copyOf(map);}
    StateStore.Land land(Location l){return claims.get(key(l));}
    boolean allowed(Player p,Location location){var l=land(location);return l==null||p.hasPermission("koreaneconomy.land.bypass")||l.owner().equals(p.getUniqueId().toString())||("ACTIVE".equals(l.state())&&trusts.getOrDefault(l.owner(),Set.of()).contains(p.getUniqueId().toString()));}
    boolean same(Location a,Location b){var x=land(a);var y=land(b);return x==null?y==null:y!=null&&x.owner().equals(y.owner())&&x.state().equals(y.state());}
    List<Location> locations(Inventory inventory){
        InventoryHolder holder=inventory.getHolder();
        if(holder instanceof Player||holder instanceof Tycoon.Screen||holder instanceof KoreanEconomyPlugin.Market)return List.of();
        if(holder instanceof DoubleChest doubleChest){List<Location> result=new ArrayList<>();for(InventoryHolder side:new InventoryHolder[]{doubleChest.getLeftSide(),doubleChest.getRightSide()})if(side instanceof BlockState b&&b.isPlaced())result.add(b.getLocation());return result;}
        if(holder instanceof BlockState block&&block.isPlaced())return List.of(block.getLocation());
        if(holder instanceof Entity entity)return List.of(entity.getLocation());
        Location location=inventory.getLocation();return location==null?List.of():List.of(location);
    }
    boolean allowed(Player p,Inventory inventory){return locations(inventory).stream().allMatch(l->allowed(p,l));}
    boolean transferAllowed(Inventory source,Inventory destination){for(Location a:locations(source))for(Location b:locations(destination))if(!same(a,b))return false;return true;}
    boolean deny(Player p,Location l){if(allowed(p,l))return false;p.sendMessage("§c다른 사람의 보호 구역입니다.");return true;}
    void command(Player p,String[] a){
        if(t.plugin.getServer().getPluginManager().isPluginEnabled("GriefPrevention")){t.plugin.serverTools.land(p,a);return;}
        String op=a.length==0?"안내":a[0];Location loc=p.getLocation();String world=p.getWorld().getUID().toString();int x=loc.getBlockX()>>4,z=loc.getBlockZ()>>4;
        switch(op){
            case "생성","확장"->{
                t.survival(p);if(a.length!=2||!a[1].equals("확인")){long cost=t.store.landCost(p.getUniqueId(),250000);p.sendMessage("§e현재 청크(16×16, 모든 높이)를 "+Tycoon.money(cost)+"에 보호합니다. 최대 3청크.");p.sendMessage("§e/땅보호 "+op+" 확인 §7으로 확정하세요. 포기 시 환불하지 않습니다.");return;}
                if(t.plugin.getConfig().getStringList("땅보호-금지월드").contains(p.getWorld().getName()))throw new StateStore.Denied("이 월드에서는 개인 땅을 만들 수 없습니다.");
                if(worldGuardOverlap(p.getWorld(),x,z))throw new StateStore.Denied("WorldGuard 관리 영역과 겹치는 청크입니다.");
                StateStore.Receipt r=t.store.reserveLand(p.getUniqueId(),world,x,z,3,250000);reload();
                Outcome outcome=t.engine.reserved(r,Direction.DEBIT,NO_ITEMS,()->t.store.activateLandInside(r.id()));reload();t.result(p,outcome,"현재 청크를 보호했습니다. /땅보호 초대 닉네임으로 친구를 초대하세요.");
            }
            case "목록"->{p.sendMessage("§6[내 땅] 최대 3청크");for(var l:t.store.lands(p.getUniqueId()))p.sendMessage("§e"+worldName(l.world())+" 청크 "+l.x()+", "+l.z()+" · "+l.state());}
            case "확인"->{var l=land(loc);p.sendMessage(l==null?"§e현재 청크는 주인이 없습니다.":"§e소유자: "+t.store.playerName(l.owner())+" · "+l.state());}
            case "초대","추방"->{if(a.length!=2)throw new StateStore.Denied("/땅보호 "+op+" 닉네임");UUID id=t.store.playerId(a[1]).orElseThrow(()->new StateStore.Denied("서버에 접속했던 정확한 닉네임을 입력하세요."));t.store.trust(p.getUniqueId(),id,op.equals("초대"));reload();t.store.meta("land-admin:last","owner="+p.getUniqueId()+" op="+op+" member="+id+" at="+System.currentTimeMillis());p.sendMessage("§a내 모든 땅의 건축·상자 권한을 "+(op.equals("초대")?"부여했습니다.":"회수했습니다."));}
            case "권한"->{p.sendMessage("§6[내 땅 초대 목록] 모든 내 땅에 적용");for(String id:t.store.trusted(p.getUniqueId().toString()))p.sendMessage("§e"+t.store.playerName(id));}
            case "포기"->{if(a.length!=2||!a[1].equals("확인"))throw new StateStore.Denied("현재 청크 보호를 해제하려면 /땅보호 포기 확인 · 환불 없음");t.store.abandon(p.getUniqueId(),world,x,z);reload();p.sendMessage("§e현재 청크의 보호를 해제했습니다. 첫 땅 무료 혜택은 다시 지급되지 않습니다.");}
            default->{p.sendMessage("§6[내 땅] 첫 청크 무료, 추가 청크 2,500원 · 최대 3개");p.sendMessage("§e/땅보호 생성 · /땅보호 확장 §7(기존 땅 옆 청크)");p.sendMessage("§e/땅보호 목록 · 확인 · 초대 닉네임 · 추방 닉네임 · 권한");p.sendMessage("§e/땅보호 포기 확인 §7: 현재 청크 해제, 환불 없음");}
        }
    }
    String worldName(String id){World w=t.plugin.getServer().getWorld(UUID.fromString(id));return w==null?id.substring(0,8):w.getName();}
    boolean worldGuardOverlap(World world,int x,int z){
        if(!t.plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard"))return false;
        // Optional, version-independent bridge. A bridge error denies a new claim rather than bypassing regions.
        try{
            Class<?> adapter=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");Object weWorld=adapter.getMethod("adapt",World.class).invoke(null,world);
            Class<?> wg=Class.forName("com.sk89q.worldguard.WorldGuard");Object instance=wg.getMethod("getInstance").invoke(null);Object platform=wg.getMethod("getPlatform").invoke(instance);
            Object container=platform.getClass().getMethod("getRegionContainer").invoke(platform);Class<?> weWorldType=Class.forName("com.sk89q.worldedit.world.World");Object manager=container.getClass().getMethod("get",weWorldType).invoke(container,weWorld);if(manager==null)return false;
            Class<?> vector=Class.forName("com.sk89q.worldedit.math.BlockVector3");Object min=vector.getMethod("at",int.class,int.class,int.class).invoke(null,x<<4,world.getMinHeight(),z<<4);Object max=vector.getMethod("at",int.class,int.class,int.class).invoke(null,(x<<4)+15,world.getMaxHeight()-1,(z<<4)+15);
            Class<?> regionType=Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");Object box=Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion").getConstructor(String.class,vector,vector).newInstance("__tycoon_check",min,max);
            Object set=manager.getClass().getMethod("getApplicableRegions",regionType).invoke(manager,box);
            if((int)set.getClass().getMethod("size").invoke(set)>0)return true;
            // A global region is also a deliberate administrator reservation.
            return manager.getClass().getMethod("getRegion",String.class).invoke(manager,"__global__")!=null;
        }catch(ReflectiveOperationException|RuntimeException e){t.plugin.getLogger().warning("WorldGuard 영역 확인 실패: "+e);return true;}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(deny(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(deny(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void multi(BlockMultiPlaceEvent e){for(BlockState b:e.getReplacedBlockStates())if(deny(e.getPlayer(),b.getLocation())){e.setCancelled(true);break;}}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){if(e.getClickedBlock()!=null&&!allowed(e.getPlayer(),e.getClickedBlock().getLocation())){e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucket(PlayerBucketEmptyEvent e){if(deny(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fill(PlayerBucketFillEvent e){if(deny(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entity(PlayerInteractEntityEvent e){if(t.plugin.npcs.category(e.getRightClicked())==null&&deny(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityAt(PlayerInteractAtEntityEvent e){entity(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player)return;Entity attacker=e.getDamager();if(attacker instanceof Projectile projectile&&projectile.getShooter() instanceof Entity source)attacker=source;if(attacker instanceof Player p){if(deny(p,e.getEntity().getLocation()))e.setCancelled(true);}else if(land(e.getEntity().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hanging(HangingBreakEvent e){if(land(e.getEntity().getLocation())==null)return;if(e instanceof HangingBreakByEntityEvent by&&by.getRemover() instanceof Player p&&allowed(p,e.getEntity().getLocation()))return;e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hangingPlace(HangingPlaceEvent e){if(e.getPlayer()!=null&&deny(e.getPlayer(),e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void armor(PlayerArmorStandManipulateEvent e){if(deny(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void leash(PlayerLeashEntityEvent e){if(deny(e.getPlayer(),e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p&&!allowed(p,e.getInventory()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void containerClick(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&!allowed(p,e.getView().getTopInventory()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void containerDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&!allowed(p,e.getView().getTopInventory()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->land(b.getLocation())!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplode(BlockExplodeEvent e){e.blockList().removeIf(b->land(b.getLocation())!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(land(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(land(e.getBlock().getLocation())!=null&&(e.getPlayer()==null||!allowed(e.getPlayer(),e.getBlock().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){if(!same(e.getBlock().getLocation(),e.getToBlock().getLocation()))e.setCancelled(true);}
    boolean piston(Block base,List<Block> blocks,org.bukkit.block.BlockFace dir){for(Block b:blocks){Location from=b.getLocation(),to=b.getRelative(dir).getLocation();if(!same(base.getLocation(),from)||!same(from,to))return false;}return same(base.getLocation(),base.getRelative(dir).getLocation());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void push(BlockPistonExtendEvent e){if(!piston(e.getBlock(),e.getBlocks(),e.getDirection()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pull(BlockPistonRetractEvent e){if(!piston(e.getBlock(),e.getBlocks(),e.getDirection().getOppositeFace())||!piston(e.getBlock(),e.getBlocks(),e.getDirection()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){if(!transferAllowed(e.getSource(),e.getDestination()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(InventoryPickupItemEvent e){if(locations(e.getInventory()).stream().anyMatch(a->!same(a,e.getItem().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityBlock(EntityChangeBlockEvent e){if(land(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grow(StructureGrowEvent e){if(e.getPlayer()!=null){for(BlockState b:e.getBlocks())if(!allowed(e.getPlayer(),b.getLocation())){e.setCancelled(true);return;}}else if(e.getBlocks().stream().anyMatch(b->!same(e.getLocation(),b.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(e.getPlayer()!=null&&e.getBlocks().stream().anyMatch(b->!allowed(e.getPlayer(),b.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(!same(e.getSource().getLocation(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void vehicle(org.bukkit.event.vehicle.VehicleDamageEvent e){if(land(e.getVehicle().getLocation())!=null&&(!(e.getAttacker() instanceof Player p)||!allowed(p,e.getVehicle().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void vehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent e){if(land(e.getVehicle().getLocation())!=null&&(!(e.getAttacker() instanceof Player p)||!allowed(p,e.getVehicle().getLocation())))e.setCancelled(true);}

}
