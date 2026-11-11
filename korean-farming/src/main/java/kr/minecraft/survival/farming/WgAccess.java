package kr.minecraft.survival.farming;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;

/** Loaded only when the real WorldGuard plugin is enabled. */
final class WgAccess {
    static boolean allowed(Player p,Location l){var local=WorldGuardPlugin.inst().wrapPlayer(p);var wg=WorldGuard.getInstance();
        if(wg.getPlatform().getSessionManager().hasBypass(local,BukkitAdapter.adapt(l.getWorld())))return true;
        var query=wg.getPlatform().getRegionContainer().createQuery();var location=BukkitAdapter.adapt(l);
        // Public NPC/use flags must never grant planting rights in a protected region.
        return query.testBuild(location,local)&&query.testBuild(location,local,Flags.BLOCK_PLACE,Flags.BLOCK_BREAK);
    }
}
