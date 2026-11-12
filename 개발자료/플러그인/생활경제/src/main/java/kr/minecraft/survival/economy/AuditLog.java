package kr.minecraft.survival.economy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import kr.minecraft.survival.economy.domain.StateStore;

/** Limited, explicit rollback: only ordinary blocks, never containers or tile entities. */
final class AuditLog implements Listener {
    final Tycoon t;private final Map<UUID,Preview> previews=new HashMap<>();
    record Preview(String token,long expires,List<Map<String,Object>> rows){}
    record Capture(Inventory inventory,byte[] before,Set<String> actors){}
    private final Map<Inventory,Capture> captures=new IdentityHashMap<>();
    AuditLog(Tycoon t){this.t=t;}
    static byte[] block(BlockState state){return state.getBlockData().getAsString().getBytes(StandardCharsets.UTF_8);}
    static byte[] inventory(Inventory inv){
        try{ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);out.writeInt(inv.getSize());for(ItemStack item:inv.getContents()){byte[] bytes=item==null||item.getType().isAir()?new byte[0]:item.serializeAsBytes();out.writeInt(bytes.length);out.write(bytes);}return buffer.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}
    }
    void append(String actor,Location l,String kind,byte[] before,byte[] after,String details){if(Arrays.equals(before,after))return;t.store.appendAudit(List.of(new StateStore.Audit(System.currentTimeMillis(),actor,l.getWorld().getUID().toString(),l.getBlockX(),l.getBlockY(),l.getBlockZ(),kind,before,after,details)));}
    void capture(Player p,BlockState before){
        Location l=before.getLocation();byte[] bytes=block(before);boolean tile=before instanceof TileState;
        t.plugin.getServer().getScheduler().runTask(t.plugin,()->{try{BlockState after=l.getBlock().getState();append(p.getUniqueId().toString(),l,tile||after instanceof TileState?"TILE":"BLOCK",bytes,block(after),"블록 변경");}catch(RuntimeException e){t.plugin.getLogger().log(java.util.logging.Level.SEVERE,"블록 기록 실패",e);}});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){capture(e.getPlayer(),e.getBlock().getState());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(e instanceof BlockMultiPlaceEvent multi){for(BlockState b:multi.getReplacedBlockStates())capture(e.getPlayer(),b);}else capture(e.getPlayer(),e.getBlockReplacedState());}
    void captureInventory(Inventory inv,String actor){List<Location> locations=t.lands.locations(inv);if(locations.isEmpty())return;Location location=locations.getFirst();Capture current=captures.get(inv);if(current!=null){current.actors.add(actor);return;}Capture c=new Capture(inv,inventory(inv),new TreeSet<>(Set.of(actor)));captures.put(inv,c);
        t.plugin.getServer().getScheduler().runTask(t.plugin,()->{captures.remove(inv);try{append(String.join(",",c.actors),location,"CONTAINER",c.before,inventory(inv),"상자 변경 · 같은 틱 참여자 "+c.actors.size()+"명 (개별 아이템 귀속 아님)");}catch(RuntimeException e){t.plugin.getLogger().log(java.util.logging.Level.SEVERE,"상자 기록 실패",e);}});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void click(InventoryClickEvent e){captureInventory(e.getView().getTopInventory(),e.getWhoClicked().getUniqueId().toString());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent e){captureInventory(e.getView().getTopInventory(),e.getWhoClicked().getUniqueId().toString());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(InventoryMoveItemEvent e){captureInventory(e.getSource(),"HOPPER");captureInventory(e.getDestination(),"HOPPER");}
    void command(CommandSender sender,String[] a){
        if(!sender.hasPermission("koreaneconomy.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}
        if(!(sender instanceof Player p)){sender.sendMessage("조회할 위치에서 게임 안 명령어를 사용하세요.");return;}
        try{
            if(a.length==2&&a[0].equals("복구")){Preview preview=previews.remove(p.getUniqueId());if(preview==null||preview.expires<System.currentTimeMillis()||!preview.token.equals(a[1]))throw new StateStore.Denied("유효한 복구 미리보기가 없습니다. 다시 조회하세요.");int changed=0,skipped=0;
                for(Map<String,Object> row:preview.rows){if(!"BLOCK".equals(row.get("kind"))||((Number)row.get("reverted")).intValue()!=0){skipped++;continue;}World world=t.plugin.getServer().getWorld(UUID.fromString((String)row.get("world")));if(world==null){skipped++;continue;}int x=n(row,"x"),y=n(row,"y"),z=n(row,"z");if(!world.isChunkLoaded(x>>4,z>>4)){skipped++;continue;}Block b=world.getBlockAt(x,y,z);byte[] current=block(b.getState()),expected=(byte[])row.get("after_data");if(b.getState() instanceof TileState||!Arrays.equals(current,expected)){skipped++;continue;}
                    var target=Bukkit.createBlockData(new String((byte[])row.get("before_data"),StandardCharsets.UTF_8));
                    // Avoid restoring a removed inventory or other block entity, even if the old classification was inaccurate.
                    if(isTileMaterial(target.getMaterial())){skipped++;continue;}b.setBlockData(target,false);t.store.markReverted(((Number)row.get("id")).longValue());append(p.getUniqueId().toString(),b.getLocation(),"ADMIN",current,block(b.getState()),"복구 원본 기록 "+row.get("id"));changed++;
                }p.sendMessage("§a복구 "+changed+"개 · 충돌/상자/타일/미로딩/이미복구 제외 "+skipped+"개");return;
            }
            if(a.length<3||!Set.of("조회","미리보기").contains(a[0])){p.sendMessage("§e/생활기록 조회|미리보기 반경 분 [닉네임]");p.sendMessage("§7최대 반경 64, 1440분, 최근 500건 · 상자 내용 자동 복구 제외");return;}
            int radius=Integer.parseInt(a[1]),minutes=Integer.parseInt(a[2]);if(radius<0||radius>64||minutes<1||minutes>1440)throw new StateStore.Denied("반경 0~64, 시간 1~1440분을 입력하세요.");String actor=a.length>=4?t.store.playerId(a[3]).orElseThrow(()->new StateStore.Denied("접속 이력이 없는 이름입니다.")).toString():null;
            List<Map<String,Object>> rows=t.store.audit(p.getWorld().getUID().toString(),System.currentTimeMillis()-minutes*60_000L,p.getLocation().getBlockX(),p.getLocation().getBlockZ(),radius,actor,500);
            p.sendMessage("§6[생활 기록] 최근 "+rows.size()+"건 (상자 기록은 참여자 단위)");for(var r:rows.stream().limit(12).toList())p.sendMessage("§7#"+r.get("id")+" "+r.get("kind")+" "+r.get("x")+","+r.get("y")+","+r.get("z")+" | "+r.get("actor"));
            if(a[0].equals("미리보기")){String token=UUID.randomUUID().toString().substring(0,8);previews.put(p.getUniqueId(),new Preview(token,System.currentTimeMillis()+60_000,rows));p.sendMessage("§e일반 블록만 역순 복구합니다. 현재 상태가 기록 이후 바뀐 블록은 건너뜁니다.");p.sendMessage("§e60초 안에 /생활기록 복구 "+token+" · 상자·표지판 등 타일은 복구하지 않습니다.");}
        }catch(RuntimeException e){sender.sendMessage("§c"+e.getMessage());}
    }
    static int n(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
    static boolean isTileMaterial(Material m){String n=m.name();return n.contains("CHEST")||n.contains("SHULKER")||n.contains("SIGN")||n.contains("BANNER")||n.contains("HEAD")||n.contains("SKULL")||Set.of("BARREL","FURNACE","BLAST_FURNACE","SMOKER","HOPPER","DROPPER","DISPENSER","BREWING_STAND","BEACON","LECTERN","JUKEBOX","CHISELED_BOOKSHELF","DECORATED_POT","CRAFTER","CAMPFIRE","SOUL_CAMPFIRE","BEE_NEST","BEEHIVE","SPAWNER","TRIAL_SPAWNER","VAULT","STRUCTURE_BLOCK","JIGSAW","COMMAND_BLOCK","CHAIN_COMMAND_BLOCK","REPEATING_COMMAND_BLOCK","ENDER_CHEST","CONDUIT","END_GATEWAY","SUSPICIOUS_SAND","SUSPICIOUS_GRAVEL").contains(n);}
    void close(){previews.clear();captures.clear();}
}
