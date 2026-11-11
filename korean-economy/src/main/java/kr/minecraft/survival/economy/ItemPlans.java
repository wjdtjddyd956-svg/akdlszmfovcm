package kr.minecraft.survival.economy;
import org.bukkit.inventory.ItemStack;

/** Pure cloned storage plans, including unstackable food and exact persistent metadata. */
final class ItemPlans {
    static ItemStack[] copy(ItemStack[] a){ItemStack[] b=new ItemStack[a.length];for(int i=0;i<a.length;i++)b[i]=a[i]==null?null:a[i].clone();return b;}
    static ItemStack[] move(ItemStack[] contents,ItemStack prototype,int amount,boolean add){
        if(amount<1||amount>2304)throw new IllegalArgumentException("수량 오류");ItemStack[] result=copy(contents);int left=amount;
        for(int i=0;i<result.length&&left>0;i++){ItemStack item=result[i];if(item==null||!item.isSimilar(prototype))continue;int delta=add?Math.min(left,Math.max(0,item.getMaxStackSize()-item.getAmount())):Math.min(left,item.getAmount());item.setAmount(item.getAmount()+(add?delta:-delta));if(item.getAmount()==0)result[i]=null;left-=delta;}
        if(add)for(int i=0;i<result.length&&left>0;i++)if(result[i]==null||result[i].getType().isAir()){ItemStack item=prototype.clone();int delta=Math.min(left,item.getMaxStackSize());item.setAmount(delta);result[i]=item;left-=delta;}
        return left==0?result:null;
    }
}
