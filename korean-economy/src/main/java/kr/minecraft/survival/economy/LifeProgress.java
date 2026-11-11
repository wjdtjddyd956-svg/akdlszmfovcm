package kr.minecraft.survival.economy;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Properties;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class LifeProgress implements Listener {
    enum Job {
        FARMER("농부", "다 자란 밀·당근·감자·사탕무 수확", Material.GOLDEN_HOE, 2),
        MINER("광부", "자연 광석 채굴", Material.IRON_PICKAXE, 3),
        FISHER("낚시꾼", "낚싯대로 물고기 낚기", Material.FISHING_ROD, 5);

        final String label, action;
        final Material icon;
        final int defaultPay;
        Job(String label, String action, Material icon, int defaultPay) {
            this.label = label;
            this.action = action;
            this.icon = icon;
            this.defaultPay = defaultPay;
        }
    }

    enum Quest {
        DELIVERY("밀 납품", "기본 밀을 가방에 준비하세요.", Material.WHEAT, 16, 48),
        MINING("광석 채굴", "자연 광석을 직접 캐세요.", Material.IRON_ORE, 12, 75),
        FISHING("물고기 낚기", "낚싯대로 물고기를 직접 낚으세요.", Material.COD, 6, 75);

        final String label, instruction;
        final Material icon;
        final int defaultGoal, defaultPay;
        Quest(String label, String instruction, Material icon, int defaultGoal, int defaultPay) {
            this.label = label;
            this.instruction = instruction;
            this.icon = icon;
            this.defaultGoal = defaultGoal;
            this.defaultPay = defaultPay;
        }
    }

    record Mission(int goal, int pay) {}
    record Settings(int jobCap, int levelActions, Map<Job, Integer> rates, Map<Quest, Mission> missions) {
        static Settings read(FileConfiguration config) {
            var rates = new EnumMap<Job, Integer>(Job.class);
            for (Job job : Job.values()) {
                rates.put(job, integer(config, "직업." + job.label + ".보상", job.defaultPay, 1, 1_000_000));
            }
            var missions = new EnumMap<Quest, Mission>(Quest.class);
            for (Quest quest : Quest.values()) {
                String path = "임무." + quest.label;
                missions.put(quest, new Mission(integer(config, path + ".목표", quest.defaultGoal, 1, 64),
                        integer(config, path + ".보상", quest.defaultPay, 1, 1_000_000)));
            }
            return new Settings(integer(config, "직업.하루보상한도", 300, 1, 1_000_000),
                    integer(config, "직업.레벨당활동", 50, 1, 100_000), Map.copyOf(rates), Map.copyOf(missions));
        }
    }

    static int integer(FileConfiguration config, String path, int fallback, int min, int max) {
        if (!config.contains(path)) return fallback;
        Object input = config.get(path);
        if (!(input instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
            throw new IllegalArgumentException(path + ": " + min + "~" + max + " 정수를 입력하세요.");
        }
        return number.intValue();
    }

    private record Panel(Inventory inventory, UUID owner, boolean jobs, Entity npc) {}

    // Bukkit requires the holder before the inventory is created.
    private static final class Menu implements InventoryHolder {
        final Panel panel;
        Menu(KoreanEconomyPlugin plugin, Player player, boolean jobs, Entity npc) {
            Inventory inventory = plugin.getServer().createInventory(this, 27, jobs ? "생활 직업" : "일일 임무");
            panel = new Panel(inventory, player.getUniqueId(), jobs, npc);
        }
        @Override public Inventory getInventory() { return panel.inventory(); }
    }

    private static final int[] CHOICES = { 11, 13, 15 };
    private static final DateTimeFormatter RESET = DateTimeFormatter.ofPattern("MM-dd HH:mm VV");
    private final KoreanEconomyPlugin plugin;
    private final LifeLedger ledger;
    Settings settings;

    LifeProgress(KoreanEconomyPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        ledger = new LifeLedger(plugin.getDataFolder().toPath().resolve("생활기록"));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, 40L, 40L);
    }

    void flush() { ledger.flush(plugin.getLogger()); }

    void closeMenus() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        }
    }

    private LocalDate today(Properties data) { return LifeLedger.day(data, LocalDate.now(plugin.zone())); }

    private Properties data(UUID player) throws IOException {
        Properties data = ledger.get(player);
        if (!LifeLedger.locked(data) && LifeLedger.advance(data, today(data))) ledger.queue(player, data);
        return data;
    }

    String reset(Player player) {
        try {
            ZonedDateTime next = today(ledger.get(player.getUniqueId())).plusDays(1).atStartOfDay(plugin.zone());
            return next.format(RESET);
        } catch (IOException | RuntimeException e) {
            return "운영자 확인 필요";
        }
    }

    long remaining(Player player, Material material, int cap) {
        try {
            Properties data = data(player.getUniqueId());
            if (LifeLedger.locked(data)) return 0;
            return Math.max(0, cap - LifeLedger.number(data, "daily.sale." + material.name()));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("판매 한도 조회 실패: " + player.getUniqueId() + " " + e);
            return 0;
        }
    }

    static boolean eligible(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    void activity(Player player, Job job) {
        if (!eligible(player) || !player.hasPermission("koreaneconomy.use")) return;
        UUID id = player.getUniqueId();
        try {
            Properties data = data(id);
            if (LifeLedger.locked(data)) return;
            boolean changed = false;
            if (job.name().equals(data.getProperty("job"))) {
                long earned = LifeLedger.number(data, "daily.job.earned");
                long value = Math.min(settings.rates().get(job), settings.jobCap() - earned);
                if (value > 0) {
                    LifeLedger.number(data, "daily.job.earned", earned + value);
                    LifeLedger.number(data, "job.owed", Math.addExact(LifeLedger.number(data, "job.owed"), value));
                    String xp = "job.xp." + job.name();
                    LifeLedger.number(data, xp, Math.addExact(LifeLedger.number(data, xp), 1));
                    changed = true;
                }
            }
            Quest quest = switch (job) {
                case MINER -> Quest.MINING;
                case FISHER -> Quest.FISHING;
                case FARMER -> null;
            };
            if (quest != null && "accepted".equals(data.getProperty(questKey(quest)))) {
                String key = questKey(quest) + ".progress";
                long count = LifeLedger.number(data, key);
                if (count < settings.missions().get(quest).goal()) {
                    LifeLedger.number(data, key, count + 1);
                    changed = true;
                }
            }
            if (changed) ledger.queue(id, data);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("생활 활동 기록 실패: " + id + " " + e);
        }
    }

    private static String questKey(Quest quest) { return "daily.quest." + quest.name(); }

    boolean trade(Player player, Material material, String label, int amount, int cap, boolean buying,
                  double price, ItemStack[] before, ItemStack[] after) {
        UUID id = player.getUniqueId();
        try {
            Properties original = data(id);
            if (checkLocked(player, original)) return false;
            Properties next = LifeLedger.copy(original);
            if (!buying) {
                String key = "daily.sale." + material.name();
                long sold = LifeLedger.number(original, key);
                if (amount > cap - sold) {
                    player.sendMessage("§c오늘 남은 매입 한도: " + Math.max(0, cap - sold) + "개 · 초기화 " + reset(player));
                    return false;
                }
                LifeLedger.number(next, key, sold + amount);
            }
            return transact(player, original, next, (buying ? "구매 " : "판매 ") + label + " " + amount,
                    buying ? -price : price, before, after);
        } catch (IOException | RuntimeException e) {
            failure(player, e);
            return false;
        }
    }

    private boolean checkLocked(Player player, Properties data) {
        if (!LifeLedger.locked(data)) return false;
        player.sendMessage("§c미결 거래가 있습니다. 운영자에게 /경제거래 확인을 요청하세요.");
        return true;
    }

    private boolean transact(Player player, Properties original, Properties next, String detail,
                             double delta, ItemStack[] before, ItemStack[] after) throws IOException {
        Economy wallet = plugin.wallet();
        if (wallet == null) {
            player.sendMessage("§cVault 경제가 연결되지 않았습니다.");
            return false;
        }
        if (delta < 0 && !wallet.has(player, -delta)) {
            player.sendMessage("§c잔액이 부족합니다.");
            return false;
        }
        UUID id = player.getUniqueId();
        Properties pending = ledger.reserve(id, original, next, detail, wallet.getBalance(player), delta);
        if (after != null) {
            try {
                player.getInventory().setStorageContents(after);
            } catch (RuntimeException e) {
                player.getInventory().setStorageContents(before);
                ledger.resolve(id, pending, false);
                throw e;
            }
        }
        EconomyResponse result = delta < 0 ? wallet.withdrawPlayer(player, -delta) : wallet.depositPlayer(player, delta);
        if (result == null) throw new IllegalStateException("Vault 응답 없음: " + pending.getProperty("pending.id"));
        if (!result.transactionSuccess()) {
            if (after != null) player.getInventory().setStorageContents(before);
            ledger.resolve(id, pending, false);
            player.sendMessage("§c경제 플러그인이 거래를 거절했습니다.");
            return false;
        }
        if (after != null) player.saveData();
        ledger.resolve(id, pending, true);
        return true;
    }

    void open(Player player, boolean jobs, Entity npc) {
        if (!player.isOnline() || !player.hasPermission("koreaneconomy.use")) return;
        try {
            Properties data = data(player.getUniqueId());
            Panel panel = new Menu(plugin, player, jobs, npc).panel;
            Inventory inv = panel.inventory();
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, plugin.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
            if (jobs) {
                long earned = LifeLedger.number(data, "daily.job.earned");
                long owed = LifeLedger.number(data, "job.owed");
                inv.setItem(4, plugin.icon(Material.BOOK, "§e직업 선택 · 진행도",
                        "§7보상은 아래 금괴에서 받습니다.", "§7하루 적립: " + plugin.money(earned) + " / " + plugin.money(settings.jobCap()),
                        "§7초기화 " + reset(player)));
                for (int i = 0; i < CHOICES.length; i++) {
                    Job job = Job.values()[i];
                    long xp = LifeLedger.number(data, "job.xp." + job.name());
                    int steps = settings.levelActions();
                    inv.setItem(CHOICES[i], plugin.icon(job.icon, "§a" + job.label + (job.name().equals(data.getProperty("job")) ? " §e[선택됨]" : ""),
                            "§7" + job.action, "§7활동당 " + plugin.money(settings.rates().get(job)),
                            "§7레벨 " + (xp / steps + 1) + " · 진행 " + xp % steps + "/" + steps,
                            "§7선택·변경은 하루에 한 번"));
                }
                inv.setItem(22, plugin.icon(Material.GOLD_INGOT, "§6적립 보상 받기", "§7받을 돈: " + plugin.money(owed)));
            } else {
                inv.setItem(4, plugin.icon(Material.BOOK, "§e일일 임무", "§7초기화 " + reset(player),
                        npc == null ? "§7임무 안내 NPC에서 수락하고 완료하세요." : "§7임무를 클릭해 수락 또는 완료하세요."));
                for (int i = 0; i < CHOICES.length; i++) {
                    Quest quest = Quest.values()[i];
                    String state = data.getProperty(questKey(quest), "new");
                    Mission mission = settings.missions().get(quest);
                    long count = state.equals("claimed") ? mission.goal() : quest == Quest.DELIVERY
                            ? countPlain(player, Material.WHEAT) : LifeLedger.number(data, questKey(quest) + ".progress");
                    String status = state.equals("claimed") ? "보상 수령 완료" : state.equals("new") ? "미수락"
                            : count >= mission.goal() ? "완료 가능" : "진행 중";
                    inv.setItem(CHOICES[i], plugin.icon(quest.icon, "§a" + quest.label + " §e[" + status + "]",
                            "§7" + quest.instruction, "§7진행 " + Math.min(count, mission.goal()) + "/" + mission.goal(),
                            "§7보상 " + plugin.money(mission.pay())));
                }
            }
            inv.setItem(26, plugin.icon(Material.BARRIER, "§c닫기"));
            player.openInventory(inv);
        } catch (IOException | RuntimeException e) {
            failure(player, e);
        }
    }

    private int countPlain(Player player, Material material) {
        ItemStack plain = new ItemStack(material);
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(plain)) count += item.getAmount();
        }
        return count;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        Panel panel = menu.panel;
        if (!(event.getWhoClicked() instanceof Player player) || !panel.owner().equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        int choice = -1;
        for (int i = 0; i < CHOICES.length; i++) if (slot == CHOICES[i]) choice = i;
        int index = choice;
        if (slot != 26 && index < 0 && !(panel.jobs() && slot == 22)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != panel.inventory()
                    || !player.hasPermission("koreaneconomy.use")) return;
            if (slot == 26) {
                player.closeInventory();
                return;
            }
            if (!eligible(player)) {
                player.sendMessage("§c생존 또는 모험 모드에서 이용하세요.");
                return;
            }
            if (!panel.jobs() && (panel.npc() == null || !plugin.npcs.usable(player, panel.npc()))) {
                player.sendMessage("§e임무 안내 NPC 가까이에서 우클릭해 수락·완료하세요.");
                return;
            }
            try {
                Properties original = data(player.getUniqueId());
                if (checkLocked(player, original)) return;
                Properties next = LifeLedger.copy(original);
                if (panel.jobs()) jobClick(player, slot, index, original, next);
                else questClick(player, Quest.values()[index], original, next);
                open(player, panel.jobs(), panel.npc());
            } catch (IOException | RuntimeException e) {
                failure(player, e);
            }
        });
    }

    private void jobClick(Player player, int slot, int index, Properties original, Properties next) throws IOException {
        if (slot == 22) {
            long owed = LifeLedger.number(original, "job.owed");
            if (owed <= 0) {
                player.sendMessage("§e아직 적립된 보상이 없습니다.");
                return;
            }
            LifeLedger.number(next, "job.owed", 0);
            if (transact(player, original, next, "직업 적립 보상", owed, null, null)) {
                player.sendMessage("§a직업 보상 " + plugin.money(owed) + "을 받았습니다.");
            }
            return;
        }
        Job job = Job.values()[index];
        if (job.name().equals(original.getProperty("job"))) return;
        if (original.containsKey("daily.job.changed")) {
            player.sendMessage("§e오늘은 이미 직업을 선택하거나 변경했습니다.");
            return;
        }
        next.setProperty("job", job.name());
        next.setProperty("daily.job.changed", "true");
        ledger.persist(player.getUniqueId(), next, true);
        player.sendMessage("§a직업을 " + job.label + "(으)로 선택했습니다.");
    }

    private void questClick(Player player, Quest quest, Properties original, Properties next) throws IOException {
        String key = questKey(quest);
        String state = original.getProperty(key, "new");
        if (state.equals("claimed")) return;
        if (state.equals("new")) {
            next.setProperty(key, "accepted");
            ledger.persist(player.getUniqueId(), next, true);
            player.sendMessage("§a일일 임무를 받았습니다: " + quest.label);
            return;
        }
        Mission mission = settings.missions().get(quest);
        ItemStack[] before = null, after = null;
        if (quest == Quest.DELIVERY) {
            before = player.getInventory().getStorageContents();
            after = InventoryPlan.plan(before, Material.WHEAT, mission.goal(), false);
            if (after == null) {
                player.sendMessage("§e납품할 기본 밀이 부족합니다.");
                return;
            }
        } else if (LifeLedger.number(original, key + ".progress") < mission.goal()) {
            player.sendMessage("§e임무 목표를 먼저 채우세요.");
            return;
        }
        next.setProperty(key, "claimed");
        if (transact(player, original, next, "일일 임무 " + quest.label + " " + mission.goal(), mission.pay(), before, after)) {
            player.sendMessage("§a임무 완료: " + quest.label + " · " + plugin.money(mission.pay()));
        }
    }

    void review(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§c운영자만 미결 거래를 확인할 수 있습니다.");
            return;
        }
        if (args.length != 2 || !List.of("확인", "완료", "취소").contains(args[1])) {
            sender.sendMessage("§e/경제거래 플레이어UUID 확인|완료|취소");
            return;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            Properties data = ledger.get(id);
            if (!LifeLedger.locked(data)) {
                sender.sendMessage("§e미결 거래가 없습니다.");
                return;
            }
            sender.sendMessage("§e" + data.getProperty("pending.detail") + " · 거래 전 잔액 " +
                    data.getProperty("pending.balance") + " · 예정 증감 " + data.getProperty("pending.delta"));
            if (!args[1].equals("확인")) {
                ledger.resolve(id, data, args[1].equals("완료"));
                plugin.getLogger().warning("미결 거래 수동 처리: " + sender.getName() + " " + id + " " + args[1]);
                sender.sendMessage("§a기록을 반영했습니다. 이 명령은 돈과 아이템을 이동하지 않습니다.");
            }
        } catch (IOException | RuntimeException e) {
            sender.sendMessage("§c미결 거래를 처리할 수 없습니다. UUID와 서버 로그를 확인하세요.");
            plugin.getLogger().warning(e.toString());
        }
    }

    private void failure(Player player, Exception e) {
        plugin.getLogger().severe("생활 거래/기록 확인 필요: " + player.getUniqueId() + " " + e);
        player.sendMessage("§c처리 중 오류가 발생했습니다. 운영자에게 기록 확인을 요청하세요.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { ledger.unload(event.getPlayer().getUniqueId(), plugin.getLogger()); }
}
