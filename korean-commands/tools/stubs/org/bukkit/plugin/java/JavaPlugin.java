package org.bukkit.plugin.java;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
public class JavaPlugin {
    public void onEnable() { }
    public void saveDefaultConfig() { }
    public Server getServer() { return null; }
    public Logger getLogger() { return Logger.getLogger(getClass().getName()); }
    public FileConfiguration getConfig() { return new FileConfiguration(); }
}
