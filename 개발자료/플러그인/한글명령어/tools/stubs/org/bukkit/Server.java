package org.bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.CommandSender;
public interface Server {
    PluginManager getPluginManager();
    boolean dispatchCommand(CommandSender sender, String commandLine);
}
