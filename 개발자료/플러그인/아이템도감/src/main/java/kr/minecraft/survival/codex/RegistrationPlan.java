package kr.minecraft.survival.codex;
import java.io.*;
import java.security.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

record RegistrationPlan(ItemStack[] before,ItemStack[] after,int slot,int amount,String beforeHash,String afterHash) {
    static ItemStack[] copy(ItemStack[] source){ItemStack[] result=new ItemStack[source.length];for(int i=0;i<source.length;i++)result[i]=source[i]==null?null:source[i].clone();return result;}
    static RegistrationPlan create(ItemStack[] source,Material material){
        return create(source,s->s.isSimilar(new ItemStack(material)));
    }
    static RegistrationPlan create(ItemStack[] source,java.util.function.Predicate<ItemStack> matches){
        ItemStack[] before=copy(source),after=copy(before);
        for(int i=0;i<after.length;i++)if(after[i]!=null&&matches.test(after[i])){
            int amount=after[i].getAmount();if(amount<1)continue;if(amount==1)after[i]=null;else after[i].setAmount(amount-1);return new RegistrationPlan(before,after,i,amount,hash(before),hash(after));
        }throw new CodexStore.Denied("가방에 이름·마법·손상·추가 정보가 없는 기본 아이템 1개가 필요합니다.");
    }
    static String hash(ItemStack[] storage){
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");for(ItemStack item:storage){byte[] bytes=item==null||item.getType().isAir()?new byte[0]:item.serializeAsBytes();digest.update(new byte[]{(byte)(bytes.length>>>24),(byte)(bytes.length>>>16),(byte)(bytes.length>>>8),(byte)bytes.length});digest.update(bytes);}return java.util.HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
