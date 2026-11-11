package kr.minecraft.survival.economy.domain;

import java.util.List;

public final class Rules {
    private Rules(){}
    public enum Job {
        FARMER("농부"),MINER("광부"),FISHER("낚시꾼");
        public final String label;Job(String label){this.label=label;}
        public static Job parse(String name){for(Job j:values())if(j.label.equals(name)||j.name().equals(name))return j;return null;}
    }
    private static final long[] LEVELS={0,100,300,600,1000};
    public static int level(long xp){if(xp<0)throw new IllegalArgumentException("음수 숙련도");int n=1;for(int i=1;i<LEVELS.length;i++)if(xp>=LEVELS[i])n=i+1;return n;}
    public static long nextLevel(long xp){int level=level(xp);return level==5?0:LEVELS[level];}
    public record Upgrade(int nextLevel,long cost,int chanceBasisPoints){}
    public static Upgrade upgrade(int current){
        if(current<0||current>=5)throw new IllegalArgumentException("강화 가능 단계는 0~4입니다.");
        long[] costs={1000,2500,5000,8000,12000};int[] chances={10000,8500,7000,5500,4000};
        return new Upgrade(current+1,Math.multiplyExact(costs[current],100),chances[current]);
    }
    public static boolean succeeds(int current,int roll){if(roll<0||roll>=10000)throw new IllegalArgumentException("확률값 오류");return roll<upgrade(current).chanceBasisPoints;}
    public record Quest(String id,String name,String kind,String target,int required,long reward,int xp){}
    public static final List<Quest> QUESTS=List.of(
        new Quest("deliver_wheat","마을의 식량","DELIVER","WHEAT",32,12000,10),
        new Quest("deliver_carrot","당근 납품","DELIVER","CARROT",32,9000,10),
        new Quest("mine_stone","채석장 일손","MINE","STONE",48,12000,15),
        new Quest("mine_coal","대장간의 연료","MINE","COAL_ORE",12,16000,15),
        new Quest("fish_cod","오늘의 생선","FISH","COD",8,16000,15),
        new Quest("cook_bread","따뜻한 빵","COOK","mill_bread",6,15000,15)
    );
    public static Quest quest(String id){return QUESTS.stream().filter(q->q.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("알 수 없는 임무"));}
    public record Ingredient(String material,int amount){}
    public record Recipe(String id,String name,Job job,int level,String result,int amount,List<Ingredient> ingredients,long sell,int dailyLimit){}
    private static Ingredient i(String type,int n){return new Ingredient(type,n);}
    public static final List<Recipe> RECIPES=List.of(
        new Recipe("mill_bread","방앗간 밀빵",Job.FARMER,1,"BREAD",1,List.of(i("WHEAT",3)),1200,64),
        new Recipe("carrot_salad","당근 샐러드",Job.FARMER,1,"BEETROOT_SOUP",1,List.of(i("CARROT",3),i("BEETROOT",2),i("BOWL",1)),2400,48),
        new Recipe("roast_potato","숯불 감자",Job.FARMER,2,"BAKED_POTATO",3,List.of(i("POTATO",3),i("COAL",1)),800,96),
        new Recipe("mushroom_soup","숲속 버섯수프",Job.FARMER,2,"MUSHROOM_STEW",1,List.of(i("RED_MUSHROOM",2),i("BROWN_MUSHROOM",2),i("BOWL",1)),2800,48),
        new Recipe("pumpkin_pie","농장 호박파이",Job.FARMER,3,"PUMPKIN_PIE",1,List.of(i("PUMPKIN",1),i("SUGAR",2),i("EGG",1)),3600,48),
        new Recipe("apple_jam","달콤한 사과잼",Job.FARMER,3,"HONEY_BOTTLE",1,List.of(i("APPLE",3),i("SUGAR",2),i("GLASS_BOTTLE",1)),4200,32),
        new Recipe("sweet_cookie","수제 초코쿠키",Job.FARMER,4,"COOKIE",4,List.of(i("WHEAT",2),i("COCOA_BEANS",1),i("SUGAR",1)),1200,128),
        new Recipe("roast_cod","어부의 구운 대구",Job.FISHER,1,"COOKED_COD",2,List.of(i("COD",2),i("COAL",1)),1800,64),
        new Recipe("salmon_meal","연어 정식",Job.FISHER,3,"COOKED_SALMON",2,List.of(i("SALMON",2),i("WHEAT",2),i("CARROT",1)),3200,48),
        new Recipe("golden_meal","황금 농장 만찬",Job.FARMER,5,"GOLDEN_CARROT",2,List.of(i("BEEF",2),i("CARROT",2),i("GOLD_NUGGET",4)),8000,16)
    );
    public static Recipe recipe(String id){return RECIPES.stream().filter(r->r.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("알 수 없는 요리"));}
}
