import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
/** Isolated localhost/offline instrumentation, never included in releases. */
public class BridgeProbe extends JavaPlugin {
    @Override public void onEnable(){if(!getServer().getIp().equals("127.0.0.1")||getServer().getPort()!=25566||getServer().getOnlineMode())getServer().getPluginManager().disablePlugin(this);}
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(!(s instanceof RemoteConsoleCommandSender))return true;
        Player p=a.length==0?null:getServer().getPlayerExact(a[0]);if(p==null){s.sendMessage("offline");return true;}
        Map<String,Object> result=new LinkedHashMap<>();result.put("op",p.isOp());
        for(String key:List.of("koreaneconomy.admin","koreaneconomy.operations","essentials.eco","essentials.give","griefprevention.adminclaims","worldguard.region.bypass.world","luckperms.user.parent.add","tab.scoreboard.toggle","koreanbridge.use"))result.put(key,p.hasPermission(key));
        var api=me.neznamy.tab.api.TabAPI.getInstance();var player=api.getPlayer(p.getUniqueId());
        result.put("scoreboardVisible",api.getScoreboardManager().hasScoreboardVisible(player));
        var board=api.getScoreboardManager().getActiveScoreboard(player);result.put("scoreboard",board==null?null:board.getName());
        s.sendMessage(new com.google.gson.Gson().toJson(result));return true;
    }
}
