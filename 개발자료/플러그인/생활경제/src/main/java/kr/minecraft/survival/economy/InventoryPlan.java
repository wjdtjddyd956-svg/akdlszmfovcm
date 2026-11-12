package kr.minecraft.survival.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Plans on clones; failed trades must not alter a live inventory. */
final class InventoryPlan {
    static ItemStack[] plan(ItemStack[] storage, Material material, int amount, boolean buying) {
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("거래 수량은 1~64개입니다.");
        ItemStack[] result = new ItemStack[storage.length];
        for (int i=0;i<storage.length;i++) result[i]=storage[i]==null?null:storage[i].clone();
        ItemStack plain=new ItemStack(material);
        int remaining=amount;
        for (int i=0;i<result.length && remaining>0;i++) {
            ItemStack item=result[i];
            if (item==null || !item.isSimilar(plain)) continue;
            int delta=buying?Math.min(remaining,Math.max(0,item.getMaxStackSize()-item.getAmount())):Math.min(remaining,item.getAmount());
            item.setAmount(item.getAmount()+(buying?delta:-delta));
            if (item.getAmount()==0) result[i]=null;
            remaining-=delta;
        }
        if (buying) {
            for (int i=0;i<result.length && remaining>0;i++) {
                if (result[i]!=null && result[i].getType()!=Material.AIR) continue;
                int delta=Math.min(remaining,plain.getMaxStackSize());
                result[i]=new ItemStack(material,delta);
                remaining-=delta;
            }
        }
        return remaining==0?result:null;
    }
}
