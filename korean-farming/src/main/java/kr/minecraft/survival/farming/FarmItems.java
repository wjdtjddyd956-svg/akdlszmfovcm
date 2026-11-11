package kr.minecraft.survival.farming;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

final class FarmItems {
    final NamespacedKey key;final Map<String,ItemStack> canonical=new HashMap<>();
    FarmItems(KoreanFarmingPlugin plugin){key=new NamespacedKey(plugin,"item_id");for(var c:FarmRules.CROPS){canonical.put(c.id(),create(c.material(),c.id(),c.name(),"§7농장에서 수확한 정품 농산물","§7요리 · NPC 판매 · 도감 등록에 사용"));canonical.put(c.id()+"_seed",create(Material.WHEAT_SEEDS,c.id()+"_seed",c.name()+" 씨앗","§7젖은 경작지에 우클릭하여 심기","§7성장 "+c.minutes()+"분 · "+(c.perennial()?"수확 후 다시 성장":"수확 후 씨앗 1개 반환")));}
        for(var d:FarmRules.DISHES)canonical.put(d.id(),create(d.material(),d.id(),d.name(),"§7농부 "+d.level()+"레벨 요리","§7먹기 · NPC 판매에 사용"));}
    ItemStack create(Material material,String id,String name,String...lore){ItemStack s=new ItemStack(material);ItemMeta m=s.getItemMeta();m.setDisplayName("§f"+name);m.setLore(List.of(lore));m.getPersistentDataContainer().set(key,PersistentDataType.STRING,id);m.setItemModel(NamespacedKey.fromString("tycoon:farm/"+id));s.setItemMeta(m);return s;}
    String id(ItemStack s){return s!=null&&s.hasItemMeta()?s.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING):null;}
    ItemStack item(String id){ItemStack s=canonical.get(id);if(s==null)throw new IllegalArgumentException("알 수 없는 농장 아이템");return s.clone();}
    boolean matches(ItemStack s,String id){ItemStack c=canonical.get(id);if(s==null||c==null)return false;if(s.isSimilar(c))return true;ItemStack plain=c.clone();ItemMeta m=plain.getItemMeta();m.setItemModel(null);plain.setItemMeta(m);return s.isSimilar(plain);}
}
