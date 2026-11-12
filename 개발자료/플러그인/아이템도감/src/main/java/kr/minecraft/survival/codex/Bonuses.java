package kr.minecraft.survival.codex;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Transient, plugin-owned modifiers: no base-value or potion overwrites and no login stacking. */
final class Bonuses {
    final Map<Catalog.Stat,NamespacedKey> keys=new EnumMap<>(Catalog.Stat.class);
    Bonuses(Plugin plugin){for(Catalog.Stat stat:Catalog.Stat.values())keys.put(stat,new NamespacedKey(plugin,"bonus_"+stat.name().toLowerCase(Locale.ROOT)));}
    static Attribute attribute(Catalog.Stat stat){return switch(stat){case MOVE->Attribute.MOVEMENT_SPEED;case MINING->Attribute.BLOCK_BREAK_SPEED;case HEALTH->Attribute.MAX_HEALTH;case DAMAGE->Attribute.ATTACK_DAMAGE;case ARMOR->Attribute.ARMOR;case ATTACK_SPEED->Attribute.ATTACK_SPEED;case KNOCKBACK->Attribute.KNOCKBACK_RESISTANCE;case LUCK->Attribute.LUCK;};}
    static AttributeModifier.Operation operation(Catalog.Stat stat){return switch(stat){case MOVE,MINING,ATTACK_SPEED->AttributeModifier.Operation.ADD_SCALAR;default->AttributeModifier.Operation.ADD_NUMBER;};}
    void apply(Player player,Map<Catalog.Stat,Integer> values,boolean enabled){
        for(Catalog.Stat stat:Catalog.Stat.values()){
            int units=enabled?Math.min(stat.cap,Math.max(0,values.getOrDefault(stat,0))):0;double amount=units/10000.0;
            AttributeInstance instance=player.getAttribute(attribute(stat));if(instance==null&&units>0){player.registerAttribute(attribute(stat));instance=player.getAttribute(attribute(stat));}if(instance==null)continue;NamespacedKey key=keys.get(stat);AttributeModifier old=instance.getModifier(key);
            if(old!=null&&old.getOperation()==operation(stat)&&Double.compare(old.getAmount(),amount)==0&&units>0)continue;
            if(old!=null)instance.removeModifier(key);if(units>0)instance.addTransientModifier(new AttributeModifier(key,amount,operation(stat)));
        }
        AttributeInstance health=player.getAttribute(Attribute.MAX_HEALTH);if(health!=null&&!player.isDead()&&player.getHealth()>health.getValue())player.setHealth(health.getValue());
    }
    void remove(Player p){apply(p,Map.of(),false);}
}
