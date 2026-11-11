package kr.minecraft.survival.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** All actions run as the player; this plugin never grants operator privileges. */
public class KoreanMenuPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> opens = new HashMap<>();

    static final class Menu implements InventoryHolder {
        final UUID owner;
        final boolean confirmation;
        final Inventory inventory;
        Menu(KoreanMenuPlugin plugin, Player player, boolean confirmation) {
            this.owner = player.getUniqueId();
            this.confirmation = confirmation;
            this.inventory = plugin.getServer().createInventory(this, 54,
                    confirmation ? "집 위치 저장 확인" : "경제 야생 | 마을 수첩");
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("쉬프트+F 한글 메뉴 활성화. /메뉴로도 열 수 있습니다.");
    }

    @Override public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory()!=null && p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
        }
        opens.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("koreanmenu.use")) return;
        event.setCancelled(true);
        open(player, false);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("게임 안에서 /메뉴를 입력하세요.");
            return true;
        }
        if (!player.hasPermission("koreanmenu.use")) {
            player.sendMessage("§c메뉴를 사용할 권한이 없습니다.");
            return true;
        }
        open(player, false);
        return true;
    }

    private void open(Player player, boolean confirmation) {open(player,confirmation,false);}
    public void openFromNavigation(Player player){if(player.hasPermission("koreanmenu.use"))open(player,false,true);}
    private void open(Player player, boolean confirmation,boolean navigation) {
        long now = System.currentTimeMillis();
        Long previous = opens.get(player.getUniqueId());
        if (!confirmation && !navigation && previous != null && now - previous < 350) return;
        opens.put(player.getUniqueId(), now);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            Menu menu = new Menu(this, player, confirmation);
            ItemStack border = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 54; i++) menu.inventory.setItem(i, border);
            if (confirmation) {
                menu.inventory.setItem(22, icon(Material.OAK_DOOR, "§e이곳을 집으로 저장할까요?",
                        "§7기존 기본 집 위치가 바뀝니다.", "§7저장 시 현재 서 있는 위치를 사용합니다."));
                menu.inventory.setItem(30, icon(Material.LIME_CONCRETE, "§a확인 · 집 저장"));
                menu.inventory.setItem(32, icon(Material.RED_CONCRETE, "§c취소"));
            } else {
                menu.inventory.setItem(4, icon(Material.BOOK, "§6" + player.getName() + "님의 마을 수첩",
                        "§7쉬프트+F로 언제든 열기", "§7일반 F키는 보조 손 바꾸기"));
                menu.inventory.setItem(10, icon(Material.GOLD_INGOT, "§e내 돈 확인", "§7현재 잔액을 확인합니다."));
                menu.inventory.setItem(12, icon(Material.EMERALD, "§a송금 안내", "§7/송금 닉네임 금액", "§7예: /송금 Steve 100"));
                menu.inventory.setItem(14, icon(Material.CHEST, "§6NPC 상점 안내", "§7마을의 잡화·농산물·요리 상인을 찾아가세요.","§7NPC 우클릭으로 구매·판매합니다."));
                menu.inventory.setItem(16, icon(Material.GOLDEN_SHOVEL, "§b내 땅 보호", "§7땅 보호와 친구 초대 방법"));
                menu.inventory.setItem(28, icon(Material.OAK_DOOR, "§a집 위치 저장", "§7확인 화면을 거쳐 저장합니다."));
                menu.inventory.setItem(22, icon(Material.SUNFLOWER, "§e일일 보상", "§7하루에 한 번 보상을 받습니다."));
                menu.inventory.setItem(24, icon(Material.ENDER_CHEST, "§6유저 거래소", "§7유저 상점 만들기와 거래 방법"));
                menu.inventory.setItem(30, icon(Material.RED_BED, "§a집으로 이동", "§7저장한 기본 집으로 이동"));
                menu.inventory.setItem(32, icon(Material.COMPASS, "§b스폰으로 이동", "§7운영자가 설정한 스폰으로 이동"));
                menu.inventory.setItem(37, icon(Material.WHEAT,"§a직업 선택·숙련도"));
                menu.inventory.setItem(39, icon(Material.CAMPFIRE,"§6요리 만들기"));
                menu.inventory.setItem(41, icon(Material.ANVIL,"§b도구 강화"));
                menu.inventory.setItem(43, icon(Material.WRITABLE_BOOK,"§e오늘의 임무"));
                menu.inventory.setItem(34, icon(Material.WRITABLE_BOOK, "§e서버 도움말", "§7거래·텔레포트·기본 명령어"));
            }
            menu.inventory.setItem(49, icon(Material.BARRIER, "§c닫기", "§7왼쪽 클릭: 메뉴 닫기"));
            if(!confirmation){
                menu.inventory.setItem(20,icon(Material.BOOK,"§b아이템 도감",collectionLine(player),"§7등록 가능·미보유·등록 완료를 구분합니다.","§e왼쪽 클릭: 수집 목록"));
                menu.inventory.setItem(26,icon(Material.GOLDEN_CHESTPLATE,"§6수집 업적","§7수집 목표와 실제 달성 진행률","§e왼쪽 클릭: 업적 보기"));
                menu.inventory.setItem(25,icon(Material.WHEAT,"§a커스텀 농사","§7딸기 · 토마토 · 옥수수 · 쌀 · 오이","§7씨앗은 농산물·잡화 NPC에서 구매","§e왼쪽 클릭: 농장 안내"));
                for(int row:new int[]{9,18,27,36})for(int column:new int[]{0,8})if(row+column!=26)menu.inventory.setItem(row+column,icon(Material.CYAN_STAINED_GLASS_PANE," "));
                menu.inventory.setItem(9,icon(Material.GOLD_NUGGET,"§6경제 · 거래","§7지갑 · 송금 · NPC 상점 · 땅"));
                menu.inventory.setItem(18,icon(Material.BOOK,"§b수집 · 농장","§7도감 · 일일 보상 · 거래소 · 농장 · 업적"));
                menu.inventory.setItem(27,icon(Material.COMPASS,"§a이동 · 생활","§7집 저장 · 귀가 · 스폰 · 도움말"));
                menu.inventory.setItem(36,icon(Material.EXPERIENCE_BOTTLE,"§d성장 · 직업","§7직업 · 요리 · 강화 · 임무"));
                menu.inventory.setItem(51,icon(Material.PAINTING,"§b메뉴 리소스팩","§7전용 아이콘으로 기능을 구분합니다.","§e왼쪽 클릭: 미리보기·팩 받기·설치 확인"));
                Map<Integer,String> models=Map.ofEntries(Map.entry(4,"business_ledger"),Map.entry(10,"wallet"),Map.entry(12,"transfer"),Map.entry(14,"shop"),Map.entry(16,"land_deed"),Map.entry(20,"ui/codex"),Map.entry(22,"daily_reward"),Map.entry(24,"seller_crate"),Map.entry(25,"ui/custom_farm"),Map.entry(26,"ui/achievements"),Map.entry(28,"home_key"),Map.entry(30,"home_door"),Map.entry(32,"city_spawn"),Map.entry(34,"help_book"),Map.entry(37,"jobs_case"),Map.entry(39,"cooking_pan"),Map.entry(41,"upgrade_anvil"),Map.entry(43,"quest_clipboard"),Map.entry(49,"close"),Map.entry(51,"ui/effects"));
                models.forEach((slot,id)->tag(menu.inventory,slot,id));
                Map<Integer,String> dependencies=Map.ofEntries(Map.entry(10,"Essentials"),Map.entry(12,"Essentials"),Map.entry(14,"KoreanEconomy"),Map.entry(16,"KoreanEconomy"),Map.entry(20,"KoreanCodex"),Map.entry(22,"KoreanEconomy"),Map.entry(24,"QuickShop-Hikari"),Map.entry(25,"KoreanFarming"),Map.entry(26,"KoreanCodex"),Map.entry(28,"Essentials"),Map.entry(30,"Essentials"),Map.entry(32,"EssentialsSpawn"),Map.entry(37,"KoreanEconomy"),Map.entry(39,"KoreanEconomy"),Map.entry(41,"KoreanEconomy"),Map.entry(43,"KoreanEconomy"),Map.entry(51,"CityTextures"));
                dependencies.forEach((slot,dependency)->{if(!enabled(dependency)){ItemStack item=menu.inventory.getItem(slot);ItemMeta m=item.getItemMeta();m.setDisplayName("§8"+org.bukkit.ChatColor.stripColor(m.getDisplayName())+" §7[준비 중]");m.setLore(List.of("§7이 기능이 아직 활성화되지 않았습니다.","§7운영자에게 확인을 요청하세요."));item.setItemMeta(m);menu.inventory.setItem(slot,item);}});
            }else{tag(menu.inventory,30,"confirm");tag(menu.inventory,32,"close");tag(menu.inventory,49,"close");}
            player.openInventory(menu.inventory);
        });
    }

    static void tag(Inventory inv,int slot,String id){ItemStack item=inv.getItem(slot);if(item==null)return;ItemMeta m=item.getItemMeta();m.getPersistentDataContainer().set(new NamespacedKey("tycoon","ui_icon"),PersistentDataType.STRING,id);item.setItemMeta(m);inv.setItem(slot,item);}
    private String collectionLine(Player player){var plugin=getServer().getPluginManager().getPlugin("KoreanCodex");if(plugin==null||!plugin.isEnabled())return "§7도감 준비 중";try{int[] counts=(int[])plugin.getClass().getMethod("collectionSummary",Player.class).invoke(plugin,player);return "§7등록 "+counts[0]+" / "+counts[1]+" · 등록 가능 "+counts[2]+"종";}catch(ReflectiveOperationException ex){return "§7수집 현황과 작은 영구 보너스";}}
    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        // Includes bottom-inventory shift-click, number keys, double-click and offhand swaps.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!menu.owner.equals(player.getUniqueId()) || !player.hasPermission("koreanmenu.use")) return;
        if(event.getClick()!=org.bukkit.event.inventory.ClickType.LEFT)return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 49 || (menu.confirmation && slot == 32)) {
            laterClose(player);
        } else if (menu.confirmation) {
            if (slot == 30) action(player, "Essentials", "essentials:sethome default");
        } else {
            switch (slot) {
                case 10 -> action(player, "Essentials", "essentials:balance");
                case 12 -> help(player, "§e송금: /송금 닉네임 금액", "§7예: /송금 Steve 100", "§7송금 전에 닉네임과 금액을 확인하세요.");
                case 14 -> help(player,"§6[NPC 상점]", "§f마을의 잡화·농산물·요리 NPC를 우클릭하세요.","§7농장 씨앗·수확물은 NPC의 농장 상점 버튼에서 거래합니다.");
                case 20 -> action(player,"KoreanCodex","도감");
                case 25 -> action(player,"KoreanFarming","농사");
                case 26 -> action(player,"KoreanCodex","업적");
                case 51 -> action(player,"CityTextures","도시팩 메뉴미리보기");
                case 22 -> action(player, "KoreanEconomy", "일일보상");
                case 24 -> {
                    if (!enabled("QuickShop-Hikari") || !enabled("KoreanCommands")) help(player, "§c유저 거래소 기능이 설치되거나 활성화되지 않았습니다.");
                    else help(player, "§6[유저 상점]", "§f판매할 아이템을 손에 들고 상자를 바라보세요.",
                            "§f/거래소 생성 가격 §7으로 상점을 만듭니다.", "§7안내에 따라 판매/구매 수량을 입력하세요.",
                            "§7예: /거래소 생성 100", "§7상점 거래는 해당 상점의 안내를 따라 진행하세요.");
                }
                case 37 -> action(player, "KoreanEconomy", "직업");
                case 39 -> action(player, "KoreanEconomy", "요리");
                case 41 -> action(player, "KoreanEconomy", "강화");
                case 43 -> action(player, "KoreanEconomy", "임무");
                case 16 -> {
                    action(player,"KoreanEconomy","땅보호");
                }
                case 28 -> {
                    if (enabled("Essentials")) open(player, true);
                    else help(player, "§c집 저장 기능이 활성화되지 않았습니다.");
                }
                case 30 -> action(player, "Essentials", "essentials:home default");
                case 32 -> action(player, "EssentialsSpawn", "essentialsspawn:spawn");
                case 34 -> help(player, "§6[경제 야생 도움말]", "§f쉬프트+F 또는 /메뉴 §7: 메뉴", "§f/돈 §7: 내 돈",
                        "§f/송금 닉네임 금액 §7: 송금", "§f/이동요청 닉네임 §7: 이동 요청", "§f/이동수락 §7: 이동 수락", "§f/상점 · /일일보상 · /경제안내", "§7스폰·관리 지역에서는 운영 규칙을 따라주세요.");
                default -> { }
            }
        }
    }

    private boolean enabled(String name) {
        return getServer().getPluginManager().isPluginEnabled(name);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private void laterClose(Player player) {
        Inventory expected=player.getOpenInventory().getTopInventory();
        getServer().getScheduler().runTask(this, () -> { if (player.isOnline()&&player.getOpenInventory().getTopInventory()==expected) player.closeInventory(); });
    }

    private void action(Player player, String dependency, String command) {
        Inventory expected=player.getOpenInventory().getTopInventory();
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline() || !player.hasPermission("koreanmenu.use") || player.getOpenInventory().getTopInventory()!=expected) return;
            player.closeInventory();
            if (!enabled(dependency)) { player.sendMessage("§c선택한 기능이 활성화되지 않았습니다. 운영자에게 문의하세요."); return; }
            // Player dispatch preserves the target plugin's permissions, cooldowns and fees.
            player.performCommand(command);
        });
    }

    private void help(Player player, String... messages) {
        Inventory expected=player.getOpenInventory().getTopInventory();
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()||player.getOpenInventory().getTopInventory()!=expected) return;
            player.closeInventory();
            for (String message : messages) player.sendMessage(message);
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { opens.remove(event.getPlayer().getUniqueId()); }
}
