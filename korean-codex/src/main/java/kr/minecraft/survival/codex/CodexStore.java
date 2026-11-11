package kr.minecraft.survival.codex;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

final class CodexStore implements AutoCloseable {
    static class Denied extends RuntimeException {Denied(String message){super(message);}}
    static class Failure extends RuntimeException {Failure(Throwable cause){super("도감 저장소 확인이 필요합니다.",cause);}}
    record Receipt(String id,String player,String entry,String state,int slot,int amount,String beforeHash,String afterHash,String stage){}
    private final Connection db;final Path path;private boolean inTransaction;
    CodexStore(Path file){
        Connection opened=null;
        try{path=file.toAbsolutePath();Files.createDirectories(path.getParent());Class.forName("org.sqlite.JDBC");opened=DriverManager.getConnection("jdbc:sqlite:"+path);db=opened;
            execute("PRAGMA busy_timeout=5000");execute("PRAGMA journal_mode=WAL");execute("PRAGMA synchronous=FULL");execute("PRAGMA foreign_keys=ON");int version=((Number)rows("PRAGMA user_version").getFirst().values().iterator().next()).intValue();if(version>2)throw new Denied("더 새로운 도감 DB입니다. 버전을 내리지 마세요.");
            transaction(()->{execute("CREATE TABLE IF NOT EXISTS players(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1)");execute("CREATE TABLE IF NOT EXISTS receipts(id TEXT PRIMARY KEY,player TEXT NOT NULL,entry TEXT NOT NULL,state TEXT NOT NULL,created INTEGER NOT NULL,slot INTEGER NOT NULL,amount INTEGER NOT NULL,before_hash TEXT NOT NULL,after_hash TEXT NOT NULL,stage TEXT NOT NULL DEFAULT 'RESERVED',note TEXT NOT NULL DEFAULT '')");execute("CREATE TABLE IF NOT EXISTS registrations(player TEXT NOT NULL,entry TEXT NOT NULL,state TEXT NOT NULL,receipt TEXT NOT NULL,PRIMARY KEY(player,entry))");execute("CREATE TABLE IF NOT EXISTS achievements(player TEXT NOT NULL,award TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(player,award))");execute("CREATE TABLE IF NOT EXISTS marker_reviews(player TEXT NOT NULL,marker TEXT NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',PRIMARY KEY(player,marker))");execute("PRAGMA user_version=2");return null;});
            transaction(()->{execute("UPDATE receipts SET state='REVIEW',note='처리 중 종료: 아이템과 저장 표식을 확인하세요.' WHERE state='PENDING'");execute("UPDATE registrations SET state='REVIEW' WHERE state='PENDING'");return null;});
        }catch(Exception e){if(opened!=null)try{opened.close();}catch(SQLException failure){e.addSuppressed(failure);}throw new Failure(e);}
    }
    synchronized int execute(String sql,Object... args){try(PreparedStatement s=db.prepareStatement(sql)){bind(s,args);return s.execute()?0:s.getUpdateCount();}catch(SQLException e){throw new Failure(e);}}
    synchronized List<Map<String,Object>> rows(String sql,Object... args){try(PreparedStatement s=db.prepareStatement(sql)){bind(s,args);try(ResultSet r=s.executeQuery()){List<Map<String,Object>> list=new ArrayList<>();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));list.add(row);}return list;}}catch(SQLException e){throw new Failure(e);}}
    static void bind(PreparedStatement s,Object[] args)throws SQLException {for(int i=0;i<args.length;i++)s.setObject(i+1,args[i] instanceof UUID?args[i].toString():args[i]);}
    synchronized <T>T transaction(Supplier<T> action){if(inTransaction)throw new IllegalStateException("중첩 도감 저장 거래");try{db.setAutoCommit(false);inTransaction=true;try{T result=action.get();db.commit();return result;}catch(RuntimeException|SQLException e){try{db.rollback();}catch(SQLException failure){e.addSuppressed(failure);}throw e;}finally{inTransaction=false;db.setAutoCommit(true);}}catch(SQLException e){throw new Failure(e);}}
    void player(UUID id,String name){execute("UPDATE players SET name=uuid WHERE name=? COLLATE NOCASE AND uuid<>?",name,id);execute("INSERT INTO players(uuid,name) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name",id,name);}
    boolean enabled(UUID id){return rows("SELECT enabled FROM players WHERE uuid=?",id).stream().findFirst().map(r->((Number)r.get("enabled")).intValue()!=0).orElse(true);}
    void enabled(UUID id,boolean value){execute("UPDATE players SET enabled=? WHERE uuid=?",value?1:0,id);}
    Set<String> registered(UUID id){Set<String> result=new HashSet<>();for(var r:rows("SELECT entry FROM registrations WHERE player=? AND state='DONE'",id))result.add((String)r.get("entry"));return Set.copyOf(result);}
    Set<String> awards(UUID id){Set<String> result=new HashSet<>();for(var r:rows("SELECT award FROM achievements WHERE player=?",id))result.add((String)r.get("award"));return Set.copyOf(result);}
    boolean markerHeld(UUID id){return !rows("SELECT 1 FROM marker_reviews WHERE player=? AND state='REVIEW' LIMIT 1",id).isEmpty();}
    void markerReview(UUID id,String marker){execute("INSERT OR IGNORE INTO marker_reviews(player,marker,created,state) VALUES(?,?,?,'REVIEW')",id,marker,System.currentTimeMillis());}
    boolean markerReleased(UUID id,String marker){return !rows("SELECT 1 FROM marker_reviews WHERE player=? AND marker=? AND state='RELEASED'",id,marker).isEmpty();}
    List<Map<String,Object>> markerReviews(){return rows("SELECT * FROM marker_reviews WHERE state='REVIEW' ORDER BY created LIMIT 100");}
    void releaseMarkers(UUID id,String admin){if(execute("UPDATE marker_reviews SET state='RELEASED',note=? WHERE player=? AND state='REVIEW'","운영자 표식 확인: "+admin,id)==0)throw new Denied("이 계정에 알 수 없는 저장 표식의 확인 대기 기록이 없습니다.");}
    Receipt reserve(UUID id,String entry,int slot,int amount,String before,String after){return transaction(()->{
        if(markerHeld(id)||!rows("SELECT id FROM receipts WHERE player=? AND state IN ('PENDING','REVIEW')",id).isEmpty())throw new Denied("확인 대기 중인 도감 등록이 있습니다. 운영자에게 /도감관리 거래 확인을 요청하세요.");
        if(!rows("SELECT entry FROM registrations WHERE player=? AND entry=?",id,entry).isEmpty())throw new Denied("이미 등록한 아이템입니다.");
        String rid=UUID.randomUUID().toString();execute("INSERT INTO receipts(id,player,entry,state,created,slot,amount,before_hash,after_hash) VALUES(?,?,?,'PENDING',?,?,?,?,?)",rid,id,entry,System.currentTimeMillis(),slot,amount,before,after);execute("INSERT INTO registrations(player,entry,state,receipt) VALUES(?,?,'PENDING',?)",id,entry,rid);return receipt(rid);
    });}
    Receipt receipt(String id){var r=rows("SELECT * FROM receipts WHERE id=?",id).stream().findFirst().orElseThrow(()->new Denied("등록 거래가 없습니다."));return new Receipt((String)r.get("id"),(String)r.get("player"),(String)r.get("entry"),(String)r.get("state"),((Number)r.get("slot")).intValue(),((Number)r.get("amount")).intValue(),(String)r.get("before_hash"),(String)r.get("after_hash"),(String)r.get("stage"));}
    void stage(String id,String stage){execute("UPDATE receipts SET stage=? WHERE id=? AND state='PENDING'",stage,id);}
    Set<String> finish(String id,Catalog catalog,boolean reviewed){return transaction(()->{Receipt receipt=receipt(id);if(!(receipt.state.equals("PENDING")||reviewed&&receipt.state.equals("REVIEW")))throw new Denied("이미 정리한 등록 거래입니다.");UUID player=UUID.fromString(receipt.player);if(execute("UPDATE registrations SET state='DONE' WHERE receipt=?",id)!=1)throw new Denied("등록 원본 기록이 없습니다.");execute("UPDATE receipts SET state='DONE' WHERE id=?",id);Set<String> newly=new HashSet<>();for(String award:catalog.eligible(registered(player)))if(execute("INSERT OR IGNORE INTO achievements(player,award,created) VALUES(?,?,?)",player,award,System.currentTimeMillis())==1)newly.add(award);return Set.copyOf(newly);});}
    void review(String id,String note){transaction(()->{execute("UPDATE receipts SET state='REVIEW',note=? WHERE id=? AND state='PENDING'",note,id);execute("UPDATE registrations SET state='REVIEW' WHERE receipt=? AND state='PENDING'",id);return null;});}
    void cancel(String id,String admin){transaction(()->{Receipt r=receipt(id);if(!Set.of("PENDING","REVIEW").contains(r.state))throw new Denied("이미 정리한 거래입니다.");execute("DELETE FROM registrations WHERE receipt=? AND state<>'DONE'",id);execute("UPDATE receipts SET state='CANCELLED',note=? WHERE id=?",admin,id);return null;});}
    String receiptId(String prefix){if(!prefix.matches("[a-fA-F0-9-]{6,36}"))throw new Denied("표시된 등록 거래 번호를 입력하세요.");var ids=rows("SELECT id FROM receipts WHERE id LIKE ? LIMIT 2",prefix+"%");if(ids.size()!=1)throw new Denied("번호가 없거나 여러 개입니다. 더 긴 번호를 입력하세요.");return (String)ids.getFirst().get("id");}
    List<Map<String,Object>> unresolved(){return rows("SELECT * FROM receipts WHERE state IN ('PENDING','REVIEW') ORDER BY created LIMIT 100");}
    List<Map<String,Object>> log(UUID id){return rows("SELECT * FROM receipts WHERE player=? ORDER BY created DESC LIMIT 20",id);}
    Optional<UUID> playerId(String name){return rows("SELECT uuid FROM players WHERE name=? COLLATE NOCASE LIMIT 1",name).stream().findFirst().map(r->UUID.fromString((String)r.get("uuid")));}
    void snapshot(Path destination){if(Files.exists(destination))throw new Denied("백업 파일이 이미 있습니다.");try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+path);PreparedStatement s=c.prepareStatement("VACUUM INTO ?")){s.setString(1,destination.toAbsolutePath().toString());s.execute();}catch(SQLException e){throw new Failure(e);}}
    @Override public synchronized void close(){try{db.close();}catch(SQLException e){throw new Failure(e);}}
}
