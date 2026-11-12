package kr.minecraft.survival.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ServerTools {
    static final Set<String> COMMANDS = Set.of("땅보호", "기록조회", "복구", "백업");
    private final KoreanEconomyPlugin plugin;

    ServerTools(KoreanEconomyPlugin plugin) { this.plugin = plugin; }

    void command(CommandSender sender, String name, String[] args) {
        if (name.equals("땅보호")) {
            land(sender, args);
            return;
        }
        if (!sender.isOp()) {
            sender.sendMessage("§c운영자만 사용할 수 있습니다.");
            return;
        }
        if (name.equals("백업")) {
            backup(sender, args);
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§e게임 안에서 확인할 지역에 선 뒤 사용하세요.");
            return;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("prism")) {
            sender.sendMessage("§c기록 플러그인 Prism이 활성화되지 않았습니다.");
            return;
        }
        audit(sender, name, args);
    }

    private void land(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§e게임 안에서 /땅보호를 사용하세요.");
            return;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            player.sendMessage("§c땅 보호 기능이 활성화되지 않았습니다.");
            return;
        }
        if (args.length == 1 && args[0].equals("확인")) {
            var claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
                player.sendMessage("§e이 위치에는 유저 땅 보호가 없습니다. 스폰은 별도로 보호됩니다.");
                return;
            }
            var min = claim.getLesserBoundaryCorner();
            var max = claim.getGreaterBoundaryCorner();
            player.sendMessage("§a보호 주인: " + (claim.getOwnerID() == null ? "운영진" : claim.getOwnerName()));
            player.sendMessage("§e범위 X " + min.getBlockX() + "~" + max.getBlockX()
                    + " · Z " + min.getBlockZ() + "~" + max.getBlockZ());
            player.sendMessage("§7막대기로 블록을 우클릭하면 경계를 확인할 수 있습니다.");
            return;
        }
        if (args.length == 1 && args[0].equals("목록")) {
            dispatch(player, "griefprevention:claimslist");
            return;
        }
        if (args.length == 2 && playerName(args[1])) {
            String target = switch (args[0]) {
                case "초대" -> "trust";
                case "상자초대" -> "containertrust";
                case "회수" -> "untrust";
                default -> null;
            };
            if (target != null) {
                var claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
                if (claim == null || !player.getUniqueId().equals(claim.getOwnerID())) {
                    player.sendMessage("§c권한을 관리할 자기 땅 안에 서세요.");
                    return;
                }
                // GriefPrevention checks ownership, claim permissions and any delegated trust.
                dispatch(player, "griefprevention:" + target + " " + args[1]);
                return;
            }
        }
        if (args.length == 2 && args[0].equals("해제") && args[1].equals("확인")) {
            var claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim != null && player.getUniqueId().equals(claim.getOwnerID())) dispatch(player, "griefprevention:abandonclaim");
            else player.sendMessage("§c현재 위치에서 자기 땅만 해제할 수 있습니다.");
            return;
        }
        player.sendMessage("§6[땅 보호] 금 삽으로 대각선 두 모서리를 우클릭해 지정하세요.");
        player.sendMessage("§e/땅보호 확인 §7현재 범위 · §e/땅보호 목록 §7내 땅 목록");
        player.sendMessage("§e/땅보호 초대 닉네임 §7건축·상자 · §e/땅보호 상자초대 닉네임 §7상자 이용");
        player.sendMessage("§e/땅보호 회수 닉네임 §7권한 회수 · §e/땅보호 해제 확인 §7내 보호 해제");
        player.sendMessage("§7집과 상자를 보호 범위 안에 두세요. 스폰 보호도 계속 적용됩니다.");
    }

    private void audit(CommandSender sender, String name, String[] args) {
        boolean rollback = name.equals("복구");
        if (rollback && args.length == 1 && List.of("적용", "취소", "되돌리기").contains(args[0])) {
            dispatch(sender, "pr " + switch (args[0]) {
                case "적용" -> "preview-apply";
                case "취소" -> "preview-cancel";
                default -> "undo";
            });
            return;
        }
        if (!rollback && args.length == 2 && args[0].equals("페이지") && args[1].matches("[1-9][0-9]{0,3}")) {
            dispatch(sender, "pr page " + args[1]);
            return;
        }
        int offset = rollback ? 1 : 0;
        if ((args.length == offset + 3 || args.length == offset + 4)
                && (!rollback || args[0].equals("미리보기")) && playerName(args[offset])
                && args[offset + 1].matches("[1-9][0-9]{0,3}[mhd]")
                && args[offset + 2].matches("[1-9][0-9]{0,2}")) {
            int radius = Integer.parseInt(args[offset + 2]);
            String filter = args.length == offset + 3 ? "전체" : args[offset + 3];
            String actions = switch (filter) {
                case "전체" -> "block-place,block-break,item-insert,item-remove";
                case "블록" -> "block-place,block-break";
                case "상자" -> "item-insert,item-remove";
                default -> null;
            };
            if (actions != null && radius <= 128) {
                if (rollback) sender.sendMessage("§e미리보기에서는 블록 변화를 보여줍니다. 상자 내역은 /기록조회로 함께 확인하세요.");
                dispatch(sender, "pr " + (rollback ? "preview-rollback" : "lookup") + " p:" + args[offset]
                        + " since:" + args[offset + 1] + " r:" + radius + " a:" + actions);
                return;
            }
        }
        if (rollback) {
            sender.sendMessage("§e/복구 미리보기 닉네임 1h 16 [전체|블록|상자]");
            sender.sendMessage("§e/복구 적용 · /복구 취소 · /복구 되돌리기");
        } else {
            sender.sendMessage("§e/기록조회 닉네임 1h 16 [전체|블록|상자] · /기록조회 페이지 2");
        }
        sender.sendMessage("§7시간 단위 m(분)·h(시간)·d(일), 반경 1~128블록");
    }

    private void backup(CommandSender sender, String[] args) {
        Path control = plugin.getDataFolder().toPath().getParent().toAbsolutePath().getParent().resolve("life-control");
        if (args.length == 0 || args.length == 1 && args[0].equals("상태")) {
            try {
                Path status = control.resolve("status.txt");
                sender.sendMessage(Files.isRegularFile(status) ? Files.readString(status).strip()
                        : "§e아직 Windows 백업 관리자가 상태를 기록하지 않았습니다.");
            } catch (IOException e) {
                sender.sendMessage("§c백업 상태를 읽을 수 없습니다.");
            }
            return;
        }
        if (args.length != 1 || !args[0].equals("지금")) {
            sender.sendMessage("§e/백업 상태 · /백업 지금");
            return;
        }
        if (System.getProperty("koreanlife.managed") == null || !Files.isRegularFile(control.resolve("active.txt"))) {
            sender.sendMessage("§cWindows 백업 관리자(서버시작.cmd)로 서버를 먼저 시작하세요.");
            return;
        }
        try {
            Files.writeString(control.resolve("backup.request"), Instant.now() + " " + sender.getName(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            sender.sendMessage("§a백업 요청을 보냈습니다. 안내 후 월드를 저장하고 서버를 재시작합니다.");
        } catch (java.nio.file.FileAlreadyExistsException e) {
            sender.sendMessage("§e이미 백업 요청이 있습니다.");
        } catch (IOException e) {
            sender.sendMessage("§c백업 요청을 저장할 수 없습니다.");
            plugin.getLogger().warning(e.toString());
        }
    }

    private static boolean playerName(String value) { return value.matches("[A-Za-z0-9_]{1,16}"); }

    private void dispatch(CommandSender sender, String command) {
        try {
            if (!plugin.getServer().dispatchCommand(sender, command)) sender.sendMessage("§c연결된 명령을 실행할 수 없습니다.");
        } catch (RuntimeException e) {
            sender.sendMessage("§c명령을 실행할 수 없습니다. 서버 로그를 확인하세요.");
            plugin.getLogger().warning(e.toString());
        }
    }
}
