package kr.minecraft.survival.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
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
        getLogger().info("쉬프트+F 한글 메뉴 활성화. /kmenu 또는 /메뉴로도 열 수 있습니다.");
    }

    @Override public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
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
            sender.sendMessage("게임 안에서 /kmenu를 입력하세요.");
            return true;
        }
        if (!player.hasPermission("koreanmenu.use")) {
            player.sendMessage("§c메뉴를 사용할 권한이 없습니다.");
            return true;
        }
        open(player, false);
        return true;
    }

    private void open(Player player, boolean confirmation) {
        long now = System.currentTimeMillis();
        Long previous = opens.get(player.getUniqueId());
        if (!confirmation && previous != null && now - previous < 350) return;
        opens.put(player.getUniqueId(), now);
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            Menu menu = new Menu(this, player, confirmation);
            ItemStack border = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 54; i++) menu.inventory.setItem(i, border);
            if (confirmation) {
                menu.inventory.setItem(22, icon(Material.OAK_DOOR, "§e이곳을 집으로 저장할까요?",
                        "§7기존 default 집 위치가 바뀝니다.", "§7저장 시 현재 서 있는 위치를 사용합니다."));
                menu.inventory.setItem(30, icon(Material.LIME_CONCRETE, "§a확인 · 집 저장"));
                menu.inventory.setItem(32, icon(Material.RED_CONCRETE, "§c취소"));
            } else {
                menu.inventory.setItem(4, icon(Material.BOOK, "§6" + player.getName() + "님의 마을 수첩",
                        "§7쉬프트+F로 언제든 열기", "§7일반 F키는 보조 손 바꾸기"));
                menu.inventory.setItem(10, icon(Material.GOLD_INGOT, "§e내 돈 확인", "§7현재 잔액을 확인합니다."));
                menu.inventory.setItem(12, icon(Material.EMERALD, "§a송금 안내", "§7/pay 닉네임 금액", "§7예: /pay Steve 100"));
                menu.inventory.setItem(14, icon(Material.CHEST, "§6상점 안내", "§7유저 상점 만들기와 거래 방법"));
                menu.inventory.setItem(16, icon(Material.GOLDEN_SHOVEL, "§b내 땅 보호", "§7땅 보호와 친구 초대 방법"));
                menu.inventory.setItem(28, icon(Material.OAK_DOOR, "§a집 위치 저장", "§7확인 화면을 거쳐 저장합니다."));
                menu.inventory.setItem(30, icon(Material.RED_BED, "§a집으로 이동", "§7저장한 default 집으로 이동"));
                menu.inventory.setItem(32, icon(Material.COMPASS, "§b스폰으로 이동", "§7운영자가 설정한 스폰으로 이동"));
                menu.inventory.setItem(34, icon(Material.WRITABLE_BOOK, "§e서버 도움말", "§7거래·텔레포트·기본 명령어"));
            }
            menu.inventory.setItem(49, icon(Material.BARRIER, "§c닫기"));
            player.openInventory(menu.inventory);
        });
    }

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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 49 || (menu.confirmation && slot == 32)) {
            laterClose(player);
        } else if (menu.confirmation) {
            if (slot == 30) action(player, "Essentials", "essentials:sethome default");
        } else {
            switch (slot) {
                case 10 -> action(player, "Essentials", "essentials:balance");
                case 12 -> help(player, "§e송금: /pay 닉네임 금액", "§7예: /pay Steve 100", "§7송금 전에 닉네임과 금액을 확인하세요.");
                case 14 -> {
                    if (!enabled("QuickShop-Hikari")) help(player, "§cQuickShop-Hikari가 설치되거나 활성화되지 않았습니다.");
                    else help(player, "§6[유저 상점]", "§f판매할 아이템을 손에 들고 상자를 바라보세요.",
                            "§f/qs create 가격 §7으로 상점을 만듭니다.", "§7안내에 따라 판매/구매 수량을 입력하세요.",
                            "§7예: /qs create 100", "§7상점 거래는 해당 상점의 안내를 따라 진행하세요.");
                }
                case 16 -> {
                    if (!enabled("GriefPrevention")) help(player, "§cGriefPrevention이 설치되거나 활성화되지 않았습니다.", "§7추가 다운로드 안내를 확인하세요.");
                    else help(player, "§b[내 땅 보호]", "§f금 삽으로 대각선 두 모서리를 우클릭하세요.",
                            "§f/trust 닉네임 §7: 친구에게 건축 권한", "§f/untrust 닉네임 §7: 권한 회수", "§f/claimslist §7: 내 땅 목록");
                }
                case 28 -> {
                    if (enabled("Essentials")) open(player, true);
                    else help(player, "§cEssentialsX가 활성화되지 않았습니다.");
                }
                case 30 -> action(player, "Essentials", "essentials:home default");
                case 32 -> action(player, "EssentialsSpawn", "essentialsspawn:spawn");
                case 34 -> help(player, "§6[경제 야생 도움말]", "§f쉬프트+F 또는 /kmenu §7: 메뉴", "§f/bal §7: 내 돈",
                        "§f/pay 닉네임 금액 §7: 송금", "§f/tpa 닉네임 §7: 이동 요청", "§f/tpaccept §7: 이동 수락", "§7스폰·관리 지역에서는 운영 규칙을 따라주세요.");
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
        getServer().getScheduler().runTask(this, () -> { if (player.isOnline()) player.closeInventory(); });
    }

    private void action(Player player, String dependency, String command) {
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline() || !player.hasPermission("koreanmenu.use")) return;
            player.closeInventory();
            if (!enabled(dependency)) { player.sendMessage("§c" + dependency + "가 활성화되지 않았습니다."); return; }
            // Player dispatch preserves the target plugin's permissions, cooldowns and fees.
            player.performCommand(command);
        });
    }

    private void help(Player player, String... messages) {
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            player.closeInventory();
            for (String message : messages) player.sendMessage(message);
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { opens.remove(event.getPlayer().getUniqueId()); }
}
