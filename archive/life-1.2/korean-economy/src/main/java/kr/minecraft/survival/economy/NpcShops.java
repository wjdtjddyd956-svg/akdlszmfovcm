package kr.minecraft.survival.economy;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Native world-persistent NPCs; no packets, NMS, skin network calls or external NPC plugin. */
final class NpcShops implements Listener {
    interface Opener {void open(Player player,ShopCategory category,Entity npc);}
    private final KoreanEconomyPlugin plugin;
    private final Opener opener;
    private final NamespacedKey key;
    private final Map<UUID,Long> clicks=new HashMap<>();
    NpcShops(KoreanEconomyPlugin plugin,Opener opener){this.plugin=plugin;this.opener=opener;key=new NamespacedKey(plugin,"market_npc");}
    ShopCategory category(Entity entity) {
        String stored=entity.getPersistentDataContainer().get(key,PersistentDataType.STRING);
        if(stored==null)return null;
        try{return ShopCategory.valueOf(stored);}catch(IllegalArgumentException e){return null;}
    }
    boolean nearby(Player player,Entity npc) {
        return npc.isValid()&&player.getWorld().equals(npc.getWorld())&&player.getLocation().distanceSquared(npc.getLocation())<=25;
    }
    boolean usable(Player player,Entity npc){return category(npc)!=null&&nearby(player,npc);}
    boolean command(CommandSender sender,String[] args) {
        if(!sender.hasPermission("koreaneconomy.npc.admin")){sender.sendMessage("§c운영자만 상인을 배치하거나 삭제할 수 있습니다.");return true;}
        if(sender instanceof ConsoleCommandSender && args.length>=1 && args[0].equals("배치")) {
            if(args.length<7 || args.length>8) {
                sender.sendMessage("상인 배치 월드 X Y Z 이름 품목 [사람|주민]");
                return true;
            }
            World world=plugin.getServer().getWorld(args[1]);
            try {
                if(world==null) throw new IllegalArgumentException();
                double x=Double.parseDouble(args[2]),y=Double.parseDouble(args[3]),z=Double.parseDouble(args[4]);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>=29_999_984
                        ||Math.abs(z)>=29_999_984||y<=world.getMinHeight()||y+1>=world.getMaxHeight()) throw new IllegalArgumentException();
                Location point=new Location(world,x,y,z);
                var feet=point.getBlock();
                var below=feet.getRelative(BlockFace.DOWN);
                if(!feet.isPassable()||feet.isLiquid()||!feet.getRelative(BlockFace.UP).isPassable()||!below.getType().isSolid()) {
                    sender.sendMessage("발밑에 단단한 바닥이 있고 위로 두 칸이 비어 있는 위치를 사용하세요.");
                    return true;
                }
                spawn(sender,point,args[5],args[6],args.length==8?args[7]:"사람");
            } catch(IllegalArgumentException e) {sender.sendMessage("월드와 좌표를 확인하세요.");}
            return true;
        }
        if(!(sender instanceof Player player)){sender.sendMessage("게임 안에서 상인을 배치하세요.");return true;}
        if(args.length>=1&&args[0].equals("생성")) {
            if(args.length<3||args.length>4){help(sender);return true;}
            Location location=player.getLocation().clone();
            location.setYaw(location.getYaw()+180);
            spawn(sender,location,args[1],args[2],args.length==4?args[3]:"사람");
            return true;
        }
        if(args.length==1&&args[0].equals("삭제")) {
            Entity closest=player.getNearbyEntities(4,4,4).stream().filter(e->category(e)!=null&&e.isValid())
                    .filter(e->e.getLocation().distanceSquared(player.getLocation())<=16)
                    .min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(player.getLocation()))).orElse(null);
            if(closest==null)sender.sendMessage("§e4블록 안에 이 플러그인으로 만든 상인이 없습니다.");
            else {closest.remove();sender.sendMessage("§a가장 가까운 상인 하나를 삭제했습니다.");}
            return true;
        }
        if(args.length==1&&args[0].equals("목록")) {
            List<Entity> list=player.getNearbyEntities(16,16,16).stream().filter(e->category(e)!=null&&e.isValid()).toList();
            sender.sendMessage("§6[주변 상인] "+list.size()+"명 (주변 16블록 구역)");
            for(Entity entity:list)sender.sendMessage("§e"+entity.getCustomName()+" §7| "+category(entity).label);
            return true;
        }
        help(sender);return true;
    }
    private void spawn(CommandSender sender,Location location,String name,String categoryName,String appearance) {
        ShopCategory category=ShopCategory.parse(categoryName);
        if(category==null){sender.sendMessage("§c전체·농산물·광물·재료·일일임무 중에서 선택하세요.");return;}
        if(name.isBlank()||name.length()>24||name.indexOf('§')>=0){sender.sendMessage("§c상인 이름은 색상 코드 없이 1~24자로 입력하세요.");return;}
        if(!appearance.equals("사람")&&!appearance.equals("주민")){sender.sendMessage("§c외형은 사람 또는 주민으로 입력하세요.");return;}
        String display="§6"+name+(category==ShopCategory.QUEST?" §e[임무 · 우클릭]":" §e[상점 · 우클릭]");
        if(location.getWorld().getNearbyEntities(location,1,1,1).stream().anyMatch(n->category(n)==category && display.equals(n.getCustomName()))) {
            sender.sendMessage("§e이 위치에 같은 NPC가 있습니다.");
            return;
        }
        Entity entity=null;
        try {
            entity=location.getWorld().spawnEntity(location,appearance.equals("사람")?EntityType.MANNEQUIN:EntityType.VILLAGER);
            if(!entity.isValid())throw new IllegalStateException("다른 플러그인 또는 월드 규칙이 생성을 취소했습니다.");
            if(!(entity instanceof LivingEntity living))throw new IllegalStateException("살아있는 NPC 엔티티가 아닙니다.");
            entity.setPersistent(true);entity.setInvulnerable(true);entity.setSilent(true);entity.setGravity(false);
            living.setRemoveWhenFarAway(false);living.setCollidable(false);
            if(living instanceof Mob mob)mob.setAI(false);
            if(living instanceof Mannequin mannequin){mannequin.setImmovable(true);mannequin.setDescription(null);}
            entity.setCustomName(display);entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer().set(key,PersistentDataType.STRING,category.name());
            sender.sendMessage("§a"+name+" NPC를 생성했습니다. 종류: "+category.label+". 우클릭하면 열립니다.");
        } catch(RuntimeException e) {
            if(entity!=null)entity.remove();
            plugin.getLogger().warning("상인 생성 실패: "+e);
            sender.sendMessage("§c상인을 생성하지 못했습니다. 서버 로그를 확인하세요.");
        }
    }
    private void help(CommandSender sender){
        sender.sendMessage("§6[상인 관리] 현재 서 있는 위치에 생성됩니다.");
        sender.sendMessage("§e/상인 생성 잡화상인 전체");sender.sendMessage("§e/상인 생성 농부 농산물");
        sender.sendMessage("§e/상인 생성 임무안내 일일임무");
        sender.sendMessage("§e/상인 생성 광물상 광물 주민");sender.sendMessage("§e/상인 삭제 · /상인 목록");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onInteract(PlayerInteractEntityEvent event){interact(event);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onInteractAt(PlayerInteractAtEntityEvent event){interact(event);}
    private void interact(PlayerInteractEntityEvent event) {
        Entity npc=event.getRightClicked();ShopCategory category=category(npc);
        if(category==null)return;
        event.setCancelled(true); // Also block offhand equipment changes and vanilla villager trading.
        Player player=event.getPlayer();
        if(event.getHand()!=EquipmentSlot.HAND||!player.hasPermission("koreaneconomy.use")||!nearby(player,npc))return;
        long now=System.currentTimeMillis();Long previous=clicks.get(player.getUniqueId());
        if(previous!=null&&now-previous<350)return;
        clicks.put(player.getUniqueId(),now);
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(player.isOnline()&&player.hasPermission("koreaneconomy.use")&&usable(player,npc))opener.open(player,category,npc);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event){if(category(event.getEntity())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event){if(category(event.getEntity())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event){if(category(event.getEntity())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onVehicle(VehicleEnterEvent event){if(category(event.getEntered())!=null)event.setCancelled(true);}
    @EventHandler public void onQuit(PlayerQuitEvent event){clicks.remove(event.getPlayer().getUniqueId());}
}
