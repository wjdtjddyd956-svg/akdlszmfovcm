package kr.minecraft.survival.economy;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
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
        if(!(sender instanceof Player player)){sender.sendMessage("게임 안에서 상인을 배치하세요.");return true;}
        if(args.length>=1&&args[0].equals("생성")) {
            if(args.length<3||args.length>4){help(sender);return true;}
            ShopCategory category=ShopCategory.parse(args[2]);
            if(category==null){sender.sendMessage("§c품목은 전체·농산물·광물·재료·요리·임무 중에서 선택하세요.");return true;}
            String name=args[1];
            if(name.isBlank()||name.length()>24||name.indexOf('§')>=0){sender.sendMessage("§c상인 이름은 색상 코드 없이 1~24자로 입력하세요.");return true;}
            String appearance=args.length==4?args[3]:"사람";
            if(!appearance.equals("사람")&&!appearance.equals("주민")){sender.sendMessage("§c외형은 사람 또는 주민으로 입력하세요.");return true;}
            try {
                Location location=player.getLocation().clone();location.setYaw(location.getYaw()+180);
                spawn(name,category,appearance,location);
                sender.sendMessage("§a"+name+" 상인을 생성했습니다. 품목: "+category.label+". 한 걸음 물러나 우클릭하세요.");
            }catch(RuntimeException e){plugin.getLogger().warning("상인 생성 실패: "+e);sender.sendMessage("§c상인을 생성하지 못했습니다. 서버 버전과 콘솔을 확인하세요.");}
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
    Entity spawn(String name,ShopCategory category,String appearance,Location location){
        Entity entity=null;
        try{
            EntityType type=EntityType.valueOf(appearance.equals("사람")?"MANNEQUIN":"VILLAGER");entity=location.getWorld().spawnEntity(location,type);
            if(!entity.isValid()||!(entity instanceof LivingEntity))throw new IllegalStateException("다른 플러그인 또는 월드 규칙이 NPC 생성을 취소했습니다.");
            LivingEntity living=(LivingEntity)entity;entity.setPersistent(true);entity.setInvulnerable(true);entity.setSilent(true);entity.setGravity(false);living.setRemoveWhenFarAway(false);living.setCollidable(false);
            if(living instanceof Mob mob)mob.setAI(false);if(living instanceof Mannequin mannequin){mannequin.setImmovable(true);mannequin.setDescription(null);}
            entity.setCustomName("§6"+name+" §e["+(category==ShopCategory.QUESTS?"임무":"상점")+" · 우클릭]");entity.setCustomNameVisible(true);entity.getPersistentDataContainer().set(key,PersistentDataType.STRING,category.name());return entity;
        }catch(RuntimeException e){if(entity!=null)entity.remove();throw e;}
    }
    boolean town(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)||!sender.hasPermission("koreaneconomy.npc.admin")){sender.sendMessage("§c게임 안에서 운영자만 배치할 수 있습니다.");return true;}
        if(args.length!=1||!args[0].equals("확인")){sender.sendMessage("§e평평하고 비어 있는 11×5 공간 가운데 서서 /마을설치 확인");sender.sendMessage("§7주변에 잡화·농산물·광물·요리·임무 NPC 5명을 배치합니다. 블록은 바꾸지 않습니다.");return true;}
        if(p.getNearbyEntities(8,8,8).stream().anyMatch(e->category(e)!=null)){sender.sendMessage("§c주변에 기존 상인이 있습니다. 다른 빈 장소에서 배치하거나 /상인 생성으로 개별 배치하세요.");return true;}
        List<Location> locations=new ArrayList<>();Location center=p.getLocation();
        for(int x:new int[]{-4,-2,0,2,4}){Location l=center.clone().add(x,0,2);l.setX(l.getBlockX()+0.5);l.setZ(l.getBlockZ()+0.5);l.setYaw(180);if(!l.getBlock().getType().isAir()||!l.clone().add(0,1,0).getBlock().getType().isAir()||!l.clone().add(0,-1,0).getBlock().getType().isSolid()){sender.sendMessage("§cNPC가 설 평평한 바닥과 두 칸 높이의 빈 공간이 필요합니다. 블록을 고치고 다시 시도하세요.");return true;}locations.add(l);}
        List<Entity> created=new ArrayList<>();ShopCategory[] categories={ShopCategory.ALL,ShopCategory.CROPS,ShopCategory.MINERALS,ShopCategory.FOOD,ShopCategory.QUESTS};String[] names={"잡화상인","농부","광물상","주방장","마을이장"};
        try{for(int i=0;i<5;i++)created.add(spawn(names[i],categories[i],"사람",locations.get(i)));sender.sendMessage("§aNPC 5명을 배치했습니다. 우클릭해 이용하세요. /상인 삭제로 가까운 NPC를 지울 수 있습니다.");}
        catch(RuntimeException e){for(Entity npc:created)npc.remove();plugin.getLogger().warning("마을 배치 실패: "+e);sender.sendMessage("§c배치를 완료하지 못해 이번에 생성한 NPC를 제거했습니다.");}return true;
    }
    private void help(CommandSender sender){
        sender.sendMessage("§6[상인 관리] 현재 서 있는 위치에 생성됩니다.");
        sender.sendMessage("§e/상인 생성 잡화상인 전체");sender.sendMessage("§e/상인 생성 농부 농산물");
        sender.sendMessage("§e/상인 생성 광물상 광물 주민");sender.sendMessage("§e/상인 생성 주방장 요리 · /상인 생성 이장 임무");sender.sendMessage("§e/상인 삭제 · /상인 목록");
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
