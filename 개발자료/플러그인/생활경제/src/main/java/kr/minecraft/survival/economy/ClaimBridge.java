package kr.minecraft.survival.economy;

import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import kr.minecraft.survival.economy.domain.StateStore;

/** Optional GP 16.18.8 bridge. Never changes the existing claim store or trust lists. */
final class ClaimBridge {
    final LandProtection protection;final Plugin gp;boolean ready;Material tool=Material.GOLDEN_SHOVEL;
    ClaimBridge(LandProtection protection){this.protection=protection;gp=protection.t.plugin.getServer().getPluginManager().getPlugin("GriefPrevention");if(gp==null||!gp.isEnabled()){ready=true;return;}
        try{tool=(Material)gp.getClass().getField("config_claims_modificationTool").get(gp);for(String type:List.of("ClaimCreatedEvent","ClaimResizeEvent")){
            Class<? extends Event> event=Class.forName("me.ryanhamshire.GriefPrevention.events."+type,true,gp.getClass().getClassLoader()).asSubclass(Event.class);
            if(!Cancellable.class.isAssignableFrom(event))throw new IllegalStateException("취소할 수 없는 보호 이벤트");
            protection.t.plugin.getServer().getPluginManager().registerEvent(event,protection,EventPriority.HIGHEST,(listener,e)->{
                try{Object claim=e.getClass().getMethod(type.equals("ClaimCreatedEvent")?"getClaim":"getTo").invoke(e);Location low=(Location)claim.getClass().getMethod("getLesserBoundaryCorner").invoke(claim),high=(Location)claim.getClass().getMethod("getGreaterBoundaryCorner").invoke(claim);if(overlaps(low,high))((Cancellable)e).setCancelled(true);}
                catch(ReflectiveOperationException|RuntimeException failure){((Cancellable)e).setCancelled(true);ready=false;protection.t.plugin.getLogger().warning("개인 보호 연동 확인 필요: "+failure);}
            },protection.t.plugin,true);
        }ready=true;}catch(ReflectiveOperationException|RuntimeException failure){ready=false;protection.t.plugin.getLogger().warning("마을-개인 보호 연동 확인 필요: "+failure);}
    }
    boolean overlaps(Location low,Location high){if(low.getWorld()==null||high.getWorld()!=low.getWorld())throw new IllegalArgumentException("보호 월드 오류");String world=low.getWorld().getUID().toString();
        for(var land:protection.t.store.lands())if(land.world().equals(world)&&intersects(low.getBlockX(),low.getBlockZ(),high.getBlockX(),high.getBlockZ(),land.x(),land.z()))return true;
        for(String key:protection.leases.values()){String[] c=key.split(":");if(c[0].equals(world)&&intersects(low.getBlockX(),low.getBlockZ(),high.getBlockX(),high.getBlockZ(),Integer.parseInt(c[1]),Integer.parseInt(c[2])))return true;}return false;
    }
    static boolean intersects(int minX,int minZ,int maxX,int maxZ,int chunkX,int chunkZ){return (long)maxX>=(long)chunkX*16&&minX<=(long)chunkX*16+15&&(long)maxZ>=(long)chunkZ*16&&minZ<=(long)chunkZ*16+15;}
    boolean collides(World world,int x,int z){if(gp==null||!gp.isEnabled())return false;if(!ready)throw new StateStore.Denied("개인 땅 보호 연동을 확인할 때까지 신규 필지 거래를 중지합니다.");
        try{Object data=gp.getClass().getField("dataStore").get(gp);Object result=data.getClass().getMethod("getClaims").invoke(data);if(!(result instanceof Collection<?> claims))throw new IllegalStateException("보호 목록 형식 오류");for(Object claim:claims){Location low=(Location)claim.getClass().getMethod("getLesserBoundaryCorner").invoke(claim),high=(Location)claim.getClass().getMethod("getGreaterBoundaryCorner").invoke(claim);if(world.equals(low.getWorld())&&intersects(low.getBlockX(),low.getBlockZ(),high.getBlockX(),high.getBlockZ(),x,z))return true;}return false;}
        catch(ReflectiveOperationException|RuntimeException failure){ready=false;throw new StateStore.Denied("기존 개인 땅을 확인하지 못해 신규 필지 거래를 중지했습니다.");}
    }
    boolean blockTool(PlayerInteractEvent e){if(ready||gp==null||!gp.isEnabled()||e.getItem()==null||e.getItem().getType()!=tool)return false;String world=e.getPlayer().getWorld().getUID().toString();return protection.t.store.lands().stream().anyMatch(l->l.world().equals(world))||protection.leases.values().stream().anyMatch(k->k.startsWith(world+":"));}
}
