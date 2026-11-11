package kr.minecraft.survival.economy;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

final class LifeActivity implements Listener {
    private static final Set<Material> ORES = EnumSet.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);
    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    private static final Set<Material> FISH = EnumSet.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
    private final NamespacedKey placedOres;
    private final LifeProgress progress;

    LifeActivity(KoreanEconomyPlugin plugin, LifeProgress progress) {
        placedOres = new NamespacedKey(plugin, "placed_ores");
        this.progress = progress;
    }

    private int coordinate(Block block) {
        return (block.getY() - block.getWorld().getMinHeight()) * 256
                + (block.getZ() & 15) * 16 + (block.getX() & 15);
    }

    private boolean marked(Block block) {
        int[] values = block.getChunk().getPersistentDataContainer().get(placedOres, PersistentDataType.INTEGER_ARRAY);
        return values != null && Arrays.binarySearch(values, coordinate(block)) >= 0;
    }

    private void mark(Block block, boolean add) {
        if (block.getY() < block.getWorld().getMinHeight() || block.getY() >= block.getWorld().getMaxHeight()) return;
        var data = block.getChunk().getPersistentDataContainer();
        int[] previous = data.get(placedOres, PersistentDataType.INTEGER_ARRAY);
        if (previous == null) previous = new int[0];
        int position = coordinate(block);
        int index = Arrays.binarySearch(previous, position);
        if (add == (index >= 0)) return;
        if (add) {
            int[] next = Arrays.copyOf(previous, previous.length + 1);
            next[previous.length] = position;
            Arrays.sort(next);
            data.set(placedOres, PersistentDataType.INTEGER_ARRAY, next);
        } else if (previous.length == 1) {
            data.remove(placedOres);
        } else {
            int[] next = new int[previous.length - 1];
            System.arraycopy(previous, 0, next, 0, index);
            System.arraycopy(previous, index + 1, next, index, previous.length - index - 1);
            data.set(placedOres, PersistentDataType.INTEGER_ARRAY, next);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (ORES.contains(event.getBlockPlaced().getType())) mark(event.getBlockPlaced(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drops(BlockDropItemEvent event) {
        Block block = event.getBlock();
        Material type = event.getBlockState().getType();
        if (ORES.contains(type)) {
            if (marked(block)) mark(block, false);
            else if (!event.getItems().isEmpty()) progress.activity(event.getPlayer(), LifeProgress.Job.MINER);
        } else if (CROPS.contains(type) && event.getBlockState().getBlockData() instanceof Ageable crop
                && crop.getAge() == crop.getMaximumAge() && !event.getItems().isEmpty()) {
            progress.activity(event.getPlayer(), LifeProgress.Job.FARMER);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent event) { moved(event.getBlocks(), event.getDirection()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent event) { moved(event.getBlocks(), event.getDirection()); }

    private void moved(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (!ORES.contains(block.getType())) continue;
            // Conservatively exclude piston-moved ores, including cross-chunk movement.
            mark(block, true);
            mark(block.getRelative(direction), true);
            mark(block.getRelative(direction.getOppositeFace()), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught
                && FISH.contains(caught.getItemStack().getType())) {
            progress.activity(event.getPlayer(), LifeProgress.Job.FISHER);
        }
    }
}
