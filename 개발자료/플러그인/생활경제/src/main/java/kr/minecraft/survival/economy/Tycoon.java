package kr.minecraft.survival.economy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import kr.minecraft.survival.economy.domain.*;
import static kr.minecraft.survival.economy.domain.ActionEngine.*;

/** Server-owned menus: actions are bound to the holder, never to a client item/name. */
final class Tycoon implements Listener {
    final KoreanEconomyPlugin plugin;
    final StateStore store;
    final ActionEngine engine;
    final LandProtection lands;
    final AuditLog audit;
    final ServerBackups backups;
    final Legacy12 legacy;
    final Set<UUID> registered=new HashSet<>();
    final NamespacedKey dishKey,upgradeKey,toolKey;
    final SecureRandom random=new SecureRandom();
    private final Map<UUID,Long> actions=new HashMap<>();
    static final class Screen implements InventoryHolder {
        final UUID owner;final Inventory inventory;final Map<Integer,Runnable> buttons=new HashMap<>();final Entity source;final Map<Integer,java.util.function.IntConsumer> quantities=new HashMap<>();
        Screen(Tycoon t,Player p,String title,Entity source){owner=p.getUniqueId();this.source=source;inventory=t.plugin.getServer().createInventory(this,54,title);}
        public Inventory getInventory(){return inventory;}
    }
    Tycoon(KoreanEconomyPlugin plugin){
        this.plugin=plugin;store=new StateStore(plugin.getDataFolder().toPath().resolve("생활경제.db"));
        legacy=new Legacy12(store,plugin.getDataFolder().toPath().resolve("생활기록"));
        dishKey=new NamespacedKey(plugin,"dish_id");upgradeKey=new NamespacedKey(plugin,"upgrade_level");toolKey=new NamespacedKey(plugin,"tool_id");
        engine=new ActionEngine(store,new Wallet(){
            public boolean credit(UUID id,long c){return plugin.transfer(id,c,true);}
            public boolean debit(UUID id,long c){return plugin.transfer(id,c,false);}
        },e->plugin.getLogger().log(java.util.logging.Level.SEVERE,"거래 확인 필요",e));
        lands=new LandProtection(this);audit=new AuditLog(this);backups=new ServerBackups(this);
        for(Listener l:List.of(this,lands,audit))plugin.getServer().getPluginManager().registerEvents(l,plugin);
        for(Player p:plugin.getServer().getOnlinePlayers())register(p);
    }
    String day(){return LocalDate.now(plugin.rewardZone()).toString();}
    String resetLabel(){return LocalDate.now(plugin.rewardZone()).plusDays(1)+" 00:00 ("+plugin.rewardZone()+")";}
    Rules.Recipe configured(Rules.Recipe r){
        long price=plugin.getConfig().getLong("요리."+r.id()+".매입가",r.sell()/100);
        int limit=plugin.getConfig().getInt("요리."+r.id()+".하루매입한도",r.dailyLimit());
        if(price<1||price>1_000_000||limit<0||limit>1_000_000)throw new StateStore.Denied("요리 매입가·한도 설정을 확인하세요.");
        return new Rules.Recipe(r.id(),r.name(),r.job(),r.level(),r.result(),r.amount(),r.ingredients(),Money.cents(price),limit);
    }
    static String money(long cents){return Money.display(cents);}
    void register(Player p){
        if(registered.contains(p.getUniqueId()))return;
        store.player(p.getUniqueId(),p.getName());
        if(store.meta("legacy-daily:"+p.getUniqueId())==null){
            Path file=plugin.getDataFolder().toPath().resolve("보상기록").resolve(p.getUniqueId()+".properties");
            Properties data=new Properties();
            try{if(Files.exists(file))try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){data.load(r);}
                store.importDaily(p.getUniqueId(),data.getProperty("날짜"),data.getProperty("상태"));
            }catch(Exception e){throw new StateStore.Denied("이전 보상 기록을 읽지 못했습니다. 운영자 확인이 필요합니다.");}
        }
        List<Legacy12.Mission> oldQuests=List.of(new Legacy12.Mission("DELIVERY","밀 납품",plugin.getConfig().getInt("임무.밀 납품.목표",16),Money.cents(plugin.getConfig().getInt("임무.밀 납품.보상",48))),new Legacy12.Mission("MINING","광석 채굴",plugin.getConfig().getInt("임무.광석 채굴.목표",12),Money.cents(plugin.getConfig().getInt("임무.광석 채굴.보상",75))),new Legacy12.Mission("FISHING","물고기 낚기",plugin.getConfig().getInt("임무.물고기 낚기.목표",6),Money.cents(plugin.getConfig().getInt("임무.물고기 낚기.보상",75))));
        if(legacy.importPlayer(p.getUniqueId(),day(),plugin.getConfig().getInt("직업.레벨당활동",50),oldQuests))p.sendMessage("§a1.2 직업·적립금·오늘의 매입/임무 기록을 안전하게 가져왔습니다.");
        registered.add(p.getUniqueId());

    }
    boolean command(CommandSender sender,String root,String[] a){
        if(root.equals("경제기록")){admin(sender,a);return true;}
        if(root.equals("생활기록")){audit.command(sender,a);return true;}
        if(root.equals("서버백업")){backups.command(sender,a);return true;}
        if(!List.of("직업","요리","강화","임무","땅보호").contains(root))return false;
        if(!(sender instanceof Player p)){sender.sendMessage("게임 안에서 사용하세요.");return true;}
        safe(p,()->{register(p);switch(root){case "직업"->jobs(p);case "요리"->recipes(p);case "강화"->upgradeMenu(p);case "임무"->quests(p,null);case "땅보호"->lands.command(p,a);}});
        return true;
    }
    void safe(Player p,Runnable work){
        if(!p.hasPermission("koreaneconomy.use")){p.sendMessage("§c생활 기능 사용 권한이 없습니다.");return;}
        try{register(p);work.run();}catch(StateStore.Denied|IllegalArgumentException e){p.sendMessage("§c"+e.getMessage());}
        catch(RuntimeException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"생활 기능 처리 실패",e);p.sendMessage("§c저장 중 오류가 발생했습니다. 운영자에게 문의하세요.");}
    }
    void result(Player p,Outcome outcome,String success){p.sendMessage((outcome.status()==Status.DONE?"§a"+success:"§c"+outcome.message()));}
    void survival(Player p){if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)throw new StateStore.Denied("생존 또는 모험 모드에서 이용하세요.");}
    Screen screen(Player p,String title,Entity source){Screen s=new Screen(this,p,title,source);for(int i=0;i<54;i++)s.inventory.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," "));button(s,49,Material.BARRIER,"§c닫기",p::closeInventory);return s;}
    static ItemStack icon(Material m,String name,String... lore){ItemStack item=new ItemStack(m);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));item.setItemMeta(meta);return item;}
    void button(Screen s,int slot,Material m,String title,Runnable action,String... lore){s.inventory.setItem(slot,icon(m,title,lore));s.buttons.put(slot,action);}
    void jobs(Player p){
        Screen s=screen(p,"생활 직업 | 선택·숙련도",null);Rules.Job selected=store.job(p.getUniqueId());
        s.inventory.setItem(4,icon(Material.BOOK,"§6현재 직업: "+(selected==null?"미선택":selected.label),"§7첫 선택 무료 · 변경 200원 / 30분 간격","§7직업별 숙련도는 변경 후에도 유지됩니다.","§7하루 숙련도 획득 한도: 250"));
        int slot=20;for(Rules.Job j:Rules.Job.values()){
            StateStore.Progress progress=store.progress(p.getUniqueId(),j);
            button(s,slot,j==Rules.Job.FARMER?Material.WHEAT:j==Rules.Job.MINER?Material.IRON_PICKAXE:Material.FISHING_ROD,"§e"+j.label+" · "+progress.level()+"레벨",()->{
                survival(p);Rules.Job old=store.job(p.getUniqueId());if(old==j)throw new StateStore.Denied("이미 선택한 직업입니다.");
                if(old!=null&&System.currentTimeMillis()-store.changedAt(p.getUniqueId())<1_800_000)throw new StateStore.Denied("직업 변경 후 30분이 지나야 합니다.");
                long now=System.currentTimeMillis();Outcome out=engine.execute(p.getUniqueId(),"JOB","job:"+UUID.randomUUID(),old==null?0:20000,Direction.DEBIT,j.name(),null,NO_ITEMS,()->store.selectJobInside(p.getUniqueId(),j,now));
                result(p,out,j.label+" 직업을 선택했습니다.");
            },"§7누적 숙련도: "+progress.xp(),"§7다음 단계: "+(progress.level()==5?"최고 레벨":Rules.nextLevel(progress.xp())),"§7클릭하면 직업을 선택합니다.");slot+=2;
        }
        button(s,40,Material.GOLD_INGOT,"§6직업 적립금 받기",()->{long owed=store.owed(p.getUniqueId());if(owed<1)throw new StateStore.Denied("적립된 직업 보상이 없습니다.");Outcome out=engine.execute(p.getUniqueId(),"JOB_CLAIM","jobclaim:"+UUID.randomUUID(),owed,Direction.CREDIT,"직업 적립 보상",null,NO_ITEMS,()->store.consumeOwedInside(p.getUniqueId(),owed));result(p,out,"직업 적립금 "+money(owed)+"을 받았습니다.");},"§7받을 돈: "+money(store.owed(p.getUniqueId())),"§7오늘 적립: "+money(store.earned(p.getUniqueId(),day())),"§7하루 한도: "+plugin.getConfig().getInt("직업.하루보상한도",300)+"원");
        p.openInventory(s.inventory);
    }
    ItemStack dish(Rules.Recipe r){return dish(r,plugin.getConfig().getBoolean("리소스팩-사용",false));}
    ItemStack dish(Rules.Recipe r,boolean model){
        ItemStack item=icon(Material.valueOf(r.result()),"§e"+r.name(),"§7마을 주방에서 만든 요리","§7요리 상인에게 판매할 수 있습니다.");
        ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(dishKey,PersistentDataType.STRING,r.id());
        if(model)meta.setItemModel(new NamespacedKey("tycoon",r.id()));
        item.setItemMeta(meta);return item;
    }
    void recipes(Player p){
        Screen s=screen(p,"마을 주방 | 요리 10종",null);int slot=10;
        for(Rules.Recipe base:Rules.RECIPES){Rules.Recipe r=configured(base);
            List<String> lore=new ArrayList<>(List.of("§7필요 숙련도: "+r.job().label+" "+r.level()+"레벨","§7결과: "+r.amount()+"개 · 개당 매입 "+money(r.sell())));
            for(Rules.Ingredient i:r.ingredients())lore.add("§7재료: "+materialName(i.material())+" × "+i.amount());
            lore.add("§e클릭: 1회 만들기 (이름 없는 기본 재료만)");
            button(s,slot,Material.valueOf(r.result()),"§e"+r.name(),()->cook(p,r),lore.toArray(String[]::new));
            slot++;if(slot%9==8)slot+=2;
        }p.openInventory(s.inventory);
    }
    static String materialName(String name){return switch(name){case "WHEAT"->"밀";case "CARROT"->"당근";case "POTATO"->"감자";case "COAL"->"석탄";case "BEETROOT"->"비트";case "BOWL"->"그릇";case "RED_MUSHROOM"->"빨간 버섯";case "BROWN_MUSHROOM"->"갈색 버섯";case "PUMPKIN"->"호박";case "SUGAR"->"설탕";case "EGG"->"달걀";case "APPLE"->"사과";case "GLASS_BOTTLE"->"유리병";case "COCOA_BEANS"->"코코아 콩";case "COD"->"생대구";case "SALMON"->"생연어";case "BEEF"->"생소고기";case "GOLD_NUGGET"->"금 조각";default->name;};}
    void cook(Player p,Rules.Recipe r){
        survival(p);if(store.job(p.getUniqueId())!=r.job()||store.progress(p.getUniqueId(),r.job()).level()<r.level())throw new StateStore.Denied("해당 직업과 요리 숙련도가 필요합니다.");
        ItemStack[] before=ItemPlans.copy(p.getInventory().getStorageContents()),after=ItemPlans.copy(before);
        for(Rules.Ingredient i:r.ingredients()){after=ItemPlans.move(after,new ItemStack(Material.valueOf(i.material())),i.amount(),false);if(after==null)throw new StateStore.Denied("요리 재료가 부족합니다.");}
        after=ItemPlans.move(after,dish(r),r.amount(),true);if(after==null)throw new StateStore.Denied("가방 공간이 부족합니다.");
        String date=day();Outcome out=engine.execute(p.getUniqueId(),"COOK","cook:"+UUID.randomUUID(),0,Direction.NONE,r.id(),null,change(p,before,after),()->store.advanceInside(p.getUniqueId(),date,"COOK",r.id(),1));
        result(p,out,r.name()+" "+r.amount()+"개를 만들었습니다.");if(out.status()==Status.DONE)recipes(p);
    }
    void foodShop(Player p,Entity npc){Screen s=screen(p,"요리 상인 | 완성 요리 매입",npc);int slot=10;
        for(Rules.Recipe base:Rules.RECIPES){Rules.Recipe r=configured(base);final int target=slot;button(s,slot,Material.valueOf(r.result()),"§e"+r.name(),()->{},"§7매입 가격: "+money(r.sell())+" / 1개","§7남은 매입: "+Math.max(0,r.dailyLimit()-store.used(p.getUniqueId(),day(),"food:"+r.id()))+" / "+r.dailyLimit(),"§7초기화: "+resetLabel(),"§e좌클릭 1개 · 쉬프트 16개 · 우클릭 64개");
            s.quantities.put(target,amount->{sellFood(p,r,amount);foodShop(p,npc);});slot++;if(slot%9==8)slot+=2;
        }p.openInventory(s.inventory);
    }
    void sellFood(Player p,Rules.Recipe r,int amount){
        survival(p);ItemStack[] before=ItemPlans.copy(p.getInventory().getStorageContents());ItemStack[] after=before;int left=amount;
        // Accept both pack-on and pack-off canonical models, so changing visual settings does not strand old food.
        for(boolean model:new boolean[]{false,true}){
            ItemStack prototype=dish(r,model);int available=0;for(ItemStack i:after)if(i!=null&&i.isSimilar(prototype))available+=i.getAmount();int remove=Math.min(left,available);if(remove>0){after=ItemPlans.move(after,prototype,remove,false);left-=remove;}
        }
        if(left>0)throw new StateStore.Denied("마을 주방에서 만든 동일한 요리 "+amount+"개가 필요합니다.");
        result(p,engine.execute(p.getUniqueId(),"FOOD_SELL","food:"+UUID.randomUUID(),Money.total(r.sell(),amount),Direction.CREDIT,r.id()+" x "+amount,new StateStore.Quota(day(),"food:"+r.id(),amount,r.dailyLimit()),change(p,before,after),()->{}),r.name()+" "+amount+"개를 "+money(Money.total(r.sell(),amount))+"에 판매했습니다.");
    }
    boolean upgradeable(ItemStack item){if(item==null)return false;String n=item.getType().name();return n.endsWith("_PICKAXE")||n.endsWith("_AXE")||n.endsWith("_SHOVEL")||n.endsWith("_HOE")||n.equals("FISHING_ROD");}
    void upgradeMenu(Player p){
        ItemStack held=p.getInventory().getItemInMainHand();if(!upgradeable(held)||held.getAmount()!=1)throw new StateStore.Denied("강화할 도구 하나를 주 손에 들고 /강화를 입력하세요.");
        ItemStack original=held.clone();int slot=p.getInventory().getHeldItemSlot();int level=held.getItemMeta().getPersistentDataContainer().getOrDefault(upgradeKey,PersistentDataType.INTEGER,0);
        if(level>=5)throw new StateStore.Denied("최고 강화 단계입니다.");Rules.Upgrade defaults=Rules.upgrade(level);
        long cost=plugin.getConfig().getLong("강화."+(level+1)+".비용",defaults.cost()/100);
        int chance=plugin.getConfig().getInt("강화."+(level+1)+".성공확률만분율",defaults.chanceBasisPoints());
        if(cost<1||cost>1_000_000||chance<0||chance>10000)throw new StateStore.Denied("강화 비용·확률 설정을 확인하세요.");
        Rules.Upgrade rule=new Rules.Upgrade(level+1,Money.cents(cost),chance);Screen s=screen(p,"도구 강화 | 비용·확률 확인",null);
        s.inventory.setItem(22,original);button(s,31,Material.ANVIL,"§e+"+rule.nextLevel()+" 강화 시도",()->{
            survival(p);if(slot!=p.getInventory().getHeldItemSlot()||!original.equals(p.getInventory().getItemInMainHand()))throw new StateStore.Denied("손에 든 도구가 바뀌었습니다. 강화 창을 다시 여세요.");
            boolean success=random.nextInt(10000)<rule.chanceBasisPoints();ItemStack next=success?strengthen(original,rule.nextLevel()):original.clone();
            ItemStack[] before=ItemPlans.copy(p.getInventory().getStorageContents()),after=ItemPlans.copy(before);after[slot]=next;
            result(p,engine.execute(p.getUniqueId(),"UPGRADE","upgrade:"+UUID.randomUUID(),rule.cost(),Direction.DEBIT,"tool="+original.getType()+",slot="+slot+",level="+level+",success="+success,null,change(p,before,after),()->{}),success?"강화 성공! +"+rule.nextLevel()+" 도구가 되었습니다.":"강화 실패. 비용은 사용됐고 도구와 단계는 유지됩니다.");
        },"§7비용: "+money(rule.cost()),"§7성공 확률: "+rule.chanceBasisPoints()/100.0+"%","§7실패: 비용 소모 / 파괴·단계 하락 없음","§7성공: 효율/미끼 + 단계별 내구성 마법 적용");p.openInventory(s.inventory);
    }
    ItemStack strengthen(ItemStack original,int nextLevel){
        if(nextLevel<1||nextLevel>5||!upgradeable(original))throw new IllegalArgumentException("강화 도구 또는 단계 오류");ItemStack next=original.clone();ItemMeta meta=next.getItemMeta();
        meta.getPersistentDataContainer().set(upgradeKey,PersistentDataType.INTEGER,nextLevel);meta.getPersistentDataContainer().set(toolKey,PersistentDataType.STRING,meta.getPersistentDataContainer().getOrDefault(toolKey,PersistentDataType.STRING,UUID.randomUUID().toString()));
        Enchantment enchant=next.getType()==Material.FISHING_ROD?Enchantment.LURE:Enchantment.EFFICIENCY;int target=Math.min(enchant.getMaxLevel(),nextLevel);if(meta.getEnchantLevel(enchant)<target)meta.addEnchant(enchant,target,false);
        int durability=nextLevel==1?0:nextLevel<=3?1:nextLevel-2;if(meta.getEnchantLevel(Enchantment.UNBREAKING)<durability)meta.addEnchant(Enchantment.UNBREAKING,durability,false);
        List<String> lore=new ArrayList<>(meta.hasLore()?meta.getLore():List.of());lore.removeIf(line->line.startsWith("§b생활 강화 +"));lore.add("§b생활 강화 +"+nextLevel);meta.setLore(lore);next.setItemMeta(meta);return next;
    }
    void quests(Player p,Entity npc){
        Screen s=screen(p,"마을 게시판 | 오늘의 임무",npc);String date=day();int slot=19;Map<String,StateStore.QuestProgress> states=new HashMap<>();for(var v:store.quests(p.getUniqueId(),date))states.put(v.id(),v);
        for(Rules.Quest q:Rules.QUESTS){var state=states.get(q.id());
            button(s,slot++,q.kind().equals("DELIVER")?Material.CHEST:Material.WRITABLE_BOOK,"§e"+q.name(),()->{
                survival(p);if(!day().equals(date))throw new StateStore.Denied("날짜가 바뀌었습니다. 임무 창을 다시 여세요.");
                if(state==null){store.acceptQuest(p.getUniqueId(),date,q.id(),3);p.sendMessage("§a임무를 수락했습니다: "+q.name());quests(p,npc);return;}
                store.assertQuestReady(p.getUniqueId(),date,q.id());ItemStack[] before=ItemPlans.copy(p.getInventory().getStorageContents()),after=before;
                if(q.kind().equals("DELIVER")){after=ItemPlans.move(before,new ItemStack(Material.valueOf(q.target())),q.required(),false);if(after==null)throw new StateStore.Denied("납품할 기본 아이템 "+q.required()+"개가 필요합니다.");}
                Outcome out=engine.execute(p.getUniqueId(),"QUEST","quest:"+date+":"+q.id(),q.reward(),Direction.CREDIT,q.id(),null,change(p,before,after),()->{});result(p,out,"임무 완료! "+money(q.reward())+"을 받았습니다.");
                if(out.status()==Status.DONE){Rules.Job j=store.job(p.getUniqueId());if(j!=null)xp(p,j,q.xp());quests(p,npc);}
            },"§7보상: "+money(q.reward())+" · 숙련도 "+q.xp(),"§7목표: "+questTarget(q)+" "+q.required()+"회/개",state==null?"§e클릭: 수락 (하루 최대 3개)":state.paid()?"§a보상 수령 완료":q.kind().equals("DELIVER")?"§e클릭: 아이템 납품·보상 수령":"§7진행: "+state.progress()+" / "+q.required()+" · 클릭하여 완료");
        }
        int legacySlot=37;
        for(var old:store.quests(p.getUniqueId(),date))if(old.id().startsWith("legacy12_")){
            Legacy12.Mission m=legacy.mission(p.getUniqueId(),date,old.id());
            button(s,legacySlot++,Material.WRITABLE_BOOK,"§d이전 임무 · "+m.label(),()->{
                survival(p);if(!day().equals(date))throw new StateStore.Denied("날짜가 바뀌었습니다.");var current=store.quests(p.getUniqueId(),date).stream().filter(q->q.id().equals(old.id())).findFirst().orElseThrow();if(current.paid())throw new StateStore.Denied("이미 받은 보상입니다.");
                ItemStack[] before=ItemPlans.copy(p.getInventory().getStorageContents()),after=before;if(m.key().equals("DELIVERY")){after=ItemPlans.move(before,new ItemStack(Material.WHEAT),m.goal(),false);if(after==null)throw new StateStore.Denied("납품할 기본 밀 "+m.goal()+"개가 필요합니다.");}else if(current.progress()<m.goal())throw new StateStore.Denied("이전 임무 목표를 아직 채우지 않았습니다.");
                result(p,engine.execute(p.getUniqueId(),"QUEST","quest:"+date+":"+old.id(),m.cents(),Direction.CREDIT,"이전 임무 "+m.label(),null,change(p,before,after),()->{}),"이전 임무 완료: "+money(m.cents()));quests(p,npc);
            },"§7목표: "+m.goal()+" · 보상: "+money(m.cents()),"§7진행: "+old.progress()+" / "+m.goal(),old.paid()?"§a이미 수령함":"§e클릭: 이전 목표대로 납품/완료");
        }p.openInventory(s.inventory);
    }
    static String questTarget(Rules.Quest q){return switch(q.target()){case "STONE"->"자연 돌 채굴";case "COAL_ORE"->"자연 석탄광석 채굴";case "mill_bread"->"방앗간 밀빵 요리";default->materialName(q.target());};}
    void daily(Player p,long cents){safe(p,()->{register(p);survival(p);result(p,engine.execute(p.getUniqueId(),"DAILY","daily:"+day(),cents,Direction.CREDIT,"일일 보상",null,NO_ITEMS,()->{}),"오늘 보상 "+money(cents)+"을 받았습니다.");});}
    Change change(Player p,ItemStack[] before,ItemStack[] after){return new Change(){public void apply(){if(!Arrays.equals(before,p.getInventory().getStorageContents()))throw new IllegalStateException("거래 도중 가방이 변경됐습니다.");p.getInventory().setStorageContents(ItemPlans.copy(after));p.saveData();}public void restore(){p.getInventory().setStorageContents(ItemPlans.copy(before));p.saveData();}};}
    boolean legacyPlaced(org.bukkit.block.Block block){int[] marked=block.getChunk().getPersistentDataContainer().get(new NamespacedKey(plugin,"placed_ores"),PersistentDataType.INTEGER_ARRAY);int pos=(block.getY()-block.getWorld().getMinHeight())*256+(block.getZ()&15)*16+(block.getX()&15);return marked!=null&&Arrays.binarySearch(marked,pos)>=0;}
    void activity(Player p,Rules.Job job,int amount){if(store.job(p.getUniqueId())!=job)return;xp(p,job,amount);int rate=plugin.getConfig().getInt("직업."+job.label+".보상",job==Rules.Job.FARMER?2:job==Rules.Job.MINER?3:5);int cap=plugin.getConfig().getInt("직업.하루보상한도",300);store.accrueJob(p.getUniqueId(),day(),Money.cents(rate),Money.cents(cap));}
    void xp(Player p,Rules.Job job,int amount){if(store.job(p.getUniqueId())!=job)return;int old=store.progress(p.getUniqueId(),job).level();store.grantXp(p.getUniqueId(),job,amount,day(),250);int now=store.progress(p.getUniqueId(),job).level();if(now>old)p.sendMessage("§6"+job.label+" 숙련도 "+now+"레벨! /요리에서 해금된 요리를 확인하세요.");}
    @EventHandler public void join(PlayerJoinEvent e){safe(e.getPlayer(),()->register(e.getPlayer()));}
    @EventHandler public void quit(PlayerQuitEvent e){actions.remove(e.getPlayer().getUniqueId());registered.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){safe(e.getPlayer(),()->{var b=e.getBlockPlaced();store.markPlaced(b.getWorld().getUID().toString(),b.getX(),b.getY(),b.getZ());});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void harvest(BlockDropItemEvent e){Player p=e.getPlayer();if(p.getGameMode()!=GameMode.SURVIVAL||e.getItems().isEmpty())return;safe(p,()->{
        var b=e.getBlock();String name=e.getBlockState().getType().name();
        if(e.getBlockState().getBlockData() instanceof Ageable age&&age.getAge()==age.getMaximumAge()&&Set.of("WHEAT","CARROTS","POTATOES","BEETROOTS").contains(name)){activity(p,Rules.Job.FARMER,2);return;}
        if(legacyPlaced(b))store.markPlaced(b.getWorld().getUID().toString(),b.getX(),b.getY(),b.getZ());
        if((name.equals("STONE")||name.equals("DEEPSLATE")||(name.endsWith("_ORE")||name.equals("ANCIENT_DEBRIS")))&&store.harvestOnce(b.getWorld().getUID().toString(),b.getX(),b.getY(),b.getZ())){
            if(name.endsWith("_ORE")||name.equals("ANCIENT_DEBRIS")){activity(p,Rules.Job.MINER,4);legacy.advance(p.getUniqueId(),day(),"MINING",1);}else xp(p,Rules.Job.MINER,1);store.advance(p.getUniqueId(),day(),"MINE",name.equals("DEEPSLATE")?"STONE":name.replace("DEEPSLATE_",""),1);
        }
    });}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH||!(e.getCaught() instanceof Item item)||e.getPlayer().getGameMode()!=GameMode.SURVIVAL)return;safe(e.getPlayer(),()->{if(!Set.of(Material.COD,Material.SALMON,Material.TROPICAL_FISH,Material.PUFFERFISH).contains(item.getItemStack().getType()))return;activity(e.getPlayer(),Rules.Job.FISHER,8);legacy.advance(e.getPlayer().getUniqueId(),day(),"FISHING",1);store.advance(e.getPlayer().getUniqueId(),day(),"FISH",item.getItemStack().getType().name(),item.getItemStack().getAmount());});}
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Screen s))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!s.owner.equals(p.getUniqueId()) )return;Runnable action=s.buttons.get(e.getRawSlot());if(action==null)return;
        var quantity=s.quantities.get(e.getRawSlot());if(quantity!=null){if(!Set.of(ClickType.LEFT,ClickType.RIGHT,ClickType.SHIFT_LEFT,ClickType.SHIFT_RIGHT).contains(e.getClick()))return;int amount=e.isShiftClick()?16:e.isRightClick()?64:1;action=()->quantity.accept(amount);}else if(e.getClick()!=ClickType.LEFT)return;final Runnable chosen=action;
        plugin.getServer().getScheduler().runTask(plugin,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=s.inventory)return;if(s.source!=null&&!plugin.npcs.usable(p,s.source)){p.closeInventory();return;}Long old=actions.get(p.getUniqueId());long now=System.currentTimeMillis();if(old!=null&&now-old<300)return;actions.put(p.getUniqueId(),now);p.closeInventory();safe(p,chosen);});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
    void admin(CommandSender sender,String[] a){
        if(!sender.hasPermission("koreaneconomy.admin")){sender.sendMessage("§c운영자 전용입니다.");return;}
        try{if(a.length==0||a[0].equals("대기")){for(var r:store.unresolvedLog())sender.sendMessage("§e"+r.get("id")+" | "+store.playerName((String)r.get("actor"))+" | "+r.get("kind")+" | "+r.get("payload")+" | "+r.get("note"));sender.sendMessage("§7정리 전에 실제 잔액·가방을 확인하세요. 자동 환불·재지급은 하지 않습니다.");}
            else if(a.length==2&&a[0].equals("번호")){String id=store.receiptId(a[1]);sender.sendMessage(store.receipt(id).toString());}
            else if(a.length==2&&a[0].equals("조회")){UUID id=store.playerId(a[1]).orElseThrow(()->new StateStore.Denied("접속 이력이 없습니다."));for(var r:store.receiptLog(id,20))sender.sendMessage("§7"+r.get("id")+" | "+r.get("kind")+" | "+r.get("state")+" | "+r.get("payload"));}
            else if(a.length==4&&a[0].equals("정리")&&a[3].equals("확인")&&Set.of("완료","취소").contains(a[2])){String id=store.receiptId(a[1]);store.resolve(id,a[2].equals("완료"),sender.getName());lands.reload();sender.sendMessage("§a기록 상태만 정리했습니다. 돈·아이템은 변경하지 않았습니다.");}
            else sender.sendMessage("§e/경제기록 대기 · /경제기록 번호 거래번호 · /경제기록 조회 닉네임 · /경제기록 정리 번호 완료|취소 확인");
        }catch(RuntimeException e){sender.sendMessage("§c"+e.getMessage());}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pistonMove(BlockPistonExtendEvent e){trackMoved(e.getBlocks(),e.getDirection());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pistonPull(BlockPistonRetractEvent e){trackMoved(e.getBlocks(),e.getDirection().getOppositeFace());}
    void trackMoved(List<org.bukkit.block.Block> blocks,org.bukkit.block.BlockFace direction){store.transaction(()->{for(var b:blocks){var to=b.getRelative(direction);store.markPlaced(b.getWorld().getUID().toString(),b.getX(),b.getY(),b.getZ());store.markPlaced(to.getWorld().getUID().toString(),to.getX(),to.getY(),to.getZ());var other=b.getRelative(direction.getOppositeFace());store.markPlaced(other.getWorld().getUID().toString(),other.getX(),other.getY(),other.getZ());}return null;});}
    void close(){backups.close();audit.close();for(Player p:plugin.getServer().getOnlinePlayers())if(p.getOpenInventory().getTopInventory()!=null && p.getOpenInventory().getTopInventory().getHolder() instanceof Screen)p.closeInventory();store.close();}
}
