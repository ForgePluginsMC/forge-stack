package com.forge.stack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Spawner stacking: counts stored on the spawner TileState PDC, per-cycle
 * spawn multiplication, silk-touch-aware breaking, spawn-egg conversion,
 * and TextDisplay holograms for stacked spawners.
 */
public final class SpawnerManager {

    private final ForgeStack plugin;
    private final StackKeys keys;
    private final Map<String, TextDisplay> holograms = new HashMap<>();

    public SpawnerManager(ForgeStack plugin) {
        this.plugin = plugin;
        this.keys = plugin.keys();
    }

    // ------------------------------------------------------------------
    // Spawner PDC
    // ------------------------------------------------------------------

    /** Stacked spawner count; 1 when absent (vanilla spawner). */
    public int getCount(Block block) {
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            Integer count = spawner.getPersistentDataContainer().get(keys.spawnerCount(), PersistentDataType.INTEGER);
            return count == null ? 1 : Math.max(1, count);
        }
        return 1;
    }

    /** Writes the count (and optionally type) to the spawner. update() persists it. */
    public void setCount(Block block, int count, @Nullable EntityType type) {
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            spawner.getPersistentDataContainer().set(keys.spawnerCount(), PersistentDataType.INTEGER, Math.max(1, count));
            if (type != null) {
                spawner.setSpawnedType(type);
            }
            spawner.update();
        }
    }

    private @Nullable EntityType getSpawnerType(Block block) {
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            return spawner.getSpawnedType();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Spawn multiplication
    // ------------------------------------------------------------------

    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        CreatureSpawner spawner = event.getSpawner();
        Block block = spawner.getBlock();
        int count = getCount(block);
        if (count < 2) {
            return;
        }
        refreshHologram(block);
        EntityType type = spawner.getSpawnedType();
        if (type == null) {
            return;
        }
        int extra = Math.min(count - 1, plugin.settings().spawnerPerCycleCap());
        World world = block.getWorld();
        Location base = block.getLocation().add(0.5, 0.3, 0.5);
        for (int i = 0; i < extra; i++) {
            Location loc = base.clone().add((Math.random() - 0.5) * 3.0, Math.random() * 1.5, (Math.random() - 0.5) * 3.0);
            try {
                // Fires CreatureSpawnEvent with reason CUSTOM, which stacking skips;
                // the periodic scan folds these into stacks.
                world.spawnEntity(loc, type);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[ForgeStack] Cannot spawn " + type + " for stacked spawner: " + e.getMessage());
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Stacked spawner items: give / place / break
    // ------------------------------------------------------------------

    public ItemStack createSpawnerItem(EntityType type, int count) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keys.spawnerCount(), PersistentDataType.INTEGER, Math.max(1, count));
            pdc.set(keys.spawnerType(), PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        item.setData(DataComponentTypes.CUSTOM_NAME, spawnerItemName(type, count));
        return item;
    }

    private Component spawnerItemName(EntityType type, int count) {
        return Text.parse(plugin.settings().givespawnerNameFormat(),
                Placeholder.parsed("count", String.valueOf(count)),
                Placeholder.parsed("type", StackManager.prettyName(type)));
    }

    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        ItemStack hand = event.getItemInHand();
        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            return;
        }
        Integer count = meta.getPersistentDataContainer().get(keys.spawnerCount(), PersistentDataType.INTEGER);
        if (count == null || count < 1) {
            return;
        }
        EntityType type = null;
        String typeName = meta.getPersistentDataContainer().get(keys.spawnerType(), PersistentDataType.STRING);
        if (typeName != null) {
            try {
                type = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[ForgeStack] Stacked spawner item has unknown type: " + typeName);
            }
        }
        setCount(block, count, type);
        refreshHologram(block);
    }

    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        int count = getCount(block);
        EntityType type = getSpawnerType(block);
        removeHologram(block);
        if (count < 2 || type == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("forgestack.spawner.silktouch")) {
            return;
        }
        if (plugin.settings().spawnerBreakRequiresSilkTouch()) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            ItemMeta meta = tool.getItemMeta();
            if (meta == null || !meta.hasEnchant(Enchantment.SILK_TOUCH)) {
                return;
            }
        }
        // Vanilla spawners drop nothing; a stacked one drops its stacked item.
        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5), createSpawnerItem(type, count));
    }

    // ------------------------------------------------------------------
    // Spawn-egg conversion
    // ------------------------------------------------------------------

    /** Parses ZOMBIE_SPAWN_EGG -> EntityType.ZOMBIE. Pure logic, unit-tested. */
    static @Nullable EntityType eggToType(Material material) {
        String name = material.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void onEggConvert(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || hand.isEmpty()) {
            return;
        }
        EntityType type = eggToType(hand.getType());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("forgestack.egg")) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            spawner.update();
            refreshHologram(block);
            player.sendMessage(Text.parse("<green>Spawner now spawns <type>.",
                    Placeholder.parsed("type", StackManager.prettyName(type))));
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Holograms (TextDisplay, no dependencies)
    // ------------------------------------------------------------------

    private String holoKey(Block block) {
        Location loc = block.getLocation();
        return block.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void ensureHologram(Block block) {
        String key = holoKey(block);
        TextDisplay existing = holograms.get(key);
        if (existing != null) {
            if (existing.isValid()) {
                return;
            }
            holograms.remove(key);
        }
        int count = getCount(block);
        if (count < 2) {
            return;
        }
        EntityType type = getSpawnerType(block);
        Component text = Text.parse(plugin.settings().spawnerHologramFormat(),
                Placeholder.parsed("count", String.valueOf(count)),
                Placeholder.parsed("type", type == null ? "Spawner" : StackManager.prettyName(type)));
        Location loc = block.getLocation().add(0.5, 1.4, 0.5);
        Entity spawned = block.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        if (!(spawned instanceof TextDisplay display)) {
            return;
        }
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        holograms.put(key, display);
    }

    public void refreshHologram(Block block) {
        removeHologram(block);
        ensureHologram(block);
    }

    public void removeHologram(Block block) {
        TextDisplay display = holograms.remove(holoKey(block));
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    /** One-time pass over loaded chunks so restarts re-hologram stacked spawners. */
    public void restoreHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof CreatureSpawner) {
                        ensureHologram(state.getBlock());
                    }
                }
            }
        }
    }

    public void removeAllHolograms() {
        for (TextDisplay display : holograms.values()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        holograms.clear();
    }

    /** Counts stacked spawners across loaded chunks (for /fstack info). */
    public int stackedSpawnerCount() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof CreatureSpawner && getCount(state.getBlock()) > 1) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
