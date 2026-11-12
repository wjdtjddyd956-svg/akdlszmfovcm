package kr.minecraft.survival.civic;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import kr.minecraft.survival.economy.domain.*;

/** Exact storage snapshots; never drops escrow onto the ground. */
final class InventoryChange implements ActionEngine.Change {
    final Player player;final ItemStack[] before,after;boolean changed;
    InventoryChange(Player player){this.player=player;before=copy(player.getInventory().getStorageContents());after=copy(before);}
    static ItemStack[] copy(ItemStack[] source){ItemStack[] a=new ItemStack[source.length];for(int i=0;i<a.length;i++)a[i]=source[i]==null?null:source[i].clone();return a;}
    void takeHand(ItemStack expected,int count){int slot=player.getInventory().getHeldItemSlot();ItemStack s=after[slot];if(s==null||!s.equals(expected)||count<1||count>s.getAmount())throw new StateStore.Denied("손에 든 물건이나 수량이 바뀌었습니다. 다시 등록하세요.");if(count==s.getAmount())after[slot]=null;else s.setAmount(s.getAmount()-count);}
    void give(ItemStack item){int count=item.getAmount();for(int i=0;i<after.length&&count>0;i++){ItemStack s=after[i];if(s==null||!s.isSimilar(item))continue;int n=Math.min(count,Math.max(0,s.getMaxStackSize()-s.getAmount()));s.setAmount(s.getAmount()+n);count-=n;}for(int i=0;i<after.length&&count>0;i++)if(after[i]==null||after[i].getType().isAir()){int n=Math.min(count,item.getMaxStackSize());after[i]=item.clone();after[i].setAmount(n);count-=n;}if(count>0)throw new StateStore.Denied("물건을 받을 가방 공간이 부족합니다.");}
    public void apply(){if(!player.isOnline()||!Arrays.equals(before,player.getInventory().getStorageContents()))throw new StateStore.Denied("가방 상태가 바뀌었습니다. 다시 시도하세요.");changed=true;player.getInventory().setStorageContents(copy(after));player.saveData();}
    public void restore(){if(changed){player.getInventory().setStorageContents(copy(before));player.saveData();}}
}
