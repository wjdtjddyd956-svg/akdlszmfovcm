package kr.minecraft.rpg.koreancommands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class KoreanCommandsPlugin extends JavaPlugin implements Listener {
    private final CommandTranslator translator = new CommandTranslator();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("한글 명령어 별칭을 활성화했습니다.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.equals("/거래소") || message.equals("/거래소 도움말")) return;
        CommandTranslator.Result result = translator.translate(message);
        if (result.handled()) {
            if (!available(event.getPlayer(), result.command())) { event.setCancelled(true); return; }
            event.setMessage(result.command());
            if (getConfig().getBoolean("변환-명령어-표시", false)) {
                event.getPlayer().sendMessage(ChatColor.GRAY + "실행: " + result.command());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("거래소") && (args.length==0 || args[0].equals("도움말"))) {
            sender.sendMessage(ChatColor.GOLD + "[유저 거래소] 상자를 바라보고 사용하세요.");
            sender.sendMessage(ChatColor.YELLOW + "/거래소 생성 가격 · /거래소 가격 금액");
            sender.sendMessage(ChatColor.YELLOW + "/거래소 판매 · /거래소 매입 · /거래소 수량 개수");
            sender.sendMessage(ChatColor.YELLOW + "/거래소 이름 이름 · /거래소 삭제");
            sender.sendMessage(ChatColor.YELLOW + "/거래소 직원 추가 닉네임 · /거래소 직원 삭제 닉네임");
            sender.sendMessage(ChatColor.YELLOW + "/거래소 직원 목록 · /거래소 기록 · /거래소 둘러보기");
            return true;
        }
        if (command.getName().equalsIgnoreCase("한글명령어")) {
            sendHelp(sender);
            return true;
        }
        if (CommandTranslator.commandNames().contains(command.getName())) {
            String input = "/" + command.getName() + (args.length == 0 ? "" : " " + String.join(" ", args));
            CommandTranslator.Result result = translator.translate(input);
            if (!result.handled()) {
                sender.sendMessage(ChatColor.RED + "알 수 없는 명령입니다. /한글명령어를 확인하세요.");
                return true;
            }
            if (!available(sender, result.command())) return true;
            getServer().dispatchCommand(sender, result.command().substring(1));
            return true;
        }
        return false;
    }

    private boolean available(CommandSender sender, String command) {
        String plugin = CommandTranslator.requiredPlugin(command);
        if (plugin == null || getServer().getPluginManager().isPluginEnabled(plugin)) return true;
        sender.sendMessage(ChatColor.RED + "연결 기능이 설치되거나 활성화되지 않았습니다: " + plugin);
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        var completions = translator.complete(event.getBuffer());
        if (!completions.isEmpty()) {
            event.setCompletions(completions);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "[한글 명령어] 사용 예시");
        sender.sendMessage(ChatColor.YELLOW + "/메뉴, /돈, /돈순위, /송금 닉네임 금액");
        sender.sendMessage(ChatColor.YELLOW + "/상점, /일일보상, /거래소 생성 가격");
        sender.sendMessage(ChatColor.YELLOW + "/집저장, /집, /스폰, /이동요청 닉네임, /이동수락");
        sender.sendMessage(ChatColor.YELLOW + "/거래소 판매, /거래소 매입, /거래소 가격 금액");
        sender.sendMessage(ChatColor.YELLOW + "/땅목록, /땅초대 닉네임, /땅추방 닉네임");
        sender.sendMessage(ChatColor.YELLOW + "//도끼, //첫점, //둘째점");
        sender.sendMessage(ChatColor.YELLOW + "//채우기 돌, //바꾸기 흙 잔디블록");
        sender.sendMessage(ChatColor.YELLOW + "/영역 생성 마을, /영역 정보 마을");
        sender.sendMessage(ChatColor.YELLOW + "/영역 설정 마을 피브이피 거부");
        sender.sendMessage(ChatColor.GRAY + "영어 명령어도 기존과 동일하게 사용할 수 있습니다.");
    }
}
