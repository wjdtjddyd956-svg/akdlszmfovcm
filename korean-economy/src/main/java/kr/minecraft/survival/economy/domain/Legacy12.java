package kr.minecraft.survival.economy.domain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDate;
import java.util.*;

/** One-time 1.2 properties import. Source files remain untouched for rollback. */
public final class Legacy12 {
    public record Mission(String key,String label,int goal,long cents){public Mission{if(goal<1||goal>64||cents<0||cents>100_000_000)throw new StateStore.Denied("이전 임무 목표·보상 설정을 확인하세요.");}}
    public static final List<String> KEYS=List.of("DELIVERY","MINING","FISHING");
    private final StateStore store;private final Path directory;
    public Legacy12(StateStore store,Path directory){this.store=store;this.directory=directory;}
    static long number(Properties p,String key){long n=Long.parseLong(p.getProperty(key,"0"));if(n<0)throw new StateStore.Denied("이전 생활 기록의 음수 값: "+key);return n;}
    public boolean importPlayer(UUID id,String day,int oldLevelActions,List<Mission> missions){
        String marker="legacy12:"+id;if(store.meta(marker)!=null)return false;Path file=directory.resolve(id+".properties");Properties p=new Properties();byte[] bytes;
        try{bytes=Files.exists(file)?Files.readAllBytes(file):new byte[0];if(bytes.length>2_000_000)throw new IOException("생활 기록 파일이 너무 큽니다.");try(Reader reader=new StringReader(new String(bytes,StandardCharsets.UTF_8))){p.load(reader);}}
        catch(IOException|IllegalArgumentException e){throw new StateStore.Denied("이전 생활 기록을 읽을 수 없습니다. 원본을 보존하고 운영자에게 문의하세요.");}
        if(p.containsKey("pending.id"))throw new StateStore.Denied("1.2 미결 생활 거래가 있습니다. 1.2에서 /경제거래로 정리한 뒤 업그레이드하세요.");
        String oldDay=p.getProperty("day",day);if(LocalDate.parse(oldDay).isAfter(LocalDate.parse(day)))throw new StateStore.Denied("이전 생활 기록 날짜가 현재보다 미래입니다. 서버 시간을 확인하세요.");
        if(oldLevelActions<1||oldLevelActions>100000)throw new StateStore.Denied("기존 직업 레벨당활동 설정을 확인하세요.");
        store.transaction(()->{
            Rules.Job selected=p.getProperty("job")==null?null:Rules.Job.parse(p.getProperty("job"));
            if(p.containsKey("job")&&selected==null)throw new StateStore.Denied("이전 직업 이름을 읽을 수 없습니다.");
            if(selected!=null)store.selectJobInside(id,selected,oldDay.equals(day)&&p.containsKey("daily.job.changed")?System.currentTimeMillis():0);
            long[] thresholds={0,100,300,600,1000};
            for(Rules.Job job:Rules.Job.values()){
                long xp=number(p,"job.xp."+job.name());long level=xp/oldLevelActions+1;int index=(int)Math.min(4,level-1);
                long mapped=thresholds[index];if(index<4)mapped=Math.addExact(mapped,Math.multiplyExact(xp%oldLevelActions,thresholds[index+1]-thresholds[index])/oldLevelActions);else mapped=Math.addExact(mapped,xp-Math.multiplyExact(4L,oldLevelActions));
                store.execute("INSERT INTO job_progress(uuid,job,xp,day,daily_xp) VALUES(?,?,?, ?,0) ON CONFLICT(uuid,job) DO NOTHING",id,job.name(),mapped,day);
            }
            long owed=Money.cents(number(p,"job.owed")),earned=Money.cents(number(p,"daily.job.earned"));store.meta("job.owed:"+id,Long.toString(owed));
            if(oldDay.equals(day))store.meta("job.earned:"+id+":"+day,Long.toString(earned));
            for(String key:p.stringPropertyNames())if(key.startsWith("daily.sale.")){String product=key.substring("daily.sale.".length());long count=number(p,key);if(count>Integer.MAX_VALUE)throw new StateStore.Denied("이전 매입 수량 오류");store.execute("INSERT INTO daily_use(uuid,day,product,quantity) VALUES(?,?,?,?) ON CONFLICT(uuid,day,product) DO UPDATE SET quantity=MAX(quantity,excluded.quantity)",id,oldDay,"raw:"+product,count);}
            for(Mission mission:missions){
                String status=p.getProperty("daily.quest."+mission.key(),"new");if(status.equals("new"))continue;if(!Set.of("accepted","claimed").contains(status))throw new StateStore.Denied("이전 임무 상태 오류");
                String q="legacy12_"+mission.key();long count=Math.min(mission.goal(),number(p,"daily.quest."+mission.key()+".progress"));store.execute("INSERT OR IGNORE INTO quests(uuid,day,quest,progress) VALUES(?,?,?,?)",id,oldDay,q,status.equals("claimed")?mission.goal():count);
                store.meta("legacyquest:"+id+":"+oldDay+":"+q,mission.goal()+":"+mission.cents()+":"+mission.label());
                if(status.equals("claimed"))store.execute("INSERT OR IGNORE INTO receipts(id,actor,kind,reference,cents,state,created,payload) VALUES(?,?,'QUEST',?,0,'DONE',?,?)",UUID.randomUUID().toString(),id,"quest:"+oldDay+":"+q,System.currentTimeMillis(),"1.2 보상 수령 기록");
            }
            store.meta("legacy12-source:"+id,new String(bytes,StandardCharsets.UTF_8));
            try{store.meta(marker,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}return null;
        });return bytes.length>0;
    }
    public Mission mission(UUID id,String day,String quest){String raw=store.meta("legacyquest:"+id+":"+day+":"+quest);if(raw==null)throw new StateStore.Denied("이전 임무 설정이 없습니다.");String[] parts=raw.split(":",3);return new Mission(quest.substring("legacy12_".length()),parts[2],Integer.parseInt(parts[0]),Long.parseLong(parts[1]));}
    public void advance(UUID id,String day,String key,int amount){String quest="legacy12_"+key;if(store.meta("legacyquest:"+id+":"+day+":"+quest)==null)return;Mission m=mission(id,day,quest);store.execute("UPDATE quests SET progress=MIN(?,progress+?) WHERE uuid=? AND day=? AND quest=?",m.goal(),amount,id,day,quest);}
}
