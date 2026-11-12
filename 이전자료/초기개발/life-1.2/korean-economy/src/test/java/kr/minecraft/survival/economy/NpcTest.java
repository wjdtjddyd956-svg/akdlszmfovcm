package kr.minecraft.survival.economy;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.*;

public class NpcTest {
    static int checks;
    static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    static class Data implements PersistentDataContainer {
        final Map<NamespacedKey,Object> values=new HashMap<>();
        @SuppressWarnings("unchecked") public <P,C>C get(NamespacedKey key,PersistentDataType<P,C> type){return (C)values.get(key);}
        public <P,C>void set(NamespacedKey key,PersistentDataType<P,C> type,C value){values.put(key,value);}
    }
    static class Npc implements LivingEntity {
        final Location location;final Data data=new Data();boolean valid=true,persistent,invulnerable,gravity=true;String name;
        Npc(Location l){location=l;}
        public World getWorld(){return location.getWorld();}
        public Location getLocation(){return location;}
        public PersistentDataContainer getPersistentDataContainer(){return data;}
        public boolean isValid(){return valid;}
        public void remove(){valid=false;}
        public void setPersistent(boolean v){persistent=v;}
        public void setInvulnerable(boolean v){invulnerable=v;}
        public void setGravity(boolean v){gravity=v;}
        public void setCustomName(String s){name=s;}
        public String getCustomName(){return name;}
    }
    static class Person extends Npc implements Mannequin {
        boolean fixed,descriptionCleared;
        Person(Location l){super(l);}
        public void setImmovable(boolean v){fixed=v;}
        public void setDescription(net.kyori.adventure.text.Component c){descriptionCleared=c==null;}
    }
    static class Villager extends Npc implements Mob {
        boolean ai=true;Villager(Location l){super(l);}
        public void setAI(boolean v){ai=v;}
    }
    static class WorldData implements World {
        List<Entity> entities=new ArrayList<>();
        public Entity spawnEntity(Location l,EntityType t){Npc npc=t==EntityType.MANNEQUIN?new Person(l):new Villager(l);entities.add(npc);return npc;}
    }
    static class User extends EconomyTest.User {
        final WorldData world;Location at;boolean use=true;
        User(WorldData w){world=w;at=new Location(w,0,64,0);}
        public World getWorld(){return world;}
        public Location getLocation(){return at;}
        public List<Entity> getNearbyEntities(double x,double y,double z){return world.entities;}
        public boolean hasPermission(String name){return name.equals("koreaneconomy.use")?use:super.hasPermission(name);}
    }
    public static void main(String[] args)throws Exception {
        EconomyTest.Plugin p=new EconomyTest.Plugin(Files.createTempDirectory("korean-npc-test-"));p.onEnable();
        WorldData world=new WorldData();User admin=new User(world),player=new User(world);
        p.onCommand(player,new Command("상인"),"상인",new String[]{"생성","농부","농산물"});
        ok(world.entities.isEmpty(),"regular player cannot spawn NPCs");
        admin.admin=true;
        p.onCommand(admin,new Command("상인"),"상인",new String[]{"생성","농부","없는품목"});
        ok(world.entities.isEmpty(),"invalid category rejected without spawning");
        p.onCommand(admin,new Command("상인"),"상인",new String[]{"생성","농부","농산물"});
        ok(world.entities.size()==1&&world.entities.get(0) instanceof Person,"default appearance is native mannequin");
        Person npc=(Person)world.entities.get(0);
        ok(npc.persistent&&npc.invulnerable&&!npc.gravity&&npc.fixed&&npc.descriptionCleared,"NPC is persistent, protected and fixed with native description removed");
        ok(npc.name.contains("농부")&&npc.name.contains("우클릭"),"Korean nameplate");
        ok(p.npcs.category(npc)==ShopCategory.CROPS,"category stored in entity persistent data");
        NpcShops recreated=new NpcShops(p,(u,c,n)->{});
        ok(recreated.category(npc)==ShopCategory.CROPS,"new listener reads existing native entity tag without memory registry");
        PlayerInteractEntityEvent click=new PlayerInteractEntityEvent(player,npc,EquipmentSlot.HAND);p.npcs.onInteract(click);
        ok(click.cancelled&&!(player.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"cancel vanilla interaction and defer opening");
        p.npcs.onInteractAt(new PlayerInteractAtEntityEvent(player,npc,EquipmentSlot.HAND));
        ok(p.tasks.size()==1,"duplicate interact packets schedule one menu only");p.tick();
        KoreanEconomyPlugin.Market market=(KoreanEconomyPlugin.Market)player.view.getTopInventory().getHolder();
        ok(market.category==ShopCategory.CROPS&&market.prices.size()==3,"farmer shows crops only");
        ok(market.npc==npc&&market.prices.stream().allMatch(v->ShopCategory.CROPS.includes(v.material())),"market retains NPC origin and category");
        User off=new User(world);PlayerInteractEntityEvent offClick=new PlayerInteractEntityEvent(off,npc,EquipmentSlot.OFF_HAND);p.npcs.onInteract(offClick);p.tick();
        ok(offClick.cancelled&&!(off.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"offhand cannot equip NPC or open duplicate menu");
        User denied=new User(world);denied.use=false;p.npcs.onInteract(new PlayerInteractEntityEvent(denied,npc,EquipmentSlot.HAND));p.tick();
        ok(!(denied.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"NPC checks player economy permission");
        User remote=new User(world);remote.at=new Location(world,90,64,0);p.npcs.onInteract(new PlayerInteractEntityEvent(remote,npc,EquipmentSlot.HAND));p.tick();
        ok(!(remote.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"remote packet cannot open NPC shop");
        User anotherWorld=new User(new WorldData());p.npcs.onInteract(new PlayerInteractEntityEvent(anotherWorld,npc,EquipmentSlot.HAND));p.tick();
        ok(!(anotherWorld.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"cross-world interaction rejected");
        User moved=new User(world);p.npcs.onInteract(new PlayerInteractEntityEvent(moved,npc,EquipmentSlot.HAND));moved.at=new Location(world,20,64,0);p.tick();
        ok(!(moved.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"recheck range after scheduled click");
        EntityDamageEvent damage=new EntityDamageEvent(npc);p.npcs.onDamage(damage);ok(damage.cancelled,"NPC damage blocked");
        EntityCombustEvent burn=new EntityCombustEvent(npc);p.npcs.onCombust(burn);ok(burn.cancelled,"NPC combustion blocked");
        EntityTransformEvent change=new EntityTransformEvent(npc);p.npcs.onTransform(change);ok(change.cancelled,"NPC transformation blocked");
        VehicleEnterEvent boat=new VehicleEnterEvent(npc);p.npcs.onVehicle(boat);ok(boat.cancelled,"boat cannot move shop NPC");
        Villager normal=new Villager(new Location(world,0,64,0));world.entities.add(normal);
        PlayerInteractEntityEvent ordinaryClick=new PlayerInteractEntityEvent(player,normal,EquipmentSlot.HAND);p.npcs.onInteract(ordinaryClick);ok(!ordinaryClick.cancelled,"ordinary villagers untouched despite similar type");
        EntityDamageEvent ordinaryDamage=new EntityDamageEvent(normal);p.npcs.onDamage(ordinaryDamage);ok(!ordinaryDamage.cancelled,"ordinary entity damage untouched");
        player.at=new Location(world,40,64,0);
        org.bukkit.event.inventory.InventoryClickEvent buy=new org.bukkit.event.inventory.InventoryClickEvent(player.view,player,19);p.onClick(buy);p.tick();
        ok(p.balance==0&&player.view.getTopInventory().getHolder()==null,"leaving NPC closes stale market before money or items change");
        player.at=new Location(world,0,64,0);
        User removed=new User(world);p.npcs.onInteract(new PlayerInteractEntityEvent(removed,npc,EquipmentSlot.HAND));npc.remove();p.tick();
        ok(!(removed.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market),"removed NPC cannot open queued market");
        p.onCommand(admin,new Command("상인"),"상인",new String[]{"생성","광물상","광물","주민"});
        Villager merchant=(Villager)world.entities.get(world.entities.size()-1);
        ok(!merchant.ai&&p.npcs.category(merchant)==ShopCategory.MINERALS,"villager alternative has no AI and mineral category");
        p.onCommand(admin,new Command("상인"),"상인",new String[]{"삭제"});
        ok(!merchant.valid&&normal.valid,"delete removes nearest marked shop only");
        p.onCommand(player,new Command("상점"),"상점",new String[0]);
        ok(!(player.view.getTopInventory().getHolder() instanceof KoreanEconomyPlugin.Market)&&player.messages.get(player.messages.size()-1).contains("NPC"),"shop command guides to NPC by default");
        p.config.set("상점-명령어열기",true);p.onCommand(player,new Command("상점"),"상점",new String[0]);
        ok(((KoreanEconomyPlugin.Market)player.view.getTopInventory().getHolder()).prices.size()==8,"optional remote access remains explicitly configurable");
        ok(ShopCategory.MATERIALS.includes(Material.OAK_LOG)&&!ShopCategory.MATERIALS.includes(Material.DIAMOND),"materials category excludes minerals");
        System.out.println("PASS: "+checks+" NPC assertions (offline Bukkit fixtures; no in-game test)");
    }
}
