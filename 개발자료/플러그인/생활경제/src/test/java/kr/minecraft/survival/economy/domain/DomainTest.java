package kr.minecraft.survival.economy.domain;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DomainTest {
    static int checks;
    static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static void denied(Runnable run){try{run.run();throw new AssertionError("拒否 expected");}catch(StateStore.Denied expected){checks++;}}
    static void invalid(Runnable run){try{run.run();throw new AssertionError("invalid value accepted");}catch(IllegalArgumentException|ArithmeticException expected){checks++;}}
    static UUID player(StateStore store,String name){UUID id=UUID.randomUUID();store.player(id,name);return id;}
    public static void main(String[] args)throws Exception {
        ok(Money.cents(12.34)==1234&&Money.value(1234)==12.34,"exact cents conversion");
        ok(Money.total(199,64)==12736,"integer trade totals");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0.001,1e30})invalid(()->Money.cents(bad));
        invalid(()->Money.total(1,0));
        ok(Rules.level(0)==1&&Rules.level(99)==1&&Rules.level(100)==2&&Rules.level(999)==4&&Rules.level(1000)==5,"level thresholds");
        ok(Rules.nextLevel(0)==100&&Rules.nextLevel(1000)==0,"next level and max level");
        ok(Rules.succeeds(0,9999)&&Rules.succeeds(4,3999)&&!Rules.succeeds(4,4000),"upgrade boundaries use exact integer probabilities");
        invalid(()->Rules.upgrade(5));invalid(()->Rules.succeeds(1,10000));
        ok(Rules.RECIPES.size()==10&&Rules.RECIPES.stream().map(Rules.Recipe::id).distinct().count()==10,"ten distinct recipes");
        for(Rules.Recipe recipe:Rules.RECIPES)ok(recipe.level()>=1&&recipe.level()<=5&&recipe.amount()>0&&recipe.sell()>0&&recipe.ingredients().stream().allMatch(i->i.amount()>0),"valid recipe "+recipe.id());
        Path root=Files.createTempDirectory("tycoon-sql-test-");Path db=root.resolve("state.sqlite");
        UUID alice,bob,pending,legacy;
        try(StateStore store=new StateStore(db)){
            alice=player(store,"Alice");bob=player(store,"Bob");
            ok(store.playerId("aLiCe").orElseThrow().equals(alice),"names resolve case-insensitively");
            ok(store.grantXp(alice,Rules.Job.FARMER,100,"2026-11-11",150)==100,"XP grant");
            ok(store.grantXp(alice,Rules.Job.FARMER,100,"2026-11-11",150)==50,"daily XP cap");
            ok(store.grantXp(alice,Rules.Job.FARMER,1,"2026-11-11",150)==0,"no XP beyond cap");
            ok(store.grantXp(alice,Rules.Job.FARMER,100,"2026-11-12",150)==100&&store.progress(alice,Rules.Job.FARMER).xp()==250,"new-day cap reset retains career XP");
            StateStore.Quota quota=new StateStore.Quota("2026-11-11","wheat",2,3);
            StateStore.Receipt sale=store.reserve(alice,"SELL","sale-a",100,"wheat",quota);
            ok(store.used(alice,quota.day(),quota.product())==2,"quota reserved before payout");
            denied(()->store.reserve(alice,"SELL","sale-b",100,"",null));
            store.complete(sale.id(),()->{});
            denied(()->store.reserve(alice,"SELL","sale-over-cap",100,"",quota));
            ok(store.used(alice,quota.day(),quota.product())==2,"failed cap reservation has no side effect");
            StateStore.Receipt rejected=store.reserve(alice,"SELL","sale-retry",100,"",new StateStore.Quota(quota.day(),quota.product(),1,3));
            store.reject(rejected.id(),"provider refused");ok(store.used(alice,quota.day(),quota.product())==2,"rejected payout releases exactly its reserved quantity");
            denied(()->store.reject(rejected.id(),"double reject"));
            StateStore.Receipt retry=store.reserve(alice,"SELL","sale-retry",100,"",new StateStore.Quota(quota.day(),quota.product(),1,3));
            store.complete(retry.id(),()->{});ok(store.used(alice,quota.day(),quota.product())==3,"safe retry after definite refusal");
            denied(()->store.reserve(alice,"SELL","sale-retry",100,"",null));
            StateStore.Receipt fail=store.reserve(bob,"JOB","job-switch",100,"",null);
            try{store.complete(fail.id(),()->{store.selectJobInside(bob,Rules.Job.MINER,999);throw new StateStore.Denied("simulated callback failure");});throw new AssertionError();}catch(StateStore.Denied expected){checks++;}
            ok(store.job(bob)==null&&store.receipt(fail.id()).state().equals("PENDING"),"failed completion rolls back job and receipt together");
            store.review(fail.id(),"wallet already called");denied(()->store.reserve(bob,"SELL","other",100,"",null));
            store.resolve(fail.id(),false,"operator");ok(!store.unresolved(bob),"review requires explicit admin settlement");

            StateStore.Receipt first=store.reserveLand(bob,"world",-1,-1,3,250000);
            ok(first.cents()==0,"first claim free");store.complete(first.id(),()->store.activateLandInside(first.id()));
            ok(store.land("world",-1,-1).state().equals("ACTIVE"),"activate land only at completion");
            denied(()->store.reserveLand(alice,"world",-1,-1,3,250000));
            denied(()->store.reserveLand(bob,"world",10,10,3,250000));
            StateStore.Receipt expansion=store.reserveLand(bob,"world",0,-1,3,250000);
            ok(expansion.cents()==250000,"adjacent expansion has a cost");store.reject(expansion.id(),"not enough money");
            ok(store.land("world",0,-1)==null,"failed claim removes reservation");
            store.trust(bob,alice,true);ok(store.trusted(bob.toString()).contains(alice.toString()),"trust persisted");
            store.trust(bob,alice,false);ok(store.trusted(bob.toString()).isEmpty(),"trust revoked");denied(()->store.trust(bob,bob,true));
            denied(()->store.abandon(alice,"world",-1,-1));store.abandon(bob,"world",-1,-1);
            ok(store.landCost(bob,250000)==250000,"abandon/reclaim cannot recycle starter subsidy");
            ok(store.harvestOnce("world",1,3,4)&&!store.harvestOnce("world",1,3,4),"natural block position credited once");
            store.markPlaced("world",3,4,5);ok(!store.harvestOnce("world",3,4,5),"placed ore gives no credit");
            ok(store.harvestOnce("other",3,4,5),"natural-position keys include world");
            store.acceptQuest(alice,"2026-11-11","mine_stone",3);denied(()->store.acceptQuest(alice,"2026-11-11","mine_stone",3));
            store.advance(alice,"2026-11-11","MINE","COAL_ORE",100);
            ok(store.quests(alice,"2026-11-11").get(0).progress()==0,"wrong activity cannot progress quest");
            denied(()->store.assertQuestReady(alice,"2026-11-11","mine_stone"));
            store.advance(alice,"2026-11-11","MINE","STONE",100);store.assertQuestReady(alice,"2026-11-11","mine_stone");
            ok(store.quests(alice,"2026-11-11").get(0).progress()==48,"quest progress capped at target");
            StateStore.Receipt reward=store.reserve(alice,"QUEST","quest:2026-11-11:mine_stone",12000,"",null);store.complete(reward.id(),()->{});
            denied(()->store.assertQuestReady(alice,"2026-11-11","mine_stone"));
            store.acceptQuest(alice,"2026-11-12","mine_stone",3);ok(store.quests(alice,"2026-11-12").get(0).progress()==0,"quest reset by calendar date");
            legacy=player(store,"Legacy");store.importDaily(legacy,"2026-11-11","완료");ok(store.paid(legacy,"daily:2026-11-11"),"legacy paid reward imported without money transfer");
            store.importDaily(legacy,"2026-11-11","처리중");ok(!store.unresolved(legacy),"legacy import is one-time");
            UUID legacyPending=player(store,"LegacyPending");store.importDaily(legacyPending,"2026-11-11","처리중");ok(store.unresolved(legacyPending),"legacy uncertain payout remains frozen");
            List<StateStore.Audit> batch=List.of(new StateStore.Audit(100,alice+","+bob,"world",-1,64,0,"CONTAINER",new byte[]{1},new byte[]{2},"mixed"),new StateStore.Audit(100,bob.toString(),"world",40,64,40,"BREAK",null,null,""));
            store.appendAudit(batch);
            var records=store.audit("world",0,-1,0,1,alice.toString(),20);
            ok(records.size()==1&&Arrays.equals((byte[])records.get(0).get("before_data"),new byte[]{1}),"mixed-actor audit and exact before data");
            store.markReverted(((Number)records.get(0).get("id")).longValue());
            ok(((Number)store.audit("world",0,-1,0,1,null,20).get(0).get("reverted")).intValue()==1,"recovery status persisted");
            UUID concurrent=player(store,"Concurrent");ExecutorService pool=Executors.newFixedThreadPool(8);CountDownLatch start=new CountDownLatch(1);AtomicInteger accepted=new AtomicInteger();List<Future<?>> tasks=new ArrayList<>();
            for(int i=0;i<16;i++)tasks.add(pool.submit(()->{try{start.await();store.reserve(concurrent,"DAILY","daily:race",20000,"",null);accepted.incrementAndGet();}catch(StateStore.Denied expected){}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
            start.countDown();for(Future<?> task:tasks)task.get();pool.shutdown();ok(accepted.get()==1,"concurrent requests create one live receipt only");
            pending=player(store,"Pending");store.reserve(pending,"DAILY","daily:2026-11-11",20000,"",null);
            Path snapshot=root.resolve("snapshot.sqlite");store.snapshot(snapshot);ok(StateStore.validSnapshot(snapshot),"online SQLite snapshot is self-contained and passes quick_check");
        }
        try(StateStore reopened=new StateStore(db)){
            ok(reopened.unresolved(pending),"restart does not auto-replay pending payout");
            ok(reopened.receiptLog(pending,1).get(0).get("state").equals("REVIEW"),"orphan pending becomes review on startup");
            ok(reopened.progress(alice,Rules.Job.FARMER).xp()==250,"career progress survives restart");
            ok(reopened.used(alice,"2026-11-11","wheat")==3,"quota survives restart");
            ok(reopened.paid(legacy,"daily:2026-11-11"),"legacy daily receipt survives restart");
        }
        System.out.println("PASS: "+checks+" domain assertions with real SQLite JDBC");
    }
}
