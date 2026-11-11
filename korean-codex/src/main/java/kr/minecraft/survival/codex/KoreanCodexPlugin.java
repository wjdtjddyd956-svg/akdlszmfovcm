package kr.minecraft.survival.codex;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class KoreanCodexPlugin extends JavaPlugin implements Listener {
    Catalog catalog;CodexStore store;Bonuses bonuses;NamespacedKey pendingKey,menuKey;
    private final Map<UUID,Set<String>> collected=new HashMap<>();private final Map<UUID,Boolean> enabled=new HashMap<>();
    private final Map<Inventory,Map<Integer,ItemStack>> injected=new WeakHashMap<>();private boolean healthy=true;
    static final class Screen implements InventoryHolder {
        final UUID owner;final Inventory inventory;final Map<Integer,Runnable> actions=new HashMap<>();
        Screen(KoreanCodexPlugin plugin,Player player,String title){owner=player.getUniqueId();inventory=plugin.getServer().createInventory(this,54,title);}
        public Inventory getInventory(){return inventory;}
    }
    @Override public void onEnable(){
        saveDefaultConfig();pendingKey=new NamespacedKey(this,"pending_registration");menuKey=new NamespacedKey(this,"codex_button");bonuses=new Bonuses(this);
        try{catalog=new Catalog(getResource("catalog.tsv"));for(Catalog.Entry e:catalog.entries.values())Material.valueOf(e.material());store=new CodexStore(getDataFolder().toPath().resolve("도감.db"));}
        catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"도감 시작 실패: 기존 자료를 보존합니다.",e);getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getPluginManager().registerEvents(this,this);for(Player p:getServer().getOnlinePlayers())safe(p,()->load(p));
        getServer().getScheduler().runTaskTimer(this,()->{for(Player p:getServer().getOnlinePlayers())try{apply(p);}catch(RuntimeException e){getLogger().warning("도감 효과 갱신 실패: "+p.getUniqueId()+" "+e);}},200,200);
        getLogger().info("아이템 도감 85종 · 업적 13개 · 작은 영구 보너스 8종 준비 완료: /도감");
    }
    @Override public void onDisable(){
        if(bonuses!=null)for(Player p:getServer().getOnlinePlayers()){bonuses.remove(p);Inventory top=p.getOpenInventory().getTopInventory();if(top!=null&&top.getHolder() instanceof Screen)p.closeInventory();}
        for(Inventory inventory:new ArrayList<>(injected.keySet()))restoreButtons(inventory);collected.clear();enabled.clear();if(store!=null)store.close();
    }
    void load(Player p){store.player(p.getUniqueId(),p.getName());collected.put(p.getUniqueId(),store.registered(p.getUniqueId()));enabled.put(p.getUniqueId(),store.enabled(p.getUniqueId()));reconcileMarker(p);
        apply(p);
    }
    void reconcileMarker(Player p){
        String marker=p.getPersistentDataContainer().get(pendingKey,PersistentDataType.STRING);if(marker==null)return;
        String rid=marker.split(":",2)[0];
        try{
            CodexStore.Receipt r=store.receipt(rid);
            if(!r.player().equals(p.getUniqueId().toString()))throw new CodexStore.Denied("다른 계정의 저장 표식");
            if(Set.of("DONE","CANCELLED").contains(r.state()))p.getPersistentDataContainer().remove(pendingKey);
        }catch(CodexStore.Denied unknown){
            if(store.markerReleased(p.getUniqueId(),marker)){p.getPersistentDataContainer().remove(pendingKey);return;}
            store.markerReview(p.getUniqueId(),marker);
            getLogger().warning("확인할 수 없는 도감 저장 표식: "+p.getUniqueId()+" "+rid+". 자동 재소비를 차단합니다.");
        }
    }
    Set<String> registered(Player p){if(!collected.containsKey(p.getUniqueId()))load(p);return collected.get(p.getUniqueId());}
    void apply(Player p){if(!collected.containsKey(p.getUniqueId()))return;boolean active=getConfig().getBoolean("효과-사용",true)&&enabled.getOrDefault(p.getUniqueId(),true)&&p.hasPermission("koreancodex.use")&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);bonuses.apply(p,catalog.bonuses(collected.get(p.getUniqueId())),active);}
    void safe(Player p,Runnable task){if(!p.hasPermission("koreancodex.use")){p.sendMessage("§c도감을 사용할 권한이 없습니다.");return;}try{task.run();}catch(CodexStore.Denied|IllegalArgumentException e){p.sendMessage("§c"+e.getMessage());}catch(RuntimeException e){getLogger().log(java.util.logging.Level.SEVERE,"도감 처리 실패",e);p.sendMessage("§c도감 자료를 확인하지 못했습니다. 운영자에게 문의하세요.");}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equals("도감관리")){admin(sender,args);return true;}
        if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 사용하세요.");return true;}
        safe(p,()->{registered(p);switch(command.getName()){
            case "업적"->achievements(p);case "도감효과"->{if(args.length==1&&Set.of("켜기","끄기").contains(args[0])){boolean on=args[0].equals("켜기");store.enabled(p.getUniqueId(),on);enabled.put(p.getUniqueId(),on);apply(p);p.sendMessage(on?"§a수집 보너스를 켰습니다.":"§e수집 보너스를 껐습니다. 도감 기록은 유지됩니다.");}else effects(p);}
            default->{if(args.length==1&&args[0].equals("등록")){ItemStack held=p.getInventory().getItemInMainHand();Catalog.Entry entry=catalog.entries.values().stream().filter(e->matches(held,e)).findFirst().orElseThrow(()->new CodexStore.Denied("손에 든 아이템은 도감 대상이 아닙니다. /도감에서 목록을 보세요."));confirmation(p,entry);}else home(p);}
        }});return true;
    }
    static final NamespacedKey UI_ICON=new NamespacedKey("tycoon","ui_icon");
    enum Filter {ALL("전체"),READY("등록 가능"),MISSING("미보유"),DONE("등록 완료");final String label;Filter(String label){this.label=label;}}
    String registrationBlock(Player p){if(!healthy)return "저장소 확인 중";if(!p.hasPermission("koreancodex.use"))return "등록 권한 필요";if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)return "생존·모험 모드 필요";if(store.markerHeld(p.getUniqueId())||!store.rows("SELECT 1 FROM receipts WHERE player=? AND state IN ('PENDING','REVIEW') LIMIT 1",p.getUniqueId()).isEmpty())return "미결 등록을 운영자가 확인해야 합니다.";return null;}
    public int[] collectionSummary(Player p){Set<String> done=registered(p);boolean allowed=registrationBlock(p)==null;return new int[]{done.size(),catalog.entries.size(),(int)catalog.entries.values().stream().filter(e->allowed&&!done.contains(e.id())&&owned(p,e)>0).count(),catalog.eligible(done).size(),catalog.achievements.size()};}
    static String progress(long count,long total){int filled=total<1?0:(int)Math.min(10,Math.max(0,count)*10/total);return "§b"+"■".repeat(filled)+"§8"+"■".repeat(10-filled)+" §f"+count+" / "+total+" §7("+(total<1?0:Math.min(100,count*100/total))+"%)";}
    static String statProgress(int count,int total){int filled=Math.min(10,Math.max(0,count)*10/total);return "§b"+"■".repeat(filled)+"§8"+"■".repeat(10-filled)+" §f"+Math.min(100,count*100/total)+"%";}
    static void tag(Inventory inv,int slot,String model){ItemStack s=inv.getItem(slot);if(s==null)return;var m=s.getItemMeta();m.getPersistentDataContainer().set(UI_ICON,PersistentDataType.STRING,model);s.setItemMeta(m);inv.setItem(slot,s);}
    static void glow(Inventory inv,int slot,boolean value){ItemStack s=inv.getItem(slot);var m=s.getItemMeta();m.setEnchantmentGlintOverride(value);s.setItemMeta(m);inv.setItem(slot,s);}
    int owned(Player p,Catalog.Entry e){return Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).filter(s->matches(s,e)).mapToInt(ItemStack::getAmount).sum();}
    boolean providerReady(Catalog.Entry e){return e.customId().isEmpty()||farm()!=null;}
    static String categoryModel(String id){return "ui/"+switch(id){case "forest"->"forest";case "farm"->"crops";case "mining"->"mining";case "sea"->"sea";case "craft"->"crafting";case "nether"->"nether";case "end"->"end";case "treasure"->"treasure";case "custom_farm"->"custom_farm";default->"codex";};}
    static ItemStack icon(Material material,String name,String... lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));item.setItemMeta(meta);return item;}
    Screen screen(Player p,String title){Screen s=new Screen(this,p,title);for(int i=0;i<54;i++)s.inventory.setItem(i,icon(i<9?Material.CYAN_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE," "));button(s,49,Material.BARRIER,"§c닫기",p::closeInventory,"§7왼쪽 클릭: 화면 닫기");tag(s.inventory,49,"close");
        button(s,47,Material.WRITABLE_BOOK,"§b마을 수첩",()->returnToMenu(p),"§7쉬프트+F의 전체 메뉴로 돌아갑니다.");tag(s.inventory,47,"business_ledger");return s;}
    void returnToMenu(Player p){Plugin menu=getServer().getPluginManager().getPlugin("KoreanMenu");if(menu==null||!menu.isEnabled()||!p.hasPermission("koreanmenu.use"))throw new CodexStore.Denied("마을 수첩을 사용할 수 없습니다.");try{menu.getClass().getMethod("openFromNavigation",Player.class).invoke(menu,p);}catch(NoSuchMethodException ex){p.performCommand("메뉴");}catch(ReflectiveOperationException ex){throw new CodexStore.Denied("마을 수첩을 열지 못했습니다.");}}
    void button(Screen screen,int slot,Material material,String name,Runnable action,String... lore){screen.inventory.setItem(slot,icon(material,name,lore));screen.actions.put(slot,action);}
    static int[] eight(){return new int[]{10,12,14,16,28,30,32,34,40};}
    void home(Player p){Set<String> done=registered(p);Screen s=screen(p,"아이템 도감 | 85종 수집");int available=collectionSummary(p)[2];s.inventory.setItem(4,icon(Material.BOOK,"§b나의 수집 도감",progress(done.size(),catalog.entries.size()),"§a지금 등록 가능: "+available+"종","§7기본 아이템·정품 수확물 1개 소비 · 영구 등록","§7분야를 선택하면 보유 수량과 실제 보상을 확인합니다."));tag(s.inventory,4,"ui/codex");int[] slots={10,12,14,19,21,23,28,30,32};int i=0;
        for(Catalog.Category c:catalog.categories){long count=c.entries().stream().filter(e->done.contains(e.id())).count();long ready=registrationBlock(p)==null?c.entries().stream().filter(e->!done.contains(e.id())&&owned(p,e)>0).count():0;int slot=slots[i++];button(s,slot,Material.valueOf(c.icon()),(count==c.entries().size()?"§a[완성] ":"§f")+c.name(),()->category(p,c),progress(count,c.entries().size()),"§a등록 가능 "+ready+"종","§7주요 효과: "+c.stat().label,"§e왼쪽 클릭: 분야 열기");tag(s.inventory,slot,categoryModel(c.id()));glow(s.inventory,slot,count==c.entries().size());}
        button(s,45,Material.NETHER_STAR,"§6수집 업적",()->achievements(p),progress(catalog.eligible(done).size(),catalog.achievements.size()),"§7목표별 진행률과 추가 보상");tag(s.inventory,45,"ui/achievements");button(s,53,Material.APPLE,"§a내 수집 효과",()->effects(p),"§7현재 적용량·전체 상한·효과 켜기/끄기");tag(s.inventory,53,"ui/effects");p.openInventory(s.inventory);
    }
    void category(Player p,Catalog.Category c){category(p,c,Filter.ALL);}
    void category(Player p,Catalog.Category c,Filter filter){Set<String> done=registered(p);String block=registrationBlock(p);Screen s=screen(p,"도감 | "+c.name()+" · "+filter.label);long completed=c.entries().stream().filter(e->done.contains(e.id())).count();s.inventory.setItem(4,icon(Material.valueOf(c.icon()),"§b"+c.name()+" · "+filter.label,progress(completed,c.entries().size()),"§7보유 수량은 가방 36칸의 등록 가능한 정품 기준",c.id().equals("custom_farm")?"§7정품 수확물만 등록 · 씨앗·요리 제외":"§7이름·마법·손상·추가 정보가 없는 기본 아이템"));tag(s.inventory,4,categoryModel(c.id()));int[] slots={10,11,12,13,14,19,20,21,22,23};int i=0;
        for(Catalog.Entry e:c.entries()){boolean registered=done.contains(e.id());int count=owned(p,e);if(filter==Filter.READY&&(registered||count==0||block!=null)||filter==Filter.MISSING&&(registered||count>0)||filter==Filter.DONE&&!registered)continue;int slot=slots[i++];String state=registered?"§a[등록 완료] ":!providerReady(e)?"§8[농장 연결 필요] ":block!=null?"§e[등록 대기] ":count>0?"§b[등록 가능] ":"§7[미보유] ";List<String> lore=new ArrayList<>(List.of("§7희귀도: "+e.rarity(),"§f가방에 등록 가능한 수량: §e"+count+"개","§7기본 등록 효과: "+c.stat().label+" "+c.stat().format(catalog.entryUnits(e))));if(block!=null&&!registered)lore.add("§e"+block);lore.add(registered?"§a이미 등록했습니다. 추가 소비하지 않습니다.":count>0?"§e왼쪽 클릭: 1개 소비·실제 효과 확인":"§7아이템을 준비한 뒤 다시 확인하세요.");button(s,slot,Material.valueOf(e.material()),state+e.name(),()->{if(registered){p.sendMessage("§a이미 등록한 "+e.name()+"입니다. 아이템을 소비하지 않았습니다.");category(p,c,filter);}else confirmation(p,e);},lore.toArray(String[]::new));decorateCustom(s.inventory,slot,e,p);glow(s.inventory,slot,registered);}
        if(i==0){s.inventory.setItem(22,icon(Material.PAPER,"§7이 조건에 해당하는 아이템이 없습니다.","§7아래에서 전체 또는 다른 필터를 선택하세요."));tag(s.inventory,22,"ui/missing");}
        Catalog.Achievement award=catalog.achievements.stream().filter(a->a.id().equals("category_"+c.id())).findFirst().orElseThrow();button(s,16,Material.GOLD_INGOT,"§6분야 완성 업적",()->achievements(p),progress(completed,c.entries().size()),"§7추가 "+award.reward().entrySet().stream().map(r->r.getKey().label+" "+r.getKey().format(r.getValue())).collect(java.util.stream.Collectors.joining(", ")));tag(s.inventory,16,"ui/achievements");
        int n=0;for(Filter f:Filter.values()){int slot=new int[]{37,39,41,43}[n++];button(s,slot,f==filter?Material.LIME_DYE:Material.GRAY_DYE,(f==filter?"§b● ":"§7○ ")+f.label,()->category(p,c,f),"§7왼쪽 클릭: "+f.label+" 아이템만 보기");tag(s.inventory,slot,switch(f){case ALL->"ui/codex";case READY->"ui/ready";case MISSING->"ui/missing";case DONE->"ui/done";});glow(s.inventory,slot,f==filter);}
        button(s,45,Material.ARROW,"§e전체 도감",()->home(p));tag(s.inventory,45,"ui/back");button(s,53,Material.APPLE,"§a내 수집 효과",()->effects(p));tag(s.inventory,53,"ui/effects");p.openInventory(s.inventory);
    }
    List<String> actualRewards(Player p,Catalog.Entry entry){Set<String> next=new HashSet<>(registered(p));var before=catalog.bonuses(next);Set<String> oldAwards=catalog.eligible(next);next.add(entry.id());var after=catalog.bonuses(next);List<String> lines=new ArrayList<>();for(var stat:Catalog.Stat.values()){int difference=after.get(stat)-before.get(stat);if(difference>0)lines.add("§a실제 증가: "+stat.label+" "+stat.format(difference));}if(lines.isEmpty())lines.add("§7현재 능력치 상한에 도달해 추가 증가가 없습니다.");for(var award:catalog.achievements)if(!oldAwards.contains(award.id())&&catalog.eligible(next).contains(award.id()))lines.add("§6동시 달성: "+award.name());return lines;}
    void confirmation(Player p,Catalog.Entry e){if(registered(p).contains(e.id()))throw new CodexStore.Denied("이미 영구 등록한 아이템입니다.");Catalog.Category c=catalog.byCategory.get(e.category());Screen s=screen(p,"도감 등록 | 1개 소비 확인");List<String> lore=new ArrayList<>(List.of("§7희귀도: "+e.rarity(),"§f등록 가능한 보유 수량: §e"+owned(p,e)+"개","§c확인하면 정품 아이템 1개가 소비됩니다.","§7등록 후 아이템을 돌려주지 않습니다."));lore.addAll(actualRewards(p,e));s.inventory.setItem(22,icon(Material.valueOf(e.material()),"§f"+e.name(),lore.toArray(String[]::new)));decorateCustom(s.inventory,22,e,p);s.inventory.setItem(4,icon(Material.BOOK,"§e아이템 소비와 실제 효과를 확인하세요.","§7보유 수량과 상한을 계산한 결과입니다."));tag(s.inventory,4,"ui/codex");button(s,30,Material.LIME_CONCRETE,"§a확인 · 1개 소비해 영구 등록",()->{registerItem(p,e);category(p,c);},"§c실제 아이템 1개 소비");tag(s.inventory,30,"confirm");button(s,32,Material.RED_CONCRETE,"§c취소 · 아이템 유지",()->category(p,c));tag(s.inventory,32,"close");p.openInventory(s.inventory);
    }
    void effects(Player p){Map<Catalog.Stat,Integer> values=catalog.bonuses(registered(p));Screen s=screen(p,"도감 효과 | 작은 영구 보너스");int i=0;Material[] icons={Material.FEATHER,Material.IRON_PICKAXE,Material.APPLE,Material.IRON_SWORD,Material.IRON_CHESTPLATE,Material.CLOCK,Material.SHIELD,Material.RABBIT_FOOT};String[] models={"speed","mining_speed","health","damage","armor","attack_speed","knockback","luck"};
        boolean on=enabled.getOrDefault(p.getUniqueId(),true),applied=on&&getConfig().getBoolean("효과-사용",true)&&p.hasPermission("koreancodex.use")&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);s.inventory.setItem(4,icon(Material.NETHER_STAR,applied?"§a수집 효과 적용 중":"§7수집 효과 미적용","§7개인 설정: "+(on?"켜짐":"꺼짐"),"§7생존·모험 모드와 서버 효과 설정을 따릅니다."));tag(s.inventory,4,"ui/effects");
        for(Catalog.Stat stat:Catalog.Stat.values()){s.inventory.setItem(eight()[i],icon(icons[i],"§b"+stat.label+" "+stat.format(applied?values.get(stat):0),"§7내 수집량: "+stat.format(values.get(stat)),"§7전체 상한: "+stat.format(stat.cap),statProgress(values.get(stat),stat.cap),stat==Catalog.Stat.HEALTH?"§7체력 2 = 하트 1칸 · 즉시 회복하지 않습니다.":stat==Catalog.Stat.LUCK?"§7바닐라 낚시 전리품 판정에 사용":"§7기존 장비·물약 효과와 함께 적용"));tag(s.inventory,eight()[i],"ui/"+models[i]);i++;}
        button(s,45,Material.ARROW,"§e도감으로",()->home(p));tag(s.inventory,45,"ui/back");button(s,53,on?Material.LIME_DYE:Material.GRAY_DYE,on?"§a효과 켜짐 · 클릭하여 끄기":"§7효과 꺼짐 · 클릭하여 켜기",()->{store.enabled(p.getUniqueId(),!on);enabled.put(p.getUniqueId(),!on);apply(p);effects(p);},"§7등록 기록은 유지합니다.");tag(s.inventory,53,on?"ui/ready":"ui/missing");p.openInventory(s.inventory);
    }
    void achievements(Player p){Set<String> registered=registered(p),eligible=catalog.eligible(registered);Screen s=screen(p,"수집 업적 | 13개 목표");s.inventory.setItem(4,icon(Material.NETHER_STAR,"§6수집 업적 달성 현황",progress(eligible.size(),catalog.achievements.size()),"§7목표 달성 시 보너스가 자동 적용됩니다."));tag(s.inventory,4,"ui/achievements");int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24};int i=0;
        for(Catalog.Achievement a:catalog.achievements){long current,target;if(a.id().startsWith("total_")){target=Integer.parseInt(a.id().substring(6));current=Math.min(registered.size(),target);}else{var c=catalog.byCategory.get(a.id().substring(9));target=c.entries().size();current=c.entries().stream().filter(e->registered.contains(e.id())).count();}List<String> lore=new ArrayList<>();lore.add("§7"+a.description());lore.add(progress(current,target));for(var stat:Catalog.Stat.values())if(a.reward().getOrDefault(stat,0)>0)lore.add("§7추가 "+stat.label+" "+stat.format(a.reward().get(stat)));lore.add(eligible.contains(a.id())?"§a달성 완료 · 보너스 자동 반영":"§e달성까지 "+(target-current)+"종 남음");int slot=slots[i++];s.inventory.setItem(slot,icon(eligible.contains(a.id())?Material.GOLD_INGOT:Material.PAPER,(eligible.contains(a.id())?"§a[달성] ":"§7[진행 중] ")+a.name(),lore.toArray(String[]::new)));tag(s.inventory,slot,eligible.contains(a.id())?"ui/done":"ui/achievements");glow(s.inventory,slot,eligible.contains(a.id()));}
        button(s,45,Material.ARROW,"§e도감으로",()->home(p));tag(s.inventory,45,"ui/back");button(s,53,Material.APPLE,"§a내 수집 효과",()->effects(p));tag(s.inventory,53,"ui/effects");p.openInventory(s.inventory);
    }
    Plugin farm(){Plugin f=getServer().getPluginManager().getPlugin("KoreanFarming");return f!=null&&f.isEnabled()?f:null;}
    boolean matches(ItemStack stack,Catalog.Entry entry){
        if(stack==null)return false;if(entry.customId().isEmpty())return stack.isSimilar(new ItemStack(Material.valueOf(entry.material())));
        Plugin f=farm();if(f==null)return false;try{return Boolean.TRUE.equals(f.getClass().getMethod("isItem",ItemStack.class,String.class).invoke(f,stack,entry.customId()));}catch(ReflectiveOperationException ex){throw new CodexStore.Denied("농장 도감 연동을 확인하지 못했습니다.");}
    }
    void decorateCustom(Inventory inv,int slot,Catalog.Entry entry,Player viewer){if(entry.customId().isEmpty())return;Plugin f=farm();if(f==null)return;try{ItemStack preview=(ItemStack)f.getClass().getMethod("previewItem",String.class,Player.class).invoke(f,entry.customId(),viewer);ItemStack shown=inv.getItem(slot);var meta=shown.getItemMeta();meta.setItemModel(preview.getItemMeta().getItemModel());meta.getPersistentDataContainer().set(new NamespacedKey("tycoon","farm_preview"),PersistentDataType.STRING,entry.customId());shown.setItemMeta(meta);inv.setItem(slot,shown);}catch(ReflectiveOperationException ex){getLogger().warning("농장 도감 미리보기: "+ex);}}
    void registerItem(Player p,Catalog.Entry entry){
        reconcileMarker(p);
        if(!healthy)throw new CodexStore.Denied("저장소 확인 중이라 새 등록을 중지했습니다.");if(!p.hasPermission("koreancodex.use")||(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE))throw new CodexStore.Denied("생존 또는 모험 모드에서 등록하세요.");if(registered(p).contains(entry.id()))throw new CodexStore.Denied("이미 등록한 아이템입니다.");
        RegistrationPlan plan=RegistrationPlan.create(p.getInventory().getStorageContents(),s->matches(s,entry));CodexStore.Receipt receipt=store.reserve(p.getUniqueId(),entry.id(),plan.slot(),plan.amount(),plan.beforeHash(),plan.afterHash());Set<String> awards;
        if(!Arrays.equals(plan.before(),p.getInventory().getStorageContents())){store.cancel(receipt.id(),"가방 변경: 소비 전 취소");throw new CodexStore.Denied("가방이 바뀌었습니다. 다시 확인하세요.");}
        try{
            p.getPersistentDataContainer().set(pendingKey,PersistentDataType.STRING,receipt.id()+":BEFORE");p.saveData();store.stage(receipt.id(),"BEFORE_SAVED");
            if(!Arrays.equals(plan.before(),p.getInventory().getStorageContents()))throw new IllegalStateException("등록 직전 가방이 변경되었습니다.");
            p.getInventory().setStorageContents(RegistrationPlan.copy(plan.after()));p.getPersistentDataContainer().set(pendingKey,PersistentDataType.STRING,receipt.id()+":CONSUMED");p.saveData();store.stage(receipt.id(),"CONSUMED_SAVED");awards=store.finish(receipt.id(),catalog,false);
        }catch(RuntimeException e){try{store.review(receipt.id(),"아이템·플레이어 저장 표식 수동 확인 필요");}catch(RuntimeException failure){healthy=false;e.addSuppressed(failure);}if(e instanceof CodexStore.Failure)healthy=false;getLogger().log(java.util.logging.Level.SEVERE,"도감 등록 확인 필요: "+receipt.id()+" "+p.getUniqueId(),e);throw new CodexStore.Denied("등록 확인이 필요합니다. 번호 "+receipt.id().substring(0,8)+"을 운영자에게 알려주세요. 자동으로 다시 소비하지 않습니다.");}
        collected.put(p.getUniqueId(),store.registered(p.getUniqueId()));p.getPersistentDataContainer().remove(pendingKey);try{p.saveData();}catch(RuntimeException e){getLogger().warning("등록 완료 후 표식 정리 저장 지연: "+receipt.id());}apply(p);p.sendMessage("§a도감 등록 완료: "+entry.name()+" §7(1개 소비 · 영구 등록)");for(Catalog.Achievement a:catalog.achievements)if(awards.contains(a.id()))p.sendMessage("§6[업적 달성] "+a.name()+" · 보너스가 추가됐습니다.");if(!awards.isEmpty())p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.6f,1.2f);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof Screen s){e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!s.owner.equals(p.getUniqueId())||e.getClick()!=ClickType.LEFT)return;Runnable action=s.actions.get(e.getRawSlot());if(action!=null)getServer().getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==s.inventory){p.closeInventory();safe(p,action);}});return;}
        if(!(e.getWhoClicked() instanceof Player p)||!sourceMenu(e.getView().getTopInventory())||e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta())return;String action=item.getItemMeta().getPersistentDataContainer().get(menuKey,PersistentDataType.STRING);if(action==null)return;e.setCancelled(true);if(e.getClick()!=ClickType.LEFT||!p.hasPermission("koreanmenu.use"))return;Inventory expected=e.getView().getTopInventory();getServer().getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==expected){p.closeInventory();safe(p,()->{if(action.equals("도감"))home(p);else achievements(p);});}});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent e){safe(e.getPlayer(),()->load(e.getPlayer()));}
    @EventHandler public void quit(PlayerQuitEvent e){bonuses.remove(e.getPlayer());collected.remove(e.getPlayer().getUniqueId());enabled.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void gameMode(PlayerGameModeChangeEvent e){getServer().getScheduler().runTask(this,()->{if(e.getPlayer().isOnline())apply(e.getPlayer());});}
    @EventHandler public void respawn(PlayerRespawnEvent e){getServer().getScheduler().runTask(this,()->{if(e.getPlayer().isOnline())apply(e.getPlayer());});}
    @EventHandler public void world(PlayerChangedWorldEvent e){apply(e.getPlayer());}
    boolean sourceMenu(Inventory inv){if(inv==null||inv.getHolder()==null||!inv.getHolder().getClass().getName().equals("kr.minecraft.survival.menu.KoreanMenuPlugin$Menu"))return false;Plugin owner=getServer().getPluginManager().getPlugin("KoreanMenu");try{return owner!=null&&owner.isEnabled()&&Class.forName(owner.getDescription().getMain(),false,inv.getHolder().getClass().getClassLoader()).isInstance(owner);}catch(ClassNotFoundException e){return false;}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void openMenu(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player p)||!sourceMenu(e.getInventory())||!e.getView().getTitle().contains("마을 수첩")||!getConfig().getBoolean("메뉴-연결",true))return;Inventory inv=e.getInventory();getServer().getScheduler().runTask(this,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=inv||!p.hasPermission("koreancodex.use"))return;for(int slot:new int[]{20,26}){ItemStack old=inv.getItem(slot);if(old==null||old.getType()!=Material.GRAY_STAINED_GLASS_PANE||!old.hasItemMeta()||!" ".equals(old.getItemMeta().getDisplayName()))continue;String action=slot==20?"도감":"업적";injected.computeIfAbsent(inv,k->new HashMap<>()).put(slot,old.clone());ItemStack button=icon(slot==20?Material.BOOK:Material.GOLDEN_CHESTPLATE,slot==20?"§b아이템 도감":"§6수집 업적","§7아이템 수집과 작은 영구 보너스");ItemMeta meta=button.getItemMeta();meta.getPersistentDataContainer().set(menuKey,PersistentDataType.STRING,action);button.setItemMeta(meta);inv.setItem(slot,button);}});}
    void restoreButtons(Inventory inventory){Map<Integer,ItemStack> old=injected.remove(inventory);if(old==null)return;for(var entry:old.entrySet()){ItemStack current=inventory.getItem(entry.getKey());if(current!=null&&current.hasItemMeta()&&current.getItemMeta().getPersistentDataContainer().has(menuKey,PersistentDataType.STRING))inventory.setItem(entry.getKey(),entry.getValue());}}
    @EventHandler(priority=EventPriority.MONITOR) public void closeMenu(InventoryCloseEvent e){restoreButtons(e.getInventory());}
    void admin(CommandSender sender,String[] args){if(!sender.hasPermission("koreancodex.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}try{
        if(args.length==1&&args[0].equals("거래")){for(var r:store.unresolved())sender.sendMessage("§e"+r.get("id")+" | "+r.get("player")+" | "+r.get("entry")+" | "+r.get("stage"));for(var m:store.markerReviews())sender.sendMessage("§c저장 표식 확인 대기 | "+m.get("player")+" | "+m.get("marker"));sender.sendMessage("§7아이템과 플레이어 저장 표식을 확인한 뒤 완료/취소를 선택하세요. 자동 환불은 없습니다.");}
        else if(args.length==2&&args[0].equals("번호")){sender.sendMessage(store.receipt(store.receiptId(args[1])).toString());}
        else if(args.length==2&&args[0].equals("조회")){UUID id=store.playerId(args[1]).orElseThrow(()->new CodexStore.Denied("접속 이력이 없습니다."));sender.sendMessage("§6등록 "+store.registered(id).size()+" / 85 · 업적 "+store.awards(id).size()+" / 13");for(var r:store.log(id))sender.sendMessage("§7"+r.get("id")+" | "+r.get("entry")+" | "+r.get("state")+" | "+r.get("stage"));}
        else if(args.length==3&&Set.of("완료","취소").contains(args[0])&&args[2].equals("확인")){String rid=store.receiptId(args[1]);CodexStore.Receipt r=store.receipt(rid);if(!r.state().equals("REVIEW"))throw new CodexStore.Denied("확인 대기 등록만 정리할 수 있습니다.");if(args[0].equals("완료"))store.finish(rid,catalog,true);else store.cancel(rid,sender.getName());Player p=getServer().getPlayer(UUID.fromString(r.player()));if(p!=null&&p.isOnline()){collected.put(p.getUniqueId(),store.registered(p.getUniqueId()));String marker=p.getPersistentDataContainer().get(pendingKey,PersistentDataType.STRING);if(marker!=null&&marker.startsWith(rid+":"))p.getPersistentDataContainer().remove(pendingKey);apply(p);}sender.sendMessage("§a등록 기록을 정리했습니다. 가방 아이템은 변경하지 않았습니다.");getLogger().info("도감 거래 정리: "+sender.getName()+" "+args[0]+" "+rid);}
        else if(args.length==3&&args[0].equals("표식해제")&&args[2].equals("확인")){UUID id=store.playerId(args[1]).orElseThrow(()->new CodexStore.Denied("접속 이력이 없습니다."));store.releaseMarkers(id,sender.getName());Player p=getServer().getPlayer(id);if(p!=null&&p.isOnline()){reconcileMarker(p);p.saveData();}sender.sendMessage("§a알 수 없는 저장 표식의 잠금을 정리했습니다. 아이템·등록·보상은 변경하지 않았습니다.");getLogger().info("도감 알 수 없는 표식 정리: "+sender.getName()+" "+id);}
        else if(args.length==1&&args[0].equals("백업")){Path dir=getDataFolder().toPath().resolve("백업");Files.createDirectories(dir);Path file=dir.resolve("도감-"+System.currentTimeMillis()+".db");store.snapshot(file);sender.sendMessage("§a도감 DB 백업 완료: "+file.getFileName());}
        else sender.sendMessage("§e/도감관리 거래 · 번호 거래번호 · 조회 닉네임 · 완료|취소 번호 확인 · 표식해제 닉네임 확인 · 백업");
    }catch(Exception e){sender.sendMessage("§c"+e.getMessage());getLogger().warning("도감 관리: "+e);}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length!=1)return List.of();return (command.getName().equals("도감관리")?List.of("거래","번호","조회","완료","취소","표식해제","백업"):command.getName().equals("도감효과")?List.of("켜기","끄기"):List.of("등록")).stream().filter(s->s.startsWith(args[0])).toList();}
}
