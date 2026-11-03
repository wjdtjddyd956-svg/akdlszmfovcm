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
        CommandTranslator.Result result = translator.translate(message);
        if (result.handled()) {
            event.setMessage(result.command());
            if (getConfig().getBoolean("변환-명령어-표시", false)) {
                event.getPlayer().sendMessage(ChatColor.GRAY + "실행: " + result.command());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("한글명령어")) {
            sendHelp(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("영역")) {
            String input = "/영역" + (args.length == 0 ? "" : " " + String.join(" ", args));
            CommandTranslator.Result result = translator.translate(input);
            if (!result.handled()) {
                sender.sendMessage(ChatColor.RED + "알 수 없는 한글 영역 명령입니다. /한글명령어를 확인하세요.");
                return true;
            }
            return getServer().dispatchCommand(sender, result.command().substring(1));
        }
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
        sender.sendMessage(ChatColor.YELLOW + "//도끼, //첫점, //둘째점");
        sender.sendMessage(ChatColor.YELLOW + "//채우기 돌, //바꾸기 흙 잔디블록");
        sender.sendMessage(ChatColor.YELLOW + "/영역 생성 마을, /영역 정보 마을");
        sender.sendMessage(ChatColor.YELLOW + "/영역 설정 마을 피브이피 거부");
        sender.sendMessage(ChatColor.GRAY + "영어 명령어도 기존과 동일하게 사용할 수 있습니다.");
    }
}
