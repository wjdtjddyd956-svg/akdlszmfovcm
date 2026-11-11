package kr.minecraft.survival.farming;

import java.util.*;
import java.util.function.IntConsumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import kr.minecraft.survival.economy.domain.StateStore;

final class FarmMenus implements Listener {
    static final class Screen implements InventoryHolder {
        final UUID owner;final Inventory inventory;final Map<Integer,IntConsumer> actions=new HashMap<>();final Entity source;
        Screen(KoreanFarmingPlugin plugin,Player p,String title,Entity source){owner=p.getUniqueId();this.source=source;inventory=plugin.getServer().createInventory(this,54,title);}
        public Inventory getInventory(){return inventory;}
    }
    record Hook(int slot,ItemStack previous,Runnable action){}
    final KoreanFarmingPlugin p;final Map<Inventory,Hook> injected=new WeakHashMap<>();
    FarmMenus(KoreanFarmingPlugin p){this.p=p;}
    static ItemStack icon(Material m,String name,String...lore){ItemStack s=new ItemStack(m);var meta=s.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));s.setItemMeta(meta);return s;}
    Screen screen(Player player,String title,Entity source){Screen s=new Screen(p,player,title,source);for(int i=0;i<54;i++)s.inventory.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," "));button(s,49,icon(Material.BARRIER,"§c닫기"),n->player.closeInventory());return s;}
    void button(Screen s,int slot,ItemStack icon,IntConsumer action){s.inventory.setItem(slot,icon);s.actions.put(slot,action);}
    ItemStack described(Player player,String id,String name,String...lore){ItemStack s=p.previewItem(id,player);var meta=s.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));s.setItemMeta(meta);return s;}
    void home(Player player){Screen s=screen(player,"도시 농장 | 커스텀 작물 5종",null);s.inventory.setItem(4,icon(Material.WRITABLE_BOOK,"§b나의 작은 농장","§7씨앗은 농산물·잡화 NPC에서 구매합니다.","§7물 4칸 안의 경작지 · 밝기 9 이상","§7작물 우클릭: 수확 / 경작지를 보고 /농사 회수","§7접속 중인 서버의 로드된 청크에서만 성장"));int i=0;
        for(var c:FarmRules.CROPS){button(s,19+i++,described(player,c.id(),"§a"+c.name(),"§7성장 "+c.minutes()+"분 · 수확 "+c.minYield()+"~"+c.maxYield()+"개","§7씨앗 "+c.seedPrice()+"원 / 농산물 매입 "+c.sell()+"원","§7"+(c.perennial()?"수확 후 25%부터 다시 성장":"수확 후 씨앗 1개 반환"),"§7개인 하루 품목별 매입 64개","§e클릭: 농장 위치와 상태 확인"),n->list(player));}
        button(s,30,icon(Material.CRAFTING_TABLE,"§6농장 요리 5종"),n->kitchen(player,null));button(s,32,icon(Material.BOOK,"§b도감에 수확물 등록","§71개 소비 · 영구 등록 · 작은 체력 보너스"),n->player.performCommand("도감"));button(s,45,icon(Material.MAP,"§e내 작물 목록"),n->list(player));button(s,53,icon(Material.PAINTING,"§a농장 리소스팩 받기","§7새 작물 모델에는 농장 통합팩이 필요합니다."),n->p.request(player));player.openInventory(s.inventory);
    }
    void list(Player player){long count=p.plots.all().stream().filter(q->q.owner().equals(player.getUniqueId().toString())).count();player.sendMessage("§6내 작물 "+count+" / 32 (서버 전체 최대 256)");for(var q:p.plots.all())if(q.owner().equals(player.getUniqueId().toString())){var c=FarmRules.crop(q.crop());World w=p.getServer().getWorld(UUID.fromString(q.world()));player.sendMessage("§e"+c.name()+" §7"+(w==null?q.world():w.getName())+" "+q.x()+", "+q.y()+", "+q.z()+" · "+Math.min(100,q.progress()*100/c.duration())+"% · "+(q.state().equals("ACTIVE")?(p.growing(q)?"성장 가능":"청크·물·빛 확인"):"거래 확인 중"));}}
    void npc(Player player,Entity npc){if(npc==null||!npc.isValid()||npc.isDead()||npc.getWorld()!=player.getWorld()||npc.getLocation().distanceSquared(player.getLocation())>25)throw new StateStore.Denied("상인에게 5블록 안으로 가까이 오세요.");String category=npc.getPersistentDataContainer().get(new NamespacedKey("koreaneconomy","market_npc"),PersistentDataType.STRING);if(!Set.of("ALL","CROPS","FOOD").contains(category==null?"":category))throw new StateStore.Denied("농장 거래를 지원하는 상인이 아닙니다.");}
    void market(Player player,Entity source,boolean food){npc(player,source);Screen s=screen(player,food?"농장 요리 상인 | 완성 요리 판매":"농장 상인 | 씨앗 구매·농산물 판매",source);s.inventory.setItem(4,icon(Material.EMERALD,"§6농장 거래","§7좌클릭 1개 · 쉬프트 클릭 16개 · 우클릭 64개","§7수확물/요리: 개인·품목별 하루 매입 64개","§7씨앗은 구매만 가능 · 거래에는 정품이 필요합니다."));int i=0;
        if(food){for(var d:FarmRules.DISHES){int left=Math.max(0,64-p.db.used(player.getUniqueId(),p.core.farmingDay(),"farm:"+d.id()));button(s,19+i++,described(player,d.id(),"§6"+d.name()+" 판매","§7개당 "+d.sell()+"원 · 오늘 남은 매입 "+left+"개"),n->p.shop(player,source,d.id(),n,false));}button(s,45,icon(Material.CRAFTING_TABLE,"§a농장 요리 만들기"),n->kitchen(player,source));}
        else {for(var c:FarmRules.CROPS){int left=Math.max(0,64-p.db.used(player.getUniqueId(),p.core.farmingDay(),"farm:"+c.id()));button(s,10+i,described(player,c.id()+"_seed","§a"+c.name()+" 씨앗 구매","§7개당 "+c.seedPrice()+"원"),n->p.shop(player,source,c.id(),n,true));button(s,28+i++,described(player,c.id(),"§6"+c.name()+" 판매","§7개당 "+c.sell()+"원 · 오늘 남은 매입 "+left+"개"),n->p.shop(player,source,c.id(),n,false));}}
        player.openInventory(s.inventory);
    }
    void kitchen(Player player,Entity source){Screen s=screen(player,"농장 주방 | 요리 5종",source);int i=0;for(var d:FarmRules.DISHES){List<String> lore=new ArrayList<>();lore.add("§7농부 "+d.level()+"레벨 필요 · 현재 "+p.core.farmingLevel(player));for(var ingredient:d.ingredients()){String name;if(FarmRules.custom(ingredient.id()))name=FarmRules.crop(ingredient.id()).name();else name=switch(ingredient.id()){case "SUGAR"->"설탕";case "GLASS_BOTTLE"->"유리병";case "WHEAT"->"밀";case "EGG"->"달걀";case "MILK_BUCKET"->"우유 양동이";case "CARROT"->"당근";case "BOWL"->"그릇";default->ingredient.id();};lore.add("§7"+name+" × "+ingredient.count());}lore.add("§7포만감 +"+d.food()+" · NPC 매입 "+d.sell()+"원");if(d.id().equals("corn_soup"))lore.add("§7요리 시 빈 양동이 1개 반환");lore.add("§e좌클릭: 1개 요리");button(s,19+i++,described(player,d.id(),"§f"+d.name(),lore.toArray(String[]::new)),n->p.cook(player,d.id()));}
        if(source!=null)button(s,45,icon(Material.GOLD_INGOT,"§6농장 요리 판매"),n->market(player,source,true));else s.inventory.setItem(45,icon(Material.GOLD_INGOT,"§7판매는 요리 상인 NPC에게","§7/요리 화면에서도 이 주방에 올 수 있습니다."));player.openInventory(s.inventory);
    }
    void reclaim(Player player,FarmStore.Plot plot){p.nearby(player,plot);p.protection.access(player,plot);Screen s=screen(player,"작물 회수 | 씨앗 1개 반환",null);s.inventory.setItem(22,described(player,plot.crop(),"§e"+FarmRules.crop(plot.crop()).name()+" 회수","§7성장 상태를 버리고 씨앗 1개를 받습니다.","§7수확물·경험치·돈은 지급하지 않습니다."));button(s,30,icon(Material.LIME_CONCRETE,"§a확인 · 회수"),n->p.harvest(player,plot.id(),true));button(s,32,icon(Material.RED_CONCRETE,"§c취소"),n->home(player));player.openInventory(s.inventory);}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){Inventory inv=e.getView().getTopInventory();if(inv.getHolder() instanceof Screen s){e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player player)||!s.owner.equals(player.getUniqueId())||!Set.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT).contains(e.getClick()))return;IntConsumer action=s.actions.get(e.getRawSlot());if(action==null)return;int n=e.isShiftClick()?16:e.isRightClick()?64:1;p.getServer().getScheduler().runTask(p,()->{if(player.isOnline()&&player.getOpenInventory().getTopInventory()==s.inventory){player.closeInventory();p.safe(player,()->{if(s.source!=null)npc(player,s.source);action.accept(n);});}});return;}
        Hook h=injected.get(inv);if(h==null||e.getRawSlot()!=h.slot)return;e.setCancelled(true);if(e.getClick()!=ClickType.LEFT||!(e.getWhoClicked() instanceof Player player))return;p.getServer().getScheduler().runTask(p,()->{if(player.isOnline()&&player.getOpenInventory().getTopInventory()==inv){player.closeInventory();p.safe(player,h.action);}});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
    boolean trusted(Inventory inv,String plugin,String holder){if(inv.getHolder()==null||!inv.getHolder().getClass().getName().equals(holder))return false;var owner=p.getServer().getPluginManager().getPlugin(plugin);try{return owner!=null&&owner.isEnabled()&&Class.forName(owner.getDescription().getMain(),false,inv.getHolder().getClass().getClassLoader()).isInstance(owner);}catch(ClassNotFoundException ex){return false;}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player player))return;Inventory inv=e.getInventory();String title=e.getView().getTitle();int slot;Runnable action;ItemStack button;
        if(trusted(inv,"KoreanMenu","kr.minecraft.survival.menu.KoreanMenuPlugin$Menu")&&title.contains("마을 수첩")){slot=25;action=()->home(player);button=icon(Material.WHEAT,"§a커스텀 농사","§7딸기 · 토마토 · 옥수수 · 쌀 · 오이");}
        else if(trusted(inv,"KoreanEconomy","kr.minecraft.survival.economy.KoreanEconomyPlugin$Market")&&(title.contains("농산물 상점")||title.contains("잡화 상점"))){Entity source=p.core.getShopNpc(inv);if(source==null)return;slot=51;action=()->market(player,source,false);button=icon(Material.WHEAT_SEEDS,"§a농장 상점 · 씨앗/수확물");}
        else if(trusted(inv,"KoreanEconomy","kr.minecraft.survival.economy.Tycoon$Screen")&&(title.startsWith("요리 상인")||title.startsWith("마을 주방"))){Entity source=p.core.getShopNpc(inv);slot=51;action=source==null?()->kitchen(player,null):()->market(player,source,true);button=icon(Material.BOWL,"§a농장 요리 · 5종");}
        else return;
        p.getServer().getScheduler().runTask(p,()->{if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=inv)return;ItemStack old=inv.getItem(slot);if(old==null||old.getType()!=Material.GRAY_STAINED_GLASS_PANE||!old.hasItemMeta()||!" ".equals(old.getItemMeta().getDisplayName()))return;injected.put(inv,new Hook(slot,old.clone(),action));inv.setItem(slot,button);});
    }
    void restore(Inventory inv){Hook h=injected.remove(inv);if(h!=null)inv.setItem(h.slot,h.previous);}
    @EventHandler(priority=EventPriority.MONITOR) public void closeInventory(InventoryCloseEvent e){restore(e.getInventory());}
    void close(){for(Player player:p.getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory()!=null&&player.getOpenInventory().getTopInventory().getHolder() instanceof Screen)player.closeInventory();for(Inventory inv:new ArrayList<>(injected.keySet()))restore(inv);}
}
