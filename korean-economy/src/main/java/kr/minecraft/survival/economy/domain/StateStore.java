package kr.minecraft.survival.economy.domain;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Synchronized SQLite connection; immutable receipts precede external wallet calls. */
public final class StateStore implements AutoCloseable {
    public static class Denied extends RuntimeException {public Denied(String message){super(message);}}
    public static class StorageFailure extends RuntimeException {StorageFailure(Throwable cause){super("서버 데이터 저장 중 오류가 발생했습니다.",cause);}}
    public record Quota(String day,String product,int amount,int limit){}
    public record Receipt(String id,String actor,String kind,String reference,long cents,String state,String payload){}
    public record Progress(long xp,int dailyXp,String day){public int level(){return Rules.level(xp);}}
    public record Land(String id,String world,int x,int z,String owner,String state,String receipt){}
    public record QuestProgress(String id,int progress,boolean paid){}
    private final Connection connection;
    private final Path file;
    private boolean inTransaction;

    public StateStore(Path file) {
        try {
            this.file=file.toAbsolutePath();Files.createDirectories(this.file.getParent());
            Class.forName("org.sqlite.JDBC");connection=DriverManager.getConnection("jdbc:sqlite:"+this.file);
            execute("PRAGMA foreign_keys=ON");execute("PRAGMA busy_timeout=5000");execute("PRAGMA journal_mode=WAL");execute("PRAGMA synchronous=FULL");
            int version=((Number)rows("PRAGMA user_version").get(0).values().iterator().next()).intValue();
            if(version>1)throw new IllegalStateException("더 새로운 저장소입니다. 플러그인 버전을 내리지 마세요.");
            transaction(()->{
                execute("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,job TEXT,job_changed INTEGER NOT NULL DEFAULT 0,starter_used INTEGER NOT NULL DEFAULT 0)");
                execute("CREATE UNIQUE INDEX IF NOT EXISTS player_names ON players(name COLLATE NOCASE)");
                execute("CREATE TABLE IF NOT EXISTS job_progress(uuid TEXT NOT NULL,job TEXT NOT NULL,xp INTEGER NOT NULL DEFAULT 0,day TEXT NOT NULL DEFAULT '',daily_xp INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(uuid,job))");
                execute("CREATE TABLE IF NOT EXISTS daily_use(uuid TEXT NOT NULL,day TEXT NOT NULL,product TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>=0),PRIMARY KEY(uuid,day,product))");
                execute("CREATE TABLE IF NOT EXISTS receipts(id TEXT PRIMARY KEY,actor TEXT NOT NULL,kind TEXT NOT NULL,reference TEXT NOT NULL,cents INTEGER NOT NULL CHECK(cents>=0),state TEXT NOT NULL,created INTEGER NOT NULL,payload TEXT NOT NULL,quota_day TEXT,quota_product TEXT,quota_amount INTEGER NOT NULL DEFAULT 0,note TEXT NOT NULL DEFAULT '')");
                execute("CREATE UNIQUE INDEX IF NOT EXISTS receipt_once ON receipts(actor,reference) WHERE state IN ('PENDING','DONE','REVIEW')");
                execute("CREATE INDEX IF NOT EXISTS receipt_player ON receipts(actor,state,created)");
                execute("CREATE TABLE IF NOT EXISTS lands(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,z INTEGER NOT NULL,owner TEXT NOT NULL,state TEXT NOT NULL,receipt TEXT,UNIQUE(world,x,z))");
                execute("CREATE TABLE IF NOT EXISTS trusts(owner TEXT NOT NULL,member TEXT NOT NULL,PRIMARY KEY(owner,member))");
                execute("CREATE TABLE IF NOT EXISTS placed(world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,PRIMARY KEY(world,x,y,z))");
                execute("CREATE TABLE IF NOT EXISTS harvested(world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,PRIMARY KEY(world,x,y,z))");
                execute("CREATE TABLE IF NOT EXISTS quests(uuid TEXT NOT NULL,day TEXT NOT NULL,quest TEXT NOT NULL,progress INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(uuid,day,quest))");
                execute("CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL,actor TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,kind TEXT NOT NULL,before_data BLOB,after_data BLOB,details TEXT NOT NULL DEFAULT '',reverted INTEGER NOT NULL DEFAULT 0)");
                execute("CREATE INDEX IF NOT EXISTS audit_area ON audit(world,at,x,z)");
                execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
                execute("PRAGMA user_version=1");return null;
            });
            execute("UPDATE receipts SET state='REVIEW',note='처리 도중 서버 종료: 잔액과 아이템을 운영자가 확인해야 합니다.' WHERE state='PENDING'");
        } catch(Exception e){throw new StorageFailure(e);}
    }
    public Path file(){return file;}
    public synchronized int execute(String sql,Object... args) {
        try(PreparedStatement s=connection.prepareStatement(sql)){bind(s,args);boolean result=s.execute();return result?0:s.getUpdateCount();}
        catch(SQLException e){throw new StorageFailure(e);}
    }
    public synchronized List<Map<String,Object>> rows(String sql,Object... args) {
        try(PreparedStatement s=connection.prepareStatement(sql)){
            bind(s,args);try(ResultSet r=s.executeQuery()){
                List<Map<String,Object>> list=new ArrayList<>();ResultSetMetaData meta=r.getMetaData();
                while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),r.getObject(i));list.add(row);}return list;
            }
        }catch(SQLException e){throw new StorageFailure(e);}
    }
    private static void bind(PreparedStatement s,Object[] args)throws SQLException{for(int i=0;i<args.length;i++)s.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);}
    public synchronized <T>T transaction(Supplier<T> work) {
        if(inTransaction)throw new IllegalStateException("중첩 저장 거래는 허용하지 않습니다.");
        try {
            connection.setAutoCommit(false);inTransaction=true;
            try {T result=work.get();connection.commit();return result;}
            catch(SQLException|RuntimeException e){try{connection.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
            finally{inTransaction=false;connection.setAutoCommit(true);}
        }catch(SQLException e){throw new StorageFailure(e);}
    }
    private static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    public void player(UUID id,String name){
        // Rename is possible; release the old name mapping before assigning its current UUID.
        execute("UPDATE players SET name=uuid WHERE name=? COLLATE NOCASE AND uuid<>?",name,id);
        execute("INSERT INTO players(uuid,name) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name",id,name);
    }
    public Optional<UUID> playerId(String name){return rows("SELECT uuid FROM players WHERE name=? COLLATE NOCASE",name).stream().findFirst().map(r->UUID.fromString((String)r.get("uuid")));}
    public String playerName(String uuid){return rows("SELECT name FROM players WHERE uuid=?",uuid).stream().findFirst().map(r->(String)r.get("name")).orElse(uuid.substring(0,8));}
    public Rules.Job job(UUID id){return rows("SELECT job FROM players WHERE uuid=?",id).stream().findFirst().map(r->r.get("job")).map(v->Rules.Job.valueOf(v.toString())).orElse(null);}
    public Progress progress(UUID id,Rules.Job job){
        return rows("SELECT xp,day,daily_xp FROM job_progress WHERE uuid=? AND job=?",id,job.name()).stream().findFirst()
            .map(r->new Progress(n(r,"xp"),(int)n(r,"daily_xp"),(String)r.get("day"))).orElse(new Progress(0,0,""));
    }
    public int grantXp(UUID id,Rules.Job job,int amount,String day,int cap){
        if(amount<1||amount>1000||cap<0)throw new IllegalArgumentException("숙련도 값 오류");
        return transaction(()->{
            Progress old=progress(id,job);int used=old.day.equals(day)?old.dailyXp:0;int grant=Math.max(0,Math.min(amount,cap-used));
            execute("INSERT INTO job_progress(uuid,job,xp,day,daily_xp) VALUES(?,?,?,?,?) ON CONFLICT(uuid,job) DO UPDATE SET xp=excluded.xp,day=excluded.day,daily_xp=excluded.daily_xp",id,job.name(),Math.addExact(old.xp,grant),day,used+grant);return grant;
        });
    }
    public long owed(UUID id){String raw=meta("job.owed:"+id);return raw==null?0:Long.parseLong(raw);}
    public long earned(UUID id,String day){String raw=meta("job.earned:"+id+":"+day);return raw==null?0:Long.parseLong(raw);}
    public long accrueJob(UUID id,String day,long amount,long cap){if(amount<0||cap<0)throw new IllegalArgumentException("직업 적립 금액 오류");return transaction(()->{long used=earned(id,day),grant=Math.max(0,Math.min(amount,cap-used));meta("job.earned:"+id+":"+day,Long.toString(Math.addExact(used,grant)));meta("job.owed:"+id,Long.toString(Math.addExact(owed(id),grant)));return grant;});}
    public void consumeOwedInside(UUID id,long amount){requireTransaction();long owed=owed(id);if(amount<0||owed<amount)throw new Denied("직업 적립금 기록이 바뀌었습니다.");meta("job.owed:"+id,Long.toString(owed-amount));}
    public long changedAt(UUID id){return rows("SELECT job_changed FROM players WHERE uuid=?",id).stream().findFirst().map(r->n(r,"job_changed")).orElse(0L);}
    public void selectJobInside(UUID id,Rules.Job job,long now){requireTransaction();if(execute("UPDATE players SET job=?,job_changed=? WHERE uuid=?",job.name(),now,id)!=1)throw new Denied("유저 등록 정보를 찾지 못했습니다.");}
    private void requireTransaction(){if(!inTransaction)throw new IllegalStateException("거래 내부에서 호출해야 합니다.");}
    public int used(UUID id,String day,String product){return rows("SELECT quantity FROM daily_use WHERE uuid=? AND day=? AND product=?",id,day,product).stream().findFirst().map(r->(int)n(r,"quantity")).orElse(0);}
    public boolean unresolved(UUID id){return !rows("SELECT id FROM receipts WHERE actor=? AND state IN ('PENDING','REVIEW') LIMIT 1",id).isEmpty();}
    public boolean paid(UUID id,String reference){return !rows("SELECT id FROM receipts WHERE actor=? AND reference=? AND state='DONE'",id,reference).isEmpty();}
    private Receipt receipt(Map<String,Object> r){return new Receipt((String)r.get("id"),(String)r.get("actor"),(String)r.get("kind"),(String)r.get("reference"),n(r,"cents"),(String)r.get("state"),(String)r.get("payload"));}
    public Receipt receipt(String id){return rows("SELECT * FROM receipts WHERE id=?",id).stream().findFirst().map(this::receipt).orElseThrow(()->new Denied("거래를 찾을 수 없습니다."));}
    public Receipt reserve(UUID id,String kind,String reference,long cents,String payload,Quota quota){return transaction(()->reserveInside(id,kind,reference,cents,payload,quota));}
    private Receipt reserveInside(UUID id,String kind,String reference,long cents,String payload,Quota quota){
        if(cents<0)throw new IllegalArgumentException("음수 거래 금액");
        if(unresolved(id))throw new Denied("확인 중인 거래가 있습니다. 운영자에게 /경제기록 확인을 요청하세요.");
        if(!rows("SELECT id FROM receipts WHERE actor=? AND reference=? AND state IN ('PENDING','DONE','REVIEW')",id,reference).isEmpty())throw new Denied("이미 처리했거나 확인 중인 보상입니다.");
        if(quota!=null){
            int used=used(id,quota.day,quota.product);
            if(quota.amount<1||quota.limit<0||quota.amount>quota.limit-used)throw new Denied("오늘 매입 한도가 부족합니다. 남은 수량: "+Math.max(0,quota.limit-used));
            execute("INSERT INTO daily_use(uuid,day,product,quantity) VALUES(?,?,?,?) ON CONFLICT(uuid,day,product) DO UPDATE SET quantity=quantity+excluded.quantity",id,quota.day,quota.product,quota.amount);
        }
        String rid=UUID.randomUUID().toString();
        execute("INSERT INTO receipts(id,actor,kind,reference,cents,state,created,payload,quota_day,quota_product,quota_amount) VALUES(?,?,?,?,?,'PENDING',?,?,?,?,?)",rid,id,kind,reference,cents,System.currentTimeMillis(),payload,quota==null?null:quota.day,quota==null?null:quota.product,quota==null?0:quota.amount);
        return receipt(rid);
    }
    public void complete(String id,Runnable databaseEffect){transaction(()->{
        if(!receipt(id).state.equals("PENDING"))throw new Denied("거래 상태가 바뀌었습니다.");
        databaseEffect.run();execute("UPDATE receipts SET state='DONE' WHERE id=?",id);return null;
    });}
    public void reject(String id,String note){transaction(()->{rejectInside(id,note);return null;});}
    private void rejectInside(String id,String note){
        List<Map<String,Object>> rs=rows("SELECT * FROM receipts WHERE id=?",id);if(rs.isEmpty())throw new Denied("거래 없음");Map<String,Object> row=rs.get(0);
        if(!Set.of("PENDING","REVIEW").contains(row.get("state")))throw new Denied("이미 정리한 거래입니다.");
        if(n(row,"quota_amount")>0)execute("UPDATE daily_use SET quantity=MAX(0,quantity-?) WHERE uuid=? AND day=? AND product=?",n(row,"quota_amount"),row.get("actor"),row.get("quota_day"),row.get("quota_product"));
        execute("DELETE FROM lands WHERE receipt=? AND state='RESERVED'",id);
        execute("UPDATE receipts SET state='REJECTED',note=? WHERE id=?",note,id);
    }
    public void review(String id,String note){execute("UPDATE receipts SET state='REVIEW',note=? WHERE id=? AND state='PENDING'",note,id);}
    public void resolve(String id,boolean completed,String admin){transaction(()->{
        if(!receipt(id).state.equals("REVIEW"))throw new Denied("확인 대기 상태의 거래만 정리할 수 있습니다.");
        if(completed){Receipt record=receipt(id);if(record.kind.equals("JOB_CLAIM"))consumeOwedInside(UUID.fromString(record.actor),record.cents);if(record.kind.equals("JOB"))selectJobInside(UUID.fromString(record.actor),Rules.Job.valueOf(record.payload),System.currentTimeMillis());activateLandInside(id);execute("UPDATE receipts SET state='DONE',note=? WHERE id=?","운영자 확인 완료: "+admin,id);}
        else rejectInside(id,"운영자 취소 확인: "+admin);return null;
    });}
    public List<Map<String,Object>> receiptLog(UUID actor,int limit){return rows("SELECT * FROM receipts WHERE actor=? ORDER BY created DESC LIMIT ?",actor,Math.min(100,Math.max(1,limit)));}
    public List<Map<String,Object>> unresolvedLog(){return rows("SELECT * FROM receipts WHERE state='REVIEW' ORDER BY created LIMIT 100");}
    public String receiptId(String prefix){
        if(!prefix.matches("[a-fA-F0-9-]{6,36}"))throw new Denied("거래 번호는 표시된 영문·숫자 번호를 입력하세요.");
        List<Map<String,Object>> list=rows("SELECT id FROM receipts WHERE id LIKE ? LIMIT 2",prefix+"%");
        if(list.size()!=1)throw new Denied("거래 번호가 없거나 여러 개입니다. 더 긴 번호를 입력하세요.");return (String)list.get(0).get("id");
    }
    public List<Land> lands(){return rows("SELECT * FROM lands").stream().map(this::land).toList();}
    private Land land(Map<String,Object> r){return new Land((String)r.get("id"),(String)r.get("world"),(int)n(r,"x"),(int)n(r,"z"),(String)r.get("owner"),(String)r.get("state"),(String)r.get("receipt"));}
    public List<Land> lands(UUID owner){return rows("SELECT * FROM lands WHERE owner=?",owner).stream().map(this::land).toList();}
    public Land land(String world,int x,int z){return rows("SELECT * FROM lands WHERE world=? AND x=? AND z=?",world,x,z).stream().findFirst().map(this::land).orElse(null);}
    public Set<String> trusted(String owner){Set<String> set=new HashSet<>();for(Map<String,Object> r:rows("SELECT member FROM trusts WHERE owner=?",owner))set.add((String)r.get("member"));return set;}
    public void trust(UUID owner,UUID member,boolean allow){
        if(owner.equals(member))throw new Denied("본인은 이미 소유자입니다.");
        if(allow)execute("INSERT OR IGNORE INTO trusts(owner,member) VALUES(?,?)",owner,member);else execute("DELETE FROM trusts WHERE owner=? AND member=?",owner,member);
    }
    public long landCost(UUID owner,long normalCost){return rows("SELECT starter_used FROM players WHERE uuid=?",owner).stream().findFirst().map(r->n(r,"starter_used")==0&&lands(owner).isEmpty()?0:normalCost).orElse(normalCost);}
    public Receipt reserveLand(UUID owner,String world,int x,int z,int maxClaims,long cost){return transaction(()->{
        List<Land> existing=lands(owner);
        if(existing.size()>=maxClaims)throw new Denied("개인 땅 최대 개수에 도달했습니다.");
        if(land(world,x,z)!=null)throw new Denied("이미 보호되거나 처리 중인 땅입니다.");
        if(!existing.isEmpty()&&existing.stream().noneMatch(l->l.world.equals(world)&&Math.abs((long)l.x-x)+Math.abs((long)l.z-z)==1))throw new Denied("기존 내 땅과 한 변이 맞닿은 구역에서 확장하세요.");
        long price=landCost(owner,cost);
        Receipt receipt=reserveInside(owner,"LAND","land:"+UUID.randomUUID(),price,world+":"+x+":"+z,null);
        execute("INSERT INTO lands(id,world,x,z,owner,state,receipt) VALUES(?,?,?,?,?,'RESERVED',?)",UUID.randomUUID().toString(),world,x,z,owner,receipt.id);return receipt;
    });}
    public void activateLandInside(String receipt){
        requireTransaction();List<Map<String,Object>> rows=rows("SELECT owner FROM lands WHERE receipt=? AND state='RESERVED'",receipt);
        if(!rows.isEmpty()){execute("UPDATE players SET starter_used=1 WHERE uuid=?",rows.get(0).get("owner"));execute("UPDATE lands SET state='ACTIVE' WHERE receipt=?",receipt);}
    }
    public void abandon(UUID owner,String world,int x,int z){transaction(()->{
        Land land=land(world,x,z);if(land==null||!land.owner.equals(owner.toString()))throw new Denied("현재 구역은 내 땅이 아닙니다.");
        if(!land.state.equals("ACTIVE"))throw new Denied("처리 중인 땅은 운영자 확인이 필요합니다.");
        execute("DELETE FROM lands WHERE id=?",land.id);return null;
    });}
    public boolean placed(String world,int x,int y,int z){return !rows("SELECT 1 FROM placed WHERE world=? AND x=? AND y=? AND z=?",world,x,y,z).isEmpty();}
    public void markPlaced(String world,int x,int y,int z){execute("INSERT OR IGNORE INTO placed(world,x,y,z) VALUES(?,?,?,?)",world,x,y,z);}
    public boolean harvestOnce(String world,int x,int y,int z){return transaction(()->{
        if(placed(world,x,y,z))return false;
        return execute("INSERT OR IGNORE INTO harvested(world,x,y,z) VALUES(?,?,?,?)",world,x,y,z)==1;
    });}
    public void acceptQuest(UUID id,String day,String quest,int maximum){Rules.quest(quest);transaction(()->{
        if(!rows("SELECT 1 FROM quests WHERE uuid=? AND day=? AND quest=?",id,day,quest).isEmpty())throw new Denied("이미 받은 임무입니다.");
        if(rows("SELECT quest FROM quests WHERE uuid=? AND day=?",id,day).size()>=maximum)throw new Denied("오늘 받을 수 있는 임무 수를 초과했습니다.");
        execute("INSERT INTO quests(uuid,day,quest,progress) VALUES(?,?,?,0)",id,day,quest);return null;
    });}
    public List<QuestProgress> quests(UUID id,String day){return rows("SELECT quest,progress FROM quests WHERE uuid=? AND day=?",id,day).stream().map(r->{String q=(String)r.get("quest");return new QuestProgress(q,(int)n(r,"progress"),paid(id,"quest:"+day+":"+q));}).toList();}
    public void advance(UUID id,String day,String kind,String target,int amount){if(amount<1||amount>2304)throw new IllegalArgumentException("진행 수량 오류");transaction(()->{advanceInside(id,day,kind,target,amount);return null;});}
    public void advanceInside(UUID id,String day,String kind,String target,int amount){
        requireTransaction();
        for(Rules.Quest q:Rules.QUESTS)if(q.kind().equals(kind)&&q.target().equals(target))execute("UPDATE quests SET progress=MIN(?,progress+?) WHERE uuid=? AND day=? AND quest=?",q.required(),amount,id,day,q.id());
    }
    public void assertQuestReady(UUID id,String day,String quest){
        Rules.Quest q=Rules.quest(quest);QuestProgress state=quests(id,day).stream().filter(p->p.id.equals(quest)).findFirst().orElseThrow(()->new Denied("먼저 임무를 수락하세요."));
        if(state.paid)throw new Denied("이미 받은 임무 보상입니다.");
        if(!q.kind().equals("DELIVER")&&state.progress<q.required())throw new Denied("아직 임무를 완료하지 않았습니다.");
    }
    public record Audit(long at,String actor,String world,int x,int y,int z,String kind,byte[] before,byte[] after,String details){}
    public void appendAudit(List<Audit> batch){if(batch.isEmpty())return;transaction(()->{
        for(Audit a:batch)execute("INSERT INTO audit(at,actor,world,x,y,z,kind,before_data,after_data,details) VALUES(?,?,?,?,?,?,?,?,?,?)",a.at,a.actor,a.world,a.x,a.y,a.z,a.kind,a.before,a.after,a.details);return null;
    });}
    public List<Map<String,Object>> audit(String world,long since,int x,int z,int radius,String actor,int limit){
        if(radius<0||radius>128||limit<1||limit>2000)throw new Denied("조회 범위는 0~128블록, 기록은 최대 2000개입니다.");
        String filter=actor==null?"":" AND instr(','||actor||',',','||?||',')>0";
        List<Object> params=new ArrayList<>(List.of(world,since,(long)x-radius,(long)x+radius,(long)z-radius,(long)z+radius));if(actor!=null)params.add(actor);params.add(limit);
        return rows("SELECT * FROM audit WHERE world=? AND at>=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?"+filter+" ORDER BY id DESC LIMIT ?",params.toArray());
    }
    public void markReverted(long id){execute("UPDATE audit SET reverted=1 WHERE id=? AND reverted=0",id);}
    public void snapshot(Path destination){
        if(Files.exists(destination))throw new Denied("백업 대상 파일이 이미 있습니다.");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file);PreparedStatement s=c.prepareStatement("VACUUM INTO ?")){
            try(Statement options=c.createStatement()){options.execute("PRAGMA busy_timeout=5000");}
            s.setString(1,destination.toAbsolutePath().toString());s.execute();
        }catch(SQLException e){throw new StorageFailure(e);}
    }
    public String meta(String key){return rows("SELECT value FROM meta WHERE key=?",key).stream().findFirst().map(r->(String)r.get("value")).orElse(null);}
    public void meta(String key,String value){execute("INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",key,value);}
    public void importDaily(UUID player,String day,String legacyState){transaction(()->{
        String key="legacy-daily:"+player;if(meta(key)!=null)return null;
        if(day!=null){
            LocalDate.parse(day);
            String state=switch(legacyState){case "완료"->"DONE";case "처리중"->"REVIEW";case "실패"->"REJECTED";default->throw new Denied("이전 일일 보상 기록이 올바르지 않습니다.");};
            execute("INSERT OR IGNORE INTO receipts(id,actor,kind,reference,cents,state,created,payload,note) VALUES(?,?,'DAILY',?,0,?,?,?,'이전 버전 보상 기록 가져오기')",UUID.randomUUID().toString(),player,"daily:"+day,state,System.currentTimeMillis(),"legacy="+legacyState);
        }
        meta(key,"1");return null;
    });}
    public static boolean validSnapshot(Path file){
        Properties properties=new Properties();properties.setProperty("open_mode","1");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath(),properties);Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA quick_check")){return r.next()&&"ok".equals(r.getString(1));}
        catch(SQLException e){throw new StorageFailure(e);}
    }
    @Override public synchronized void close(){try{connection.close();}catch(SQLException e){throw new StorageFailure(e);}}
}
