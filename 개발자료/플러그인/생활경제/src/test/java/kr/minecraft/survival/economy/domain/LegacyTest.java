package kr.minecraft.survival.economy.domain;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LegacyTest {
    static int assertions;
    static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("legacy assertion "+assertions);}
    static void denied(Runnable action){assertions++;try{action.run();}catch(RuntimeException e){return;}throw new AssertionError("expected safe denial");}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("legacy12-test-");UUID id=UUID.randomUUID();String day="2026-11-11";
        List<Legacy12.Mission> missions=List.of(new Legacy12.Mission("DELIVERY","밀 납품",16,4800),new Legacy12.Mission("MINING","광석 채굴",12,7500),new Legacy12.Mission("FISHING","물고기 낚기",6,7500));
        String text="day="+day+"\njob=FARMER\njob.xp.FARMER=75\njob.xp.MINER=8\njob.owed=33\ndaily.job.earned=9\ndaily.sale.DIAMOND=12\ndaily.quest.DELIVERY=accepted\ndaily.quest.MINING=claimed\ndaily.quest.FISHING=accepted\ndaily.quest.FISHING.progress=4\n";
        Path file=dir.resolve(id+".properties");Files.writeString(file,text,StandardCharsets.UTF_8);
        try(StateStore db=new StateStore(dir.resolve("new.db"))){db.player(id,"Existing");Legacy12 legacy=new Legacy12(db,dir);
            check(legacy.importPlayer(id,day,50,missions));check(db.job(id)==Rules.Job.FARMER);check(db.progress(id,Rules.Job.FARMER).level()==2);check(db.progress(id,Rules.Job.FARMER).xp()==200);check(db.owed(id)==3300);check(db.earned(id,day)==900);check(db.used(id,day,"raw:DIAMOND")==12);check(Files.readString(file).equals(text));
            denied(()->db.reserve(id,"SELL","extra",100,"",new StateStore.Quota(day,"raw:DIAMOND",1,12)));
            check(db.quests(id,day).size()==3);check(db.quests(id,day).stream().filter(q->q.id().equals("legacy12_MINING")).findFirst().orElseThrow().paid());check(legacy.mission(id,day,"legacy12_DELIVERY").goal()==16);check(legacy.mission(id,day,"legacy12_DELIVERY").cents()==4800);
            legacy.advance(id,day,"FISHING",1);check(db.quests(id,day).stream().filter(q->q.id().equals("legacy12_FISHING")).findFirst().orElseThrow().progress()==5);
            check(!legacy.importPlayer(id,day,50,missions));check(db.owed(id)==3300);db.accrueJob(id,day,500,1000);check(db.owed(id)==3400);check(db.earned(id,day)==1000);check(db.accrueJob(id,day,500,1000)==0);
            var receipt=db.reserve(id,"JOB_CLAIM","claim",3400,"",null);db.review(receipt.id(),"external result uncertain");db.accrueJob(id,"2026-11-12",200,30000);db.resolve(receipt.id(),true,"admin checked actual money");check(db.owed(id)==200);denied(()->db.resolve(receipt.id(),true,"again"));check(db.owed(id)==200);
            UUID pending=UUID.randomUUID();db.player(pending,"Pending");Files.writeString(dir.resolve(pending+".properties"),"job=MINER\njob.owed=99\npending.id=uncertain\n");denied(()->legacy.importPlayer(pending,day,50,missions));check(db.meta("legacy12:"+pending)==null);check(db.job(pending)==null);check(db.owed(pending)==0);
            UUID future=UUID.randomUUID();db.player(future,"Future");Files.writeString(dir.resolve(future+".properties"),"day=2026-11-12\njob.owed=50\n");denied(()->legacy.importPlayer(future,day,50,missions));check(db.meta("legacy12:"+future)==null);
            UUID broken=UUID.randomUUID();db.player(broken,"Broken");Files.writeString(dir.resolve(broken+".properties"),"job=FARMER\njob.owed=-1\n");denied(()->legacy.importPlayer(broken,day,50,missions));check(db.job(broken)==null);check(db.meta("legacy12:"+broken)==null);
        }
        try(StateStore db=new StateStore(dir.resolve("new.db"))){check(db.owed(id)==200);check(!new Legacy12(db,dir).importPlayer(id,day,50,missions));}
        System.out.println("PASS: "+assertions+" legacy upgrade assertions with real SQLite and unchanged 1.2 files");
    }
}
