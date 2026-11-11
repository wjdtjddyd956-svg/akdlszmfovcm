package kr.minecraft.survival.codex;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Integer units: 10,000 units are one attribute point; percentages use the same scale. */
final class Catalog {
    enum Stat {
        MOVE("이동 속도",300,true),MINING("채굴 속도",600,true),HEALTH("최대 체력",20000,false),DAMAGE("공격력",5000,false),ARMOR("방어력",10000,false),ATTACK_SPEED("공격 속도",200,true),KNOCKBACK("넉백 저항",200,true),LUCK("행운",5000,false);
        final String label;final int cap;final boolean percent;
        Stat(String label,int cap,boolean percent){this.label=label;this.cap=cap;this.percent=percent;}
        String format(int units){return "+"+BigDecimal.valueOf(units,percent?2:4).stripTrailingZeros().toPlainString()+(this==KNOCKBACK?"%p":percent?"%":"");}
    }
    record Entry(String id,String material,String name,int weight,String category){String rarity(){return switch(weight){case 1->"일반";case 2->"고급";case 4->"희귀";case 8->"전설";default->throw new IllegalStateException();};}}
    record Category(String id,String name,String icon,Stat stat,List<Entry> entries){}
    record Achievement(String id,String name,String description,Map<Stat,Integer> reward){}
    final List<Category> categories;final Map<String,Entry> entries;final Map<String,Category> byCategory;
    final List<Achievement> achievements;
    Catalog(InputStream resource)throws IOException {
        if(resource==null)throw new IOException("내장 도감 목록이 없습니다.");Map<String,List<Entry>> grouped=new LinkedHashMap<>();Map<String,String[]> definitions=new LinkedHashMap<>();Map<String,Entry> all=new LinkedHashMap<>();Set<String> materials=new HashSet<>();
        try(resource){for(String line:new String(resource.readAllBytes(),StandardCharsets.UTF_8).split("\\R")){
            if(line.isBlank())continue;String[] c=line.split("\t",8);if(c.length!=8||!c[0].matches("[a-z0-9_]+")||!c[4].matches("[a-z0-9_]+"))throw new IOException("도감 목록 형식 오류");int weight=Integer.parseInt(c[7]);if(!Set.of(1,2,4,8).contains(weight))throw new IOException("희귀도 오류");
            Entry entry=new Entry(c[4],c[5],c[6],weight,c[0]);if(all.put(entry.id,entry)!=null||!materials.add(entry.material))throw new IOException("중복 도감 아이템");grouped.computeIfAbsent(c[0],k->new ArrayList<>()).add(entry);definitions.putIfAbsent(c[0],c);
        }}
        List<Category> cats=new ArrayList<>();Set<Stat> assigned=EnumSet.noneOf(Stat.class);
        for(var group:grouped.entrySet()){String[] c=definitions.get(group.getKey());Stat stat=Stat.valueOf(c[3]);if(!assigned.add(stat)||group.getValue().size()!=10)throw new IOException("분야는 서로 다른 효과와 10종이 필요합니다.");cats.add(new Category(c[0],c[1],c[2],stat,List.copyOf(group.getValue())));}
        if(cats.size()!=8||all.size()!=80)throw new IOException("8개 분야·80종 도감이 필요합니다.");categories=List.copyOf(cats);entries=Collections.unmodifiableMap(all);Map<String,Category> index=new HashMap<>();for(Category c:cats)index.put(c.id,c);byCategory=Map.copyOf(index);
        List<Achievement> awards=new ArrayList<>();int step=0;for(int count:new int[]{10,30,50,80}){int n=++step;Map<Stat,Integer> rewards=new EnumMap<>(Stat.class);for(Stat s:Stat.values())rewards.put(s,s.cap*n/40-s.cap*(n-1)/40);awards.add(new Achievement("total_"+count,"수집가 "+count+"종",count+"종 영구 등록",Map.copyOf(rewards)));}
        for(Category c:cats){int granted=c.entries.stream().mapToInt(e->entryUnits(e)).sum();awards.add(new Achievement("category_"+c.id,c.name+" 완성",c.name+" 10종 모두 등록",Map.of(c.stat,c.stat.cap-c.stat.cap/10-granted)));}achievements=List.copyOf(awards);
    }
    Entry entry(String id){Entry e=entries.get(id);if(e==null)throw new IllegalArgumentException("도감에 없는 아이템입니다.");return e;}
    int entryUnits(Entry e){Category c=byCategory.get(e.category);int weights=c.entries.stream().mapToInt(Entry::weight).sum();return (c.stat.cap*8/10/weights)*e.weight;}
    Set<String> eligible(Set<String> registered){Set<String> result=new LinkedHashSet<>();long count=registered.stream().filter(entries::containsKey).count();for(int n:new int[]{10,30,50,80})if(count>=n)result.add("total_"+n);for(Category c:categories)if(c.entries.stream().allMatch(e->registered.contains(e.id)))result.add("category_"+c.id);return Set.copyOf(result);}
    Map<Stat,Integer> bonuses(Set<String> registered){Map<Stat,Integer> result=new EnumMap<>(Stat.class);for(Stat s:Stat.values())result.put(s,0);for(String id:registered){Entry e=entries.get(id);if(e!=null)result.merge(byCategory.get(e.category).stat,entryUnits(e),Math::addExact);}Set<String> unlocked=eligible(registered);for(Achievement a:achievements)if(unlocked.contains(a.id))for(var reward:a.reward.entrySet())result.merge(reward.getKey(),reward.getValue(),Math::addExact);for(Stat s:Stat.values())result.put(s,Math.min(s.cap,result.get(s)));return Map.copyOf(result);}
}
