package kr.minecraft.survival.farming;

import java.util.*;
import org.bukkit.Material;

/** Fixed, bounded economy rules; prices are whole won, journals store cents. */
public final class FarmRules {
    public record Crop(String id,String name,Material material,int seedPrice,int sell,int minutes,int minYield,int maxYield,boolean perennial,float height){
        public long duration(){return minutes*60_000L;}
        public int stage(long progress){return progress>=duration()?3:progress>=duration()*6/10?2:progress>=duration()/4?1:0;}
        public long regrowth(){return perennial?duration()/4:0;}
    }
    public record Ingredient(String id,int count){}
    public record Dish(String id,String name,Material material,int level,int sell,int food,float saturation,List<Ingredient> ingredients,Material containerReturn){}
    public static final List<Crop> CROPS=List.of(
        new Crop("strawberry","딸기",Material.APPLE,40,8,8,2,4,true,.65f),
        new Crop("tomato","토마토",Material.BEETROOT,35,7,10,2,4,true,1.3f),
        new Crop("corn","옥수수",Material.BAKED_POTATO,30,10,15,2,4,false,1.7f),
        new Crop("rice","쌀",Material.PAPER,20,5,10,3,5,false,1.1f),
        new Crop("cucumber","오이",Material.DRIED_KELP,30,6,9,2,4,true,1.4f));
    static Ingredient i(String id,int n){return new Ingredient(id,n);}
    public static final List<Dish> DISHES=List.of(
        new Dish("strawberry_jam","수제 딸기잼",Material.HONEY_BOTTLE,2,40,5,3,List.of(i("strawberry",3),i("SUGAR",2),i("GLASS_BOTTLE",1)),Material.GLASS_BOTTLE),
        new Dish("tomato_pasta","토마토 파스타",Material.RABBIT_STEW,2,45,10,8,List.of(i("tomato",3),i("WHEAT",2),i("EGG",1),i("BOWL",1)),Material.BOWL),
        new Dish("corn_soup","옥수수 크림수프",Material.MUSHROOM_STEW,3,42,8,6,List.of(i("corn",2),i("MILK_BUCKET",1),i("BOWL",1)),Material.BOWL),
        new Dish("rice_bowl","도시락 밥",Material.RABBIT_STEW,1,25,8,5,List.of(i("rice",3),i("CARROT",1),i("BOWL",1)),Material.BOWL),
        new Dish("cucumber_salad","오이 토마토 샐러드",Material.BEETROOT_SOUP,1,25,6,4,List.of(i("cucumber",2),i("tomato",1),i("BOWL",1)),Material.BOWL));
    public static Crop crop(String id){return CROPS.stream().filter(c->c.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("알 수 없는 작물"));}
    public static Dish dish(String id){return DISHES.stream().filter(d->d.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("알 수 없는 요리"));}
    public static boolean custom(String id){return CROPS.stream().anyMatch(c->id.equals(c.id)||id.equals(c.id+"_seed"))||DISHES.stream().anyMatch(d->d.id.equals(id));}
    public static final int PLAYER_LIMIT=32,SERVER_LIMIT=256,DAILY_SELL=64;
    private FarmRules(){}
}
