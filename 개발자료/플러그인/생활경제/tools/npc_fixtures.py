"""Additional offline signatures for native NPC tests; never package these classes."""
from pathlib import Path

SOURCES={
'org/bukkit/World.java': 'public interface World {org.bukkit.entity.Entity spawnEntity(Location location,org.bukkit.entity.EntityType type);}',
'org/bukkit/Location.java': '''public class Location implements Cloneable {private final World world;private final double x,y,z;private float yaw; public Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}public World getWorld(){return world;} public float getYaw(){return yaw;}public void setYaw(float v){yaw=v;}public double distanceSquared(Location b){if(world!=b.world)throw new IllegalArgumentException();return (x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z);}public Location clone(){Location n=new Location(world,x,y,z);n.yaw=yaw;return n;}}''',
'org/bukkit/NamespacedKey.java': '''public class NamespacedKey {private final String key;public NamespacedKey(org.bukkit.plugin.Plugin p,String k){key="koreaneconomy:"+k;}public boolean equals(Object o){return o instanceof NamespacedKey k&&key.equals(k.key);}public int hashCode(){return key.hashCode();}}''',
'org/bukkit/persistence/PersistentDataType.java': 'public interface PersistentDataType<P,C> {PersistentDataType<String,String> STRING=new PersistentDataType<>(){};}',
'org/bukkit/persistence/PersistentDataContainer.java': '''import org.bukkit.NamespacedKey;public interface PersistentDataContainer {<P,C> C get(NamespacedKey key,PersistentDataType<P,C> type);<P,C> void set(NamespacedKey key,PersistentDataType<P,C> type,C value);}''',
'org/bukkit/entity/EntityType.java': 'public enum EntityType {MANNEQUIN,VILLAGER}',
'org/bukkit/entity/Entity.java': '''import org.bukkit.*;import org.bukkit.persistence.PersistentDataContainer;import java.util.*;public interface Entity {default UUID getUniqueId(){return UUID.randomUUID();}default World getWorld(){return null;}default Location getLocation(){return new Location(getWorld(),0,0,0);}default List<Entity> getNearbyEntities(double x,double y,double z){return List.of();}default PersistentDataContainer getPersistentDataContainer(){return null;}default boolean isValid(){return true;}default void remove(){}default void setPersistent(boolean v){}default void setInvulnerable(boolean v){}default void setSilent(boolean v){}default void setGravity(boolean v){}default void setCustomName(String s){}default String getCustomName(){return null;}default void setCustomNameVisible(boolean v){}}''',
'org/bukkit/entity/LivingEntity.java': 'public interface LivingEntity extends Entity {default void setRemoveWhenFarAway(boolean v){}default void setCollidable(boolean v){}default void setAI(boolean v){}}',
'org/bukkit/entity/Mob.java': 'public interface Mob extends LivingEntity {}',
'org/bukkit/entity/Mannequin.java': 'public interface Mannequin extends LivingEntity {void setImmovable(boolean v);void setDescription(net.kyori.adventure.text.Component c);}',
'net/kyori/adventure/text/Component.java': 'public interface Component {}',
'org/bukkit/inventory/EquipmentSlot.java': 'public enum EquipmentSlot {HAND,OFF_HAND}',
'org/bukkit/event/player/PlayerInteractEntityEvent.java': '''import org.bukkit.entity.*;import org.bukkit.inventory.EquipmentSlot;public class PlayerInteractEntityEvent {public boolean cancelled;private final Player player;private final Entity entity;private final EquipmentSlot hand;public PlayerInteractEntityEvent(Player p,Entity e,EquipmentSlot h){player=p;entity=e;hand=h;}public Player getPlayer(){return player;}public Entity getRightClicked(){return entity;}public EquipmentSlot getHand(){return hand;}public void setCancelled(boolean c){cancelled=c;}}''',
'org/bukkit/event/player/PlayerInteractAtEntityEvent.java': '''import org.bukkit.entity.*;import org.bukkit.inventory.EquipmentSlot;public class PlayerInteractAtEntityEvent extends PlayerInteractEntityEvent {public PlayerInteractAtEntityEvent(Player p,Entity e,EquipmentSlot h){super(p,e,h);}}''',
'org/bukkit/event/entity/EntityDamageEvent.java': '''import org.bukkit.entity.Entity;public class EntityDamageEvent {public boolean cancelled;private final Entity entity;public EntityDamageEvent(Entity e){entity=e;}public Entity getEntity(){return entity;}public void setCancelled(boolean c){cancelled=c;}}''',
'org/bukkit/event/entity/EntityCombustEvent.java': '''import org.bukkit.entity.Entity;public class EntityCombustEvent {public boolean cancelled;private final Entity entity;public EntityCombustEvent(Entity e){entity=e;}public Entity getEntity(){return entity;}public void setCancelled(boolean c){cancelled=c;}}''',
'org/bukkit/event/entity/EntityTransformEvent.java': '''import org.bukkit.entity.*;public class EntityTransformEvent {public boolean cancelled;private final LivingEntity entity;public EntityTransformEvent(LivingEntity e){entity=e;}public Entity getEntity(){return entity;}public void setCancelled(boolean c){cancelled=c;}}''',
'org/bukkit/event/vehicle/VehicleEnterEvent.java': '''import org.bukkit.entity.Entity;public class VehicleEnterEvent {public boolean cancelled;private final Entity entity;public VehicleEnterEvent(Entity e){entity=e;}public Entity getEntered(){return entity;}public void setCancelled(boolean c){cancelled=c;}}''',
}

def write(root):
    root=Path(root)
    for name,source in SOURCES.items():
        p=root/name;p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text('package '+name.rsplit('/',1)[0].replace('/','.')+';\n'+source+'\n')
    p=root/'org/bukkit/entity/Player.java'
    p.write_text(p.read_text().replace('extends HumanEntity, org.bukkit.OfflinePlayer','extends HumanEntity, org.bukkit.OfflinePlayer, org.bukkit.entity.Entity'))
    p=root/'org/bukkit/configuration/file/FileConfiguration.java'
    p.write_text(p.read_text().replace('public double getDouble','public boolean getBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean b?b:fallback;} public double getDouble'))
