package kr.minecraft.survival.farming;

import java.util.*;
import java.util.logging.Level;
import java.net.URI;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minecraft.survival.economy.KoreanEconomyPlugin;
import kr.minecraft.survival.economy.domain.*;
import static kr.minecraft.survival.economy.domain.ActionEngine.*;

public class KoreanFarmingPlugin extends JavaPlugin implements Listener {
    static final UUID PACK_ID=UUID.fromString("734c28bf-cf02-4f7d-b9cc-d68b04c97a02");
    KoreanEconomyPlugin core;StateStore db;ActionEngine engine;FarmStore plots;FarmItems items;FarmProtection protection;FarmRenderer renderer;FarmMenus menus;
    final Set<UUID> ready=new HashSet<>();private final Map<UUID,Long> cooldown=new HashMap<>();boolean healthy=true;
    @Override public void onEnable(){
        saveDefaultConfig();try{
            var dependency=getServer().getPluginManager().getPlugin("KoreanEconomy");if(!(dependency instanceof KoreanEconomyPlugin economy)||!economy.isEnabled())throw new IllegalStateException("KoreanEconomy 2.0.2 이상이 필요합니다.");core=economy;
            db=core.getEconomyStateStore();engine=core.getEconomyActionEngine();plots=new FarmStore(db);items=new FarmItems(this);protection=new FarmProtection(this);renderer=new FarmRenderer(this);menus=new FarmMenus(this);
            for(Listener l:List.of(this,protection,renderer,menus))getServer().getPluginManager().registerEvents(l,this);
            for(World w:getServer().getWorlds())for(Chunk c:w.getLoadedChunks())renderer.stale(c);renderer.all();
            getServer().getScheduler().runTaskTimer(this,this::tick,200,200);
            for(Player p:getServer().getOnlinePlayers())autoRequest(p);
            getLogger().info("커스텀 농장 5종 · 씨앗/NPC/농부/요리/도감 연결 완료: /농사");
        }catch(RuntimeException|LinkageError e){getLogger().log(Level.SEVERE,"농사 시작 실패: 기존 DB는 보존합니다. 경제 2.0.2와 함께 설치하세요.",e);getServer().getPluginManager().disablePlugin(this);}
    }
    @Override public void onDisable(){if(menus!=null)menus.close();if(renderer!=null)renderer.close();ready.clear();cooldown.clear();/* shared core DB is owned by KoreanEconomy */}
    /** Stable codex API. Never trusts item display name alone. */
    public ItemStack item(String id){return items.item(id);}
    public ItemStack previewItem(String id,Player viewer){ItemStack s=item(id);if(!ready.contains(viewer.getUniqueId())){var m=s.getItemMeta();m.setItemModel(null);s.setItemMeta(m);}return s;}
    public boolean isItem(ItemStack stack,String id){return items.matches(stack,id);}
    void safe(Player player,Runnable work){if(!player.hasPermission("koreanfarming.use")){player.sendMessage("§c농사를 사용할 권한이 없습니다.");return;}try{if(!healthy)throw new StateStore.Denied("농장 저장소 확인이 필요합니다. 운영자에게 문의하세요.");core.prepareFarmingPlayer(player);work.run();}catch(StateStore.Denied|IllegalArgumentException e){player.sendMessage("§c"+e.getMessage());}catch(RuntimeException e){healthy=false;getLogger().log(Level.SEVERE,"농장 저장/처리 확인 필요",e);player.sendMessage("§c농장 처리를 중지했습니다. 운영자가 저장소를 확인해야 합니다.");}}
    void survival(Player p){if(p.getGameMode()!=GameMode.SURVIVAL)throw new StateStore.Denied("생존 모드에서 농사를 하세요.");}
    void pack(Player p){if(!ready.contains(p.getUniqueId()))throw new StateStore.Denied("먼저 /농사 팩받기 를 사용하세요. 직접 설치했다면 /농사 팩확인 을 입력하세요.");}
    Location location(FarmStore.Plot plot){World w=getServer().getWorld(UUID.fromString(plot.world()));if(w==null)throw new StateStore.Denied("작물의 월드를 찾지 못했습니다.");return new Location(w,plot.x(),plot.y(),plot.z());}
    FarmStore.Plot touching(Block b){String w=b.getWorld().getUID().toString();for(int dy:new int[]{0,1,-1}){var plot=plots.at(w,b.getX(),b.getY()+dy,b.getZ());if(plot!=null)return plot;}return null;}
    void nearby(Player p,FarmStore.Plot plot){Location l=location(plot);if(l.getWorld()!=p.getWorld()||l.clone().add(.5,.5,.5).distanceSquared(p.getEyeLocation())>36)throw new StateStore.Denied("작물에서 6블록 안으로 가까이 오세요.");}
    boolean growing(FarmStore.Plot plot){World w=getServer().getWorld(UUID.fromString(plot.world()));if(w==null||!w.isChunkLoaded(plot.x()>>4,plot.z()>>4))return false;Block b=w.getBlockAt(plot.x(),plot.y(),plot.z());return b.getType().isAir()&&b.getRelative(BlockFace.UP).getType().isAir()&&b.getLightLevel()>=9&&b.getRelative(BlockFace.DOWN).getBlockData() instanceof Farmland f&&f.getMoisture()>0;}
    void tick(){if(!healthy)return;try{Map<String,Long> changes=new HashMap<>();for(var plot:plots.all())if(plot.state().equals("ACTIVE")&&growing(plot)){long duration=FarmRules.crop(plot.crop()).duration();if(plot.progress()<duration)changes.put(plot.id(),Math.min(duration,plot.progress()+10_000));}plots.grow(changes);renderer.all();}catch(RuntimeException e){healthy=false;getLogger().log(Level.SEVERE,"농장 성장 저장 실패: 성장을 중지합니다.",e);}}
    void result(Player p,Outcome out,String done){if(out.status()==Status.REJECTED)plots.cleanupRejected(out.receipt());if(out.status()==Status.DONE)p.sendMessage("§a"+done);else p.sendMessage("§c"+out.message());renderer.all();}
    void plant(Player player,Block soil,String cropId){survival(player);pack(player);var crop=FarmRules.crop(cropId);Block air=soil.getRelative(BlockFace.UP);
        if(!(soil.getBlockData() instanceof Farmland f)||f.getMoisture()==0||!air.getType().isAir()||!air.getRelative(BlockFace.UP).getType().isAir()||!air.getWorld().getWorldBorder().isInside(air.getLocation()))throw new StateStore.Denied("물에 젖은 경작지 위에 빈 공간 2칸이 필요합니다.");
        if(player.getWorld()!=soil.getWorld()||player.getEyeLocation().distanceSquared(air.getLocation().add(.5,.5,.5))>36)throw new StateStore.Denied("경작지에 더 가까이 오세요.");
        protection.build(player,soil.getLocation());protection.build(player,air.getLocation());protection.build(player,air.getRelative(BlockFace.UP).getLocation());
        InventoryPlan plan=new InventoryPlan(player);plan.take(s->isItem(s,cropId+"_seed"),1);
        if(!engine.healthy())throw new StateStore.Denied("경제 저장소 확인이 필요합니다.");
        var receipt=plots.reservePlant(player.getUniqueId(),air.getWorld().getUID().toString(),air.getX(),air.getY(),air.getZ(),crop.id());
        result(player,engine.reserved(receipt,Direction.NONE,plan,()->plots.completeInside(receipt,()->{})),crop.name()+" 씨앗을 심었습니다. 성장 시간 "+crop.minutes()+"분");
    }
    void harvest(Player player,String plotId,boolean reclaim){survival(player);pack(player);FarmStore.Plot plot=plots.get(plotId);if(plot==null)throw new StateStore.Denied("작물이 사라졌습니다.");nearby(player,plot);protection.access(player,plot);
        if(!plot.state().equals("ACTIVE"))throw new StateStore.Denied("작물 거래를 확인 중입니다. 운영자에게 문의하세요.");var crop=FarmRules.crop(plot.crop());if(!reclaim&&crop.stage(plot.progress())!=3){player.sendMessage("§e"+crop.name()+" 성장 "+Math.min(100,plot.progress()*100/crop.duration())+"% · 물과 빛이 필요합니다.");return;}
        if(!reclaim&&!growing(plot))throw new StateStore.Denied("물·빛·경작지 상태를 먼저 복구하세요.");
        int quantity=java.util.concurrent.ThreadLocalRandom.current().nextInt(crop.minYield(),crop.maxYield()+1);InventoryPlan plan=new InventoryPlan(player);
        if(reclaim)plan.give(item(crop.id()+"_seed"),1);else{plan.give(item(crop.id()),quantity);if(!crop.perennial())plan.give(item(crop.id()+"_seed"),1);}
        if(!engine.healthy())throw new StateStore.Denied("경제 저장소 확인이 필요합니다.");
        var receipt=plots.reserveHarvest(player.getUniqueId(),plot.id(),quantity,core.isFarmer(player),reclaim);
        result(player,engine.reserved(receipt,Direction.NONE,plan,()->plots.completeInside(receipt,()->core.recordFarmHarvestInside(player.getUniqueId(),FarmStore.operation(receipt).farmer()))),reclaim?crop.name()+"을 치우고 씨앗 1개를 돌려받았습니다.":crop.name()+" "+quantity+"개 수확! 농부 보상은 /직업 에서 받으세요.");
    }
    void shop(Player player,Entity npc,String id,int amount,boolean buying){survival(player);if(!Set.of(1,16,64).contains(amount))throw new IllegalArgumentException("수량 오류");menus.npc(player,npc);
        InventoryPlan plan=new InventoryPlan(player);int price;String name;
        if(buying){var crop=FarmRules.crop(id);id=crop.id()+"_seed";price=crop.seedPrice();name=crop.name()+" 씨앗";plan.give(item(id),amount);}
        else {String product=id;plan.take(s->isItem(s,product),amount);if(FarmRules.CROPS.stream().anyMatch(c->c.id().equals(product))){var c=FarmRules.crop(id);price=c.sell();name=c.name();}else{var d=FarmRules.dish(id);price=d.sell();name=d.name();}}
        result(player,engine.execute(player.getUniqueId(),buying?"FARM_BUY":"FARM_SELL","farm:shop:"+UUID.randomUUID(),Math.multiplyExact((long)price*100,amount),buying?Direction.DEBIT:Direction.CREDIT,id+":"+amount,buying?null:new StateStore.Quota(core.farmingDay(),"farm:"+id,amount,FarmRules.DAILY_SELL),plan,()->{}),name+" "+amount+"개 "+(buying?"구매":"판매")+" 완료 · "+price*amount+"원");
    }
    void cook(Player player,String id){survival(player);var dish=FarmRules.dish(id);if(core.farmingLevel(player)<dish.level())throw new StateStore.Denied("농부 직업 "+dish.level()+"레벨이 필요합니다. /직업 에서 확인하세요.");
        InventoryPlan plan=new InventoryPlan(player);for(var ingredient:dish.ingredients()){String material=ingredient.id();if(FarmRules.custom(material))plan.take(s->isItem(s,material),ingredient.count());else{ItemStack plain=new ItemStack(Material.valueOf(material));plan.take(s->s.isSimilar(plain),ingredient.count());}}
        plan.give(item(dish.id()),1);if(dish.id().equals("corn_soup"))plan.give(new ItemStack(Material.BUCKET),1);
        result(player,engine.execute(player.getUniqueId(),"FARM_COOK","farm:cook:"+UUID.randomUUID(),0,Direction.NONE,id,null,plan,()->{}),dish.name()+" 완성! (먹기 또는 요리 상인에게 판매)");
    }
    boolean throttle(Player p){long now=System.nanoTime(),last=cooldown.getOrDefault(p.getUniqueId(),0L);if(now-last<300_000_000L)return false;cooldown.put(p.getUniqueId(),now);return true;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;if(e.getHand()!=EquipmentSlot.HAND){String off=items.id(e.getItem());if(off!=null&&off.endsWith("_seed"))e.setCancelled(true);return;}Block block=e.getClickedBlock();if(block==null)return;String id=items.id(e.getItem());var plot=touching(block);
        if(plot!=null){e.setCancelled(true);if(throttle(e.getPlayer()))getServer().getScheduler().runTask(this,()->safe(e.getPlayer(),()->{if(e.getPlayer().isOnline())harvest(e.getPlayer(),plot.id(),false);}));return;}
        if(id==null||!id.endsWith("_seed"))return;e.setCancelled(true);if(!items.matches(e.getItem(),id))return;if(throttle(e.getPlayer()))getServer().getScheduler().runTask(this,()->{if(e.getPlayer().isOnline())safe(e.getPlayer(),()->plant(e.getPlayer(),block,id.substring(0,id.length()-5)));});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entity(PlayerInteractEntityEvent e){String id=renderer.plot(e.getRightClicked());if(id==null)return;e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND||!throttle(e.getPlayer()))return;getServer().getScheduler().runTask(this,()->{if(e.getPlayer().isOnline())safe(e.getPlayer(),()->harvest(e.getPlayer(),id,false));});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityAt(PlayerInteractAtEntityEvent e){entity(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e){String id=items.id(e.getItem());if(id==null||FarmRules.DISHES.stream().noneMatch(d->d.id().equals(id)))return;e.setCancelled(true);Player player=e.getPlayer();if(!isItem(e.getItem(),id)||!throttle(player))return;getServer().getScheduler().runTask(this,()->{if(!player.isOnline())return;safe(player,()->{
        survival(player);var dish=FarmRules.dish(id);InventoryPlan plan=new InventoryPlan(player);plan.take(s->isItem(s,id),1);plan.give(new ItemStack(dish.containerReturn()),1);var out=engine.execute(player.getUniqueId(),"FARM_EAT","farm:eat:"+UUID.randomUUID(),0,Direction.NONE,id,null,plan,()->{});result(player,out,dish.name()+"을 먹었습니다.");if(out.status()==Status.DONE){player.setFoodLevel(Math.min(20,player.getFoodLevel()+dish.food()));player.setSaturation(Math.min(player.getFoodLevel(),player.getSaturation()+dish.saturation()));}
    });});}
    void autoRequest(Player p){if(getConfig().getBoolean("리소스팩.접속시-요청",true))getServer().getScheduler().runTaskLater(this,()->{if(p.isOnline())safe(p,()->request(p));},60);}
    void request(Player p){String url=getConfig().getString("리소스팩.주소","");String hash=getConfig().getString("리소스팩.SHA1","");URI uri=URI.create(url);if(!uri.getScheme().equals("https")||uri.getHost()==null||!hash.matches("[a-fA-F0-9]{40}"))throw new StateStore.Denied("운영자가 리소스팩의 HTTPS 주소와 SHA1을 설정해야 합니다.");p.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(hash),"커스텀 농작물 5종과 도시 아이콘을 적용할까요?",false);p.sendMessage("§e농장 리소스팩 요청을 보냈습니다. 다운로드·적용을 허용하세요.");}
    @EventHandler public void status(PlayerResourcePackStatusEvent e){if(!PACK_ID.equals(e.getID()))return;if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){ready.add(e.getPlayer().getUniqueId());e.getPlayer().sendMessage("§a농장 리소스팩 적용 완료! /농사 로 시작하세요.");}else if(e.getStatus()!=PlayerResourcePackStatusEvent.Status.ACCEPTED&&e.getStatus()!=PlayerResourcePackStatusEvent.Status.DOWNLOADED)ready.remove(e.getPlayer().getUniqueId());renderer.visibility(e.getPlayer());}
    @EventHandler public void join(PlayerJoinEvent e){autoRequest(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){ready.remove(e.getPlayer().getUniqueId());cooldown.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){renderer.visibility(e.getPlayer());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(command.getName().equals("농사관리")){admin(sender,args);return true;}if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 사용하세요.");return true;}safe(p,()->{
        switch(args.length==0?"안내":args[0]){case "팩받기"->request(p);case "팩확인"->{ready.add(p.getUniqueId());renderer.visibility(p);p.sendMessage("§a수동 설치 확인을 저장했습니다. 작물이 보이지 않으면 /농사 팩받기 를 사용하세요.");}case "요리"->menus.kitchen(p,null);case "회수"->{Block target=p.getTargetBlockExact(6);var plot=target==null?null:touching(target);if(plot==null)throw new StateStore.Denied("회수할 작물의 경작지를 바라보세요.");menus.reclaim(p,plot);}default->menus.home(p);}
    });return true;}
    void admin(CommandSender sender,String[] args){if(!sender.hasPermission("koreanfarming.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}try{
        if(args.length==1&&args[0].equals("목록")){for(var plot:plots.all())sender.sendMessage("§7"+plot.id()+" | "+plot.owner()+" | "+plot.crop()+" | "+plot.world()+" "+plot.x()+","+plot.y()+","+plot.z()+" | "+plot.state());}
        else if(args.length==1&&args[0].equals("거래")){for(var r:db.unresolvedLog())if(r.get("kind").toString().startsWith("FARM_"))sender.sendMessage(r.toString());sender.sendMessage("§e실제 잔액·아이템 확인 후 완료/취소 번호 확인. 이 명령은 아이템·돈을 다시 지급하지 않습니다.");}
        else if(args.length==2&&args[0].equals("번호"))sender.sendMessage(db.receipt(db.receiptId(args[1])).toString());
        else if(args.length==3&&Set.of("완료","취소").contains(args[0])&&args[2].equals("확인")){var receipt=db.receipt(db.receiptId(args[1]));boolean done=args[0].equals("완료");db.resolveFarm(receipt.id(),done,sender.getName(),()->{if(done)plots.completeInside(receipt,()->core.recordFarmHarvestInside(UUID.fromString(receipt.actor()),FarmStore.operation(receipt).farmer()));else plots.cancelInside(receipt.id());});renderer.all();sender.sendMessage("§a농사 기록과 작물 예약을 정리했습니다. 아이템·돈은 수동 확인 결과대로 유지됩니다.");getLogger().info("농사 거래 정리: "+sender.getName()+" "+args[0]+" "+receipt.id());}
        else sender.sendMessage("§e/농사관리 목록 · 거래 · 번호 번호 · 완료|취소 번호 확인 · 백업은 /서버백업");
    }catch(RuntimeException e){sender.sendMessage("§c"+e.getMessage());getLogger().warning("농사 관리 확인: "+e);}}
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String label,String[] args){if(args.length!=1)return List.of();return (c.getName().equals("농사관리")?List.of("목록","거래","번호","완료","취소"):List.of("요리","회수","팩받기","팩확인")).stream().filter(s->s.startsWith(args[0])).toList();}
}
