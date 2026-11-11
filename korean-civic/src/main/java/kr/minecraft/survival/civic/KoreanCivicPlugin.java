package kr.minecraft.survival.civic;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minecraft.survival.economy.KoreanEconomyPlugin;
import kr.minecraft.survival.economy.domain.*;
import static kr.minecraft.survival.economy.domain.ActionEngine.*;

/** Existing Vault wallet and native land guards remain the single authorities. */
public class KoreanCivicPlugin extends JavaPlugin {
    KoreanEconomyPlugin core;StateStore db;CivicStore store;ActionEngine engine;CivicMenus menus;
    long founding,expansion,maxPrice;int maxPlots,maxTowns,maxListings,maxServerListings;boolean healthy=true;
    final Map<String,UUID> leases=new HashMap<>();
    @Override public void onEnable(){try{
        saveDefaultConfig();core=(KoreanEconomyPlugin)getServer().getPluginManager().getPlugin("KoreanEconomy");db=core.getEconomyStateStore();engine=core.getEconomyActionEngine();
        founding=amount("마을-설립비",true);expansion=amount("필지-확장비",true);maxPrice=amount("최대-거래금액",false);maxPlots=limit("마을당-최대필지",1,256);maxTowns=limit("서버-최대마을",1,256);maxListings=limit("인당-최대판매등록",1,100);maxServerListings=limit("서버-최대판매등록",1,5000);
        store=new CivicStore(db);core.refreshLandProtection();menus=new CivicMenus(this);getServer().getPluginManager().registerEvents(menus,this);getLogger().info("마을·필지 매매 / 쉬프트+F 개인상점 준비 완료. 기존 데이터 유지.");
    }catch(RuntimeException|LinkageError e){healthy=false;getLogger().log(java.util.logging.Level.SEVERE,"마을·개인상점 활성화 실패. KoreanEconomy 2.0.3과 함께 설치하세요.",e);getServer().getPluginManager().disablePlugin(this);}}
    @Override public void onDisable(){if(menus!=null)for(Player p:getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory()!=null&&p.getOpenInventory().getTopInventory().getHolder() instanceof CivicMenus.Screen)p.closeInventory();/* leases deliberately remain in the core until a successful refresh/restart */}
    long amount(String key,boolean zero){double v=getConfig().getDouble(key,Double.NaN);long cents=Money.cents(v);if(cents<(zero?0:100)||cents>100_000_000L)throw new IllegalArgumentException(key+" 금액 범위: "+(zero?0:1)+"~100만 원");return cents;}
    int limit(String key,int min,int max){int v=getConfig().getInt(key,-1);if(v<min||v>max)throw new IllegalArgumentException(key+" 범위: "+min+"~"+max);return v;}
    void ready(Player p){if(!healthy||!engine.healthy())throw new StateStore.Denied("저장소 확인이 필요해 거래를 중지했습니다. 운영자에게 문의하세요.");if(!p.isOnline()||!p.hasPermission("koreancivic.use"))throw new StateStore.Denied("이 기능을 사용할 권한이 없습니다.");if(p.getGameMode()!=GameMode.SURVIVAL)throw new StateStore.Denied("생존 모드에서 이용하세요.");core.prepareFarmingPlayer(p);engine.assertReady();}
    void safe(Player p,Runnable action){try{action.run();}catch(StateStore.Denied|IllegalArgumentException|ArithmeticException e){p.sendMessage("§c"+e.getMessage());}catch(RuntimeException e){healthy=false;getLogger().log(java.util.logging.Level.SEVERE,"마을 거래 중지: 저장소와 거래 기록 확인 필요",e);p.sendMessage("§c저장소 오류로 거래를 중지했습니다. 운영자에게 문의하세요.");}}
    long price(String raw){try{long cents=new java.math.BigDecimal(raw).movePointRight(2).longValueExact();checkPrice(cents);return cents;}catch(NumberFormatException|ArithmeticException e){throw new StateStore.Denied("가격은 소수 둘째 자리까지 숫자로 입력하세요.");}}
    int quantity(String raw){try{return Integer.parseInt(raw);}catch(NumberFormatException e){throw new StateStore.Denied("수량은 정수로 입력하세요.");}}
    void checkPrice(long cents){if(cents<100||cents>maxPrice)throw new StateStore.Denied("가격은 1원~"+Money.display(maxPrice)+" 사이로 정하세요.");}
    String defaultName(Player p){String n=p.getName();return CivicStore.townName(n.substring(0,Math.min(n.length(),14))+"마을");}
    String owner(String uuid){return db.playerName(uuid);}
    World world(String id){World w=getServer().getWorld(UUID.fromString(id));if(w==null)throw new StateStore.Denied("해당 월드가 열려 있지 않습니다.");return w;}
    void at(Player p,String world,int x,int z){if(!p.getWorld().getUID().toString().equals(world)||(p.getLocation().getBlockX()>>4)!=x||(p.getLocation().getBlockZ()>>4)!=z)throw new StateStore.Denied("선택했던 청크 위에서 다시 확인하세요.");}
    UUID hold(String world,int x,int z){UUID token=UUID.randomUUID();core.holdLand(token,world,x,z);return token;}
    void land(Player p,String world,int x,int z,boolean free,Supplier<StateStore.Receipt> reservation){ready(p);World w=world(world);if(free){at(p,world,x,z);core.assertChunkAvailable(p,w,x,z);}else core.assertExternalLandClear(w,x,z);UUID token=hold(world,x,z);StateStore.Receipt r;
        try{r=reservation.get();}catch(StateStore.Denied|IllegalArgumentException e){core.releaseLand(token);throw e;}catch(RuntimeException e){healthy=false;throw e;}
        leases.put(r.id(),token);try{core.refreshLandProtection();}catch(RuntimeException e){db.review(r.id(),"보호 갱신 실패: 운영자 확인 필요");healthy=false;throw e;}
        finish(p,r,Direction.DEBIT,NO_ITEMS,"필지 처리를 완료했습니다.");
    }
    void found(Player p,String name,String world,int x,int z){land(p,world,x,z,true,()->store.reserveTown(p.getUniqueId(),world,x,z,name,founding,maxTowns));}
    void expand(Player p,String town,String world,int x,int z){land(p,world,x,z,true,()->store.reserveExpansion(p.getUniqueId(),town,world,x,z,expansion,maxPlots));}
    void sellPlot(Player p,String id,long price){ready(p);if(price!=0)checkPrice(price);store.setPlotPrice(p.getUniqueId(),id,price);p.sendMessage(price==0?"§a필지 판매를 취소했습니다.":"§a필지를 "+Money.display(price)+"에 판매 등록했습니다.");}
    void buyPlot(Player p,String id,long expected){var plot=store.plot(id);land(p,plot.world(),plot.x(),plot.z(),false,()->store.reservePlotBuy(p.getUniqueId(),id,expected));}
    ItemStack escrow(CivicStore.Listing row){ItemStack item=ItemStack.deserializeBytes(row.item());if(item.getType().isAir()||item.getAmount()!=row.amount()||!item.getType().name().equals(row.material())||item.getAmount()>item.getMaxStackSize())throw new IllegalStateException("보관 물품 검증 실패: "+row.id());return item;}
    ItemStack saleItem(Player p){ItemStack item=p.getInventory().getItemInMainHand().clone();if(item.getType().isAir()||item.getAmount()<1||item.getAmount()>item.getMaxStackSize())throw new StateStore.Denied("판매할 물건을 주 손에 들고 다시 등록하세요.");for(NamespacedKey key:item.getItemMeta().getPersistentDataContainer().getKeys())if(key.equals(new NamespacedKey("tycoon","ui_icon"))||key.equals(new NamespacedKey("tycoon","farm_preview"))||key.getNamespace().equals("citytextures"))throw new StateStore.Denied("메뉴 표시용 아이템은 등록할 수 없습니다.");return item;}
    void list(Player p,ItemStack expected,int count,long price){ready(p);checkPrice(price);if(!saleItem(p).equals(expected))throw new StateStore.Denied("손에 든 물건이 바뀌었습니다.");InventoryChange items=new InventoryChange(p);items.takeHand(expected,count);ItemStack lot=expected.clone();lot.setAmount(count);byte[] bytes=lot.serializeAsBytes();var receipt=store.reserveListing(p.getUniqueId(),bytes,price,lot.getType().name(),count,maxListings,maxServerListings);finish(p,receipt,Direction.NONE,items,"물건을 개인상점에 등록했습니다. 금액은 묶음 전체 가격입니다.");}
    void buyItem(Player p,String id,long expected,boolean cancel){ready(p);var row=store.listing(id);InventoryChange items=new InventoryChange(p);items.give(escrow(row));var receipt=store.reserveItem(p.getUniqueId(),id,expected,cancel);finish(p,receipt,cancel?Direction.NONE:Direction.DEBIT,items,cancel?"판매를 취소하고 물건을 회수했습니다.":"물건을 구매했습니다. 가방을 확인하세요.");}
    void collect(Player p){ready(p);var receipt=store.reserveCollection(p.getUniqueId());finish(p,receipt,Direction.CREDIT,NO_ITEMS,"판매대금을 받았습니다.");}
    void finish(Player p,StateStore.Receipt receipt,Direction direction,Change items,String success){Outcome outcome;
        try{outcome=engine.reserved(receipt,direction,items,()->store.completeInside(receipt));}catch(RuntimeException e){db.review(receipt.id(),"실행 전후 오류: 운영자 확인 필요");throw e;}
        if(outcome.status()!=Status.REVIEW){store.cleanupRejected(receipt.id());core.refreshLandProtection();UUID token=leases.remove(receipt.id());if(token!=null)core.releaseLand(token);}
        p.sendMessage((outcome.status()==Status.DONE?"§a"+success:"§e"+outcome.message())+" §8["+receipt.id().substring(0,8)+"]");
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(command.getName().equals("마을거래")){admin(sender,args);return true;}if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 이용하세요.");return true;}safe(p,()->{ready(p);String op=args.length==0?"":args[0];if(command.getName().equals("마을")){switch(op){case "설립"->{String name=args.length>1?String.join(" ",Arrays.copyOfRange(args,1,args.length)):defaultName(p);menus.founding(p,CivicStore.townName(name));}case "확장"->menus.expansion(p);case "필지"->menus.plots(p,"판매",0);default->menus.town(p);}}else{switch(op){case "등록"->{ItemStack item=saleItem(p);long price=args.length>1?price(args[1]):10000;int count=args.length>2?quantity(args[2]):item.getAmount();if(count<1||count>item.getAmount())throw new StateStore.Denied("판매 수량은 1~"+item.getAmount()+"개입니다.");menus.listEditor(p,item,count,Math.min(maxPrice,price));}case "내판매"->menus.market(p,true,0);case "대금"->menus.collection(p);default->menus.market(p,false,0);}}});return true;}
    void admin(CommandSender sender,String[] args){if(!sender.hasPermission("koreancivic.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}try{if(args.length==0||args[0].equals("대기")){sender.sendMessage("§6[마을·개인상점 확인 대기] 자동 재지급 없음");for(var row:db.unresolvedLog())if(row.get("kind").toString().startsWith("CIVIC_"))sender.sendMessage("§e"+row.get("id")+" · "+row.get("kind")+" · "+owner(row.get("actor").toString()));sender.sendMessage("§7/마을거래 번호 번호 · /마을거래 완료|취소 번호 확인");return;}if(args[0].equals("번호")&&args.length==2){var r=db.receipt(db.receiptId(args[1]));if(!r.kind().startsWith("CIVIC_"))throw new StateStore.Denied("마을 거래가 아닙니다.");sender.sendMessage("§e"+r.id()+" · "+r.state()+" · "+r.kind()+" · "+Money.display(r.cents()));sender.sendMessage("§7"+r.payload());return;}if(args.length!=3||!Set.of("완료","취소").contains(args[0])||!args[2].equals("확인"))throw new StateStore.Denied("실제 잔액·가방·거래를 확인한 뒤 /마을거래 완료|취소 번호 확인");String id=db.receiptId(args[1]);var receipt=db.receipt(id);boolean done=args[0].equals("완료");db.resolveCivic(id,done,sender.getName(),()->{if(done)store.completeInside(receipt);else store.cancelInside(id);});core.refreshLandProtection();UUID token=leases.remove(id);if(token!=null)core.releaseLand(token);getLogger().warning("마을 거래 수동 정리: "+id+" / "+args[0]+" / "+sender.getName());sender.sendMessage("§a거래 기록을 정리했습니다. 돈·아이템은 자동 지급하거나 회수하지 않았습니다.");}catch(StateStore.Denied|IllegalArgumentException e){sender.sendMessage("§c"+e.getMessage());}catch(RuntimeException e){healthy=false;getLogger().log(java.util.logging.Level.SEVERE,"마을 거래 수동 정리 오류",e);sender.sendMessage("§c저장소 오류: 서버를 중지하고 백업·기록을 확인하세요.");}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length!=1)return List.of();List<String> words=command.getName().equals("마을")?List.of("설립","확장","필지"):command.getName().equals("개인상점")?List.of("등록","내판매","대금"):List.of("대기","번호","완료","취소");return words.stream().filter(s->s.startsWith(args[0])).toList();}
}
