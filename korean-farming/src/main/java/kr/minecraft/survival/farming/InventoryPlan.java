package kr.minecraft.survival.farming;

import java.util.*;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import kr.minecraft.survival.economy.domain.ActionEngine;
import kr.minecraft.survival.economy.domain.StateStore;

/** Exact 36-slot snapshots. Simulates every output/container before charging money. */
final class InventoryPlan implements ActionEngine.Change {
    final Player player;final ItemStack[] before,after;boolean changed;
    InventoryPlan(Player player){this.player=player;before=copy(player.getInventory().getStorageContents());after=copy(before);}
    static ItemStack[] copy(ItemStack[] source){ItemStack[] a=new ItemStack[source.length];for(int i=0;i<a.length;i++)a[i]=source[i]==null?null:source[i].clone();return a;}
    void take(Predicate<ItemStack> predicate,int count){for(int i=0;i<after.length&&count>0;i++){ItemStack s=after[i];if(s==null||!predicate.test(s))continue;int n=Math.min(count,s.getAmount());count-=n;if(n==s.getAmount())after[i]=null;else s.setAmount(s.getAmount()-n);}if(count>0)throw new StateStore.Denied("요리에 필요한 재료 또는 거래할 정품 아이템이 부족합니다.");}
    void give(ItemStack item,int count){for(int i=0;i<after.length&&count>0;i++){ItemStack s=after[i];if(s==null||!s.isSimilar(item))continue;int n=Math.min(count,Math.max(0,s.getMaxStackSize()-s.getAmount()));s.setAmount(s.getAmount()+n);count-=n;}
        for(int i=0;i<after.length&&count>0;i++)if(after[i]==null||after[i].getType().isAir()){int n=Math.min(count,item.getMaxStackSize());after[i]=item.clone();after[i].setAmount(n);count-=n;}if(count>0)throw new StateStore.Denied("가방에 수확물·요리·빈 용기를 받을 공간이 부족합니다.");}
    public void apply(){if(!Arrays.equals(before,player.getInventory().getStorageContents()))throw new StateStore.Denied("가방이 바뀌었습니다. 다시 시도하세요.");changed=true;player.getInventory().setStorageContents(copy(after));player.saveData();}
    public void restore(){if(!changed)return;player.getInventory().setStorageContents(copy(before));player.saveData();}
}
