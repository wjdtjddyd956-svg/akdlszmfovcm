package kr.minecraft.survival.bridge;
import java.util.*;
public class DisplayProfileTest {
 public static void main(String[] args){
  if(!DisplayProfile.won(4321.25).equals("4,321.25원"))throw new AssertionError();
  if(!DisplayProfile.won(0).equals("0원"))throw new AssertionError();
  for(double n:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1})if(!DisplayProfile.won(n).equals("확인 중"))throw new AssertionError();
  var source=new HashMap<String,String>();source.put("money","23원");var profile=new DisplayProfile(source);source.put("money","0원");
  if(!profile.value("money").equals("23원"))throw new AssertionError("snapshot changed");
  try{profile.values().put("money","0원");throw new AssertionError("mutable");}catch(UnsupportedOperationException expected){}
  if(profile.value("invalid")!=null)throw new AssertionError("unknown key should not invent zero");
  for(String group:List.of("default","resident","admin","unrecognized"))if(DisplayProfile.rank(group).isEmpty())throw new AssertionError();
  System.out.println("PASS 13 display contract assertions: immutable snapshot, invalid balance, precision, missing key, rank fallback");
 }
}
