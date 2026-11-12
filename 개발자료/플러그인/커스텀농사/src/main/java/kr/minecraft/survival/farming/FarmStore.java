package kr.minecraft.survival.farming;

import java.util.*;
import com.google.gson.Gson;
import kr.minecraft.survival.economy.domain.*;

/** Farm coordinates and economy receipts share the core's SQLite transaction. */
public final class FarmStore {
    public record Plot(String id,String world,int x,int y,int z,String owner,String crop,long progress,int cycle,String state,String receipt){}
    public record Operation(String plot,String crop,int yield,boolean farmer){}
    private static final Gson JSON=new Gson();
    final StateStore db;
    public FarmStore(StateStore db){this.db=db;
        db.transaction(()->{db.execute("CREATE TABLE IF NOT EXISTS farm_plots(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,owner TEXT NOT NULL,crop TEXT NOT NULL,progress INTEGER NOT NULL DEFAULT 0,cycle INTEGER NOT NULL DEFAULT 0,state TEXT NOT NULL,receipt TEXT,UNIQUE(world,x,y,z))");db.execute("CREATE INDEX IF NOT EXISTS farm_owner ON farm_plots(owner)");return null;});
        // An acknowledged compensation can be interrupted before addon cleanup.
        db.transaction(()->{for(Plot p:all())if(p.receipt!=null&&db.receipt(p.receipt).state().equals("REJECTED"))cancelInside(p.receipt);return null;});
    }
    private Plot read(Map<String,Object> r){return new Plot((String)r.get("id"),(String)r.get("world"),n(r,"x"),n(r,"y"),n(r,"z"),(String)r.get("owner"),(String)r.get("crop"),((Number)r.get("progress")).longValue(),n(r,"cycle"),(String)r.get("state"),(String)r.get("receipt"));}
    static int n(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
    public List<Plot> all(){return db.rows("SELECT * FROM farm_plots").stream().map(this::read).toList();}
    public Plot at(String w,int x,int y,int z){return db.rows("SELECT * FROM farm_plots WHERE world=? AND x=? AND y=? AND z=?",w,x,y,z).stream().findFirst().map(this::read).orElse(null);}
    public Plot get(String id){return db.rows("SELECT * FROM farm_plots WHERE id=?",id).stream().findFirst().map(this::read).orElse(null);}
    public StateStore.Receipt reservePlant(UUID owner,String world,int x,int y,int z,String crop){FarmRules.crop(crop);return db.transaction(()->{
        if(all().size()>=FarmRules.SERVER_LIMIT)throw new StateStore.Denied("서버 농장 개수 한도에 도달했습니다.");
        if(db.rows("SELECT id FROM farm_plots WHERE owner=?",owner).size()>=FarmRules.PLAYER_LIMIT)throw new StateStore.Denied("한 사람당 작물은 최대 32그루입니다.");
        // Two vertical blocks are reserved, including short species, for uniform placement protection.
        if(!db.rows("SELECT id FROM farm_plots WHERE world=? AND x=? AND z=? AND ABS(y-?)<=2",world,x,z,y).isEmpty())throw new StateStore.Denied("이 자리는 다른 작물과 겹칩니다.");
        String id=UUID.randomUUID().toString();var r=db.reserveInside(owner,"FARM_PLANT","farm:"+id+":plant",0,JSON.toJson(new Operation(id,crop,0,false)),null);
        db.execute("INSERT INTO farm_plots(id,world,x,y,z,owner,crop,state,receipt) VALUES(?,?,?,?,?,?,?,'RESERVED',?)",id,world,x,y,z,owner,crop,r.id());return r;
    });}
    public StateStore.Receipt reserveHarvest(UUID actor,String id,int yield,boolean farmer,boolean reclaim){return db.transaction(()->{
        Plot p=get(id);if(p==null||!p.state.equals("ACTIVE"))throw new StateStore.Denied("이미 처리 중이거나 사라진 작물입니다.");FarmRules.Crop c=FarmRules.crop(p.crop);
        if(!reclaim&&p.progress<c.duration())throw new StateStore.Denied("아직 다 자라지 않았습니다.");
        if(yield<c.minYield()||yield>c.maxYield())throw new IllegalArgumentException("수확 수량 오류");
        var r=db.reserveInside(actor,reclaim?"FARM_RECLAIM":"FARM_HARVEST","farm:"+id+":"+p.cycle+":"+(reclaim?"reclaim":"harvest"),0,JSON.toJson(new Operation(id,p.crop,yield,farmer)),null);
        if(db.execute("UPDATE farm_plots SET state='LOCKED',receipt=? WHERE id=? AND state='ACTIVE'",r.id(),id)!=1)throw new StateStore.Denied("작물 상태가 바뀌었습니다.");return r;
    });}
    public static Operation operation(StateStore.Receipt r){return JSON.fromJson(r.payload(),Operation.class);}
    public void completeInside(StateStore.Receipt r,Runnable harvestReward){
        if(r.kind().equals("FARM_PLANT")){if(db.execute("UPDATE farm_plots SET state='ACTIVE',receipt=NULL WHERE receipt=? AND state='RESERVED'",r.id())!=1)throw new StateStore.Denied("모종 예약을 찾지 못했습니다.");return;}
        if(!Set.of("FARM_HARVEST","FARM_RECLAIM").contains(r.kind()))return;
        Operation o=operation(r);Plot p=get(o.plot);if(p==null||!r.id().equals(p.receipt)||!p.state.equals("LOCKED"))throw new StateStore.Denied("수확 예약 상태 오류");
        if(r.kind().equals("FARM_RECLAIM")||!FarmRules.crop(p.crop).perennial())db.execute("DELETE FROM farm_plots WHERE id=?",p.id);
        else db.execute("UPDATE farm_plots SET progress=?,cycle=cycle+1,state='ACTIVE',receipt=NULL WHERE id=?",FarmRules.crop(p.crop).regrowth(),p.id);
        if(r.kind().equals("FARM_HARVEST"))harvestReward.run();
    }
    public void cancelInside(String receipt){db.execute("DELETE FROM farm_plots WHERE receipt=? AND state='RESERVED'",receipt);db.execute("UPDATE farm_plots SET state='ACTIVE',receipt=NULL WHERE receipt=? AND state='LOCKED'",receipt);}
    public void cleanupRejected(String receipt){if(db.receipt(receipt).state().equals("REJECTED"))db.transaction(()->{cancelInside(receipt);return null;});}
    public void grow(Map<String,Long> changes){if(changes.isEmpty())return;db.transaction(()->{for(var e:changes.entrySet())db.execute("UPDATE farm_plots SET progress=? WHERE id=? AND state='ACTIVE'",e.getValue(),e.getKey());return null;});}
}
