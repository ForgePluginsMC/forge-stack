package com.forge.stack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cached, validated view of config.yml. Reloaded via {@link ForgeStack#reload()}.
 */
public final class StackSettings {

    private final double mergeRadius;
    private final int maxStackSize;
    private final Map<EntityType, Integer> perTypeMax;
    private final Set<EntityType> noStackTypes;
    private final Set<CreatureSpawnEvent.SpawnReason> skipSpawnReasons;
    private final boolean spawnerOnlyMode;
    private final String entityNameFormat;
    private final boolean multiplyDrops;
    private final int maxDropMultiplier;
    private final double itemMergeRadius;
    private final int maxItemStack;
    private final Map<Material, Integer> perMaterialMax;
    private final double xpMergeRadius;
    private final int scanIntervalTicks;
    private final double nameVisibleRange;
    private final int nameVisibleIntervalTicks;
    private final int spawnerPerCycleCap;
    private final String spawnerHologramFormat;
    private final boolean spawnerBreakRequiresSilkTouch;
    private final String givespawnerNameFormat;

    public StackSettings(FileConfiguration config, Logger log) {
        this.mergeRadius = Math.max(1.0, config.getDouble("merge-radius", 5.0));
        this.maxStackSize = Math.max(2, config.getInt("max-stack-size", 100));
        this.perTypeMax = readTypeMaxima(config, log);
        this.noStackTypes = readEntityTypes(config, "no-stack-types", log);
        this.skipSpawnReasons = readSpawnReasons(config, log);
        this.spawnerOnlyMode = config.getBoolean("spawner-only-mode", false);
        this.entityNameFormat = config.getString("entity-name-format", "<gray><count>x <white><type>");
        this.multiplyDrops = config.getBoolean("multiply-drops", true);
        this.maxDropMultiplier = Math.max(1, config.getInt("max-drop-multiplier", 64));
        this.itemMergeRadius = Math.max(1.0, config.getDouble("item-merge-radius", 3.0));
        this.maxItemStack = Math.max(1, config.getInt("max-item-stack", 500));
        this.perMaterialMax = readMaterialMaxima(config, log);
        this.xpMergeRadius = Math.max(1.0, config.getDouble("xp-merge-radius", 4.0));
        this.scanIntervalTicks = Math.max(20, config.getInt("scan-interval-ticks", 200));
        this.nameVisibleRange = Math.max(4.0, config.getDouble("name-visible-range", 24.0));
        this.nameVisibleIntervalTicks = Math.max(20, config.getInt("name-visible-interval-ticks", 40));
        this.spawnerPerCycleCap = Math.max(1, config.getInt("spawner.per-cycle-cap", 4));
        this.spawnerHologramFormat = config.getString("spawner.hologram-format", "<gold><count>x <type> spawner");
        this.spawnerBreakRequiresSilkTouch = config.getBoolean("spawner.break-requires-silk-touch", true);
        this.givespawnerNameFormat = config.getString("spawner.item-name-format", "<gold><count>x <type> Spawner");
    }

    private static Map<EntityType, Integer> readTypeMaxima(FileConfiguration config, Logger log) {
        Map<EntityType, Integer> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("per-type-max");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                    int max = Math.max(2, section.getInt(key));
                    map.put(type, max);
                } catch (IllegalArgumentException e) {
                    log.warning("[ForgeStack] Unknown entity type in per-type-max: " + key);
                }
            }
        }
        return Map.copyOf(map);
    }

    private static Set<EntityType> readEntityTypes(FileConfiguration config, String path, Logger log) {
        Set<EntityType> set = new HashSet<>();
        for (String key : config.getStringList(path)) {
            try {
                set.add(EntityType.valueOf(key.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warning("[ForgeStack] Unknown entity type in " + path + ": " + key);
            }
        }
        return Set.copyOf(set);
    }

    private static Set<CreatureSpawnEvent.SpawnReason> readSpawnReasons(FileConfiguration config, Logger log) {
        Set<CreatureSpawnEvent.SpawnReason> set = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String key : config.getStringList("skip-spawn-reasons")) {
            try {
                set.add(CreatureSpawnEvent.SpawnReason.valueOf(key.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warning("[ForgeStack] Unknown spawn reason in skip-spawn-reasons: " + key);
            }
        }
        return Set.copyOf(set);
    }

    private static Map<Material, Integer> readMaterialMaxima(FileConfiguration config, Logger log) {
        Map<Material, Integer> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("per-material-max");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase(Locale.ROOT));
                    int max = Math.max(1, section.getInt(key));
                    map.put(material, max);
                } catch (IllegalArgumentException e) {
                    log.warning("[ForgeStack] Unknown material in per-material-max: " + key);
                }
            }
        }
        return Map.copyOf(map);
    }

    public double mergeRadius() {
        return mergeRadius;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public int maxForType(EntityType type) {
        return perTypeMax.getOrDefault(type, maxStackSize);
    }

    public Set<EntityType> noStackTypes() {
        return noStackTypes;
    }

    public Set<CreatureSpawnEvent.SpawnReason> skipSpawnReasons() {
        return skipSpawnReasons;
    }

    public boolean spawnerOnlyMode() {
        return spawnerOnlyMode;
    }

    public String entityNameFormat() {
        return entityNameFormat;
    }

    public boolean multiplyDrops() {
        return multiplyDrops;
    }

    public int maxDropMultiplier() {
        return maxDropMultiplier;
    }

    public double itemMergeRadius() {
        return itemMergeRadius;
    }

    public int maxItemStack() {
        return maxItemStack;
    }

    public int maxForMaterial(Material material) {
        return perMaterialMax.getOrDefault(material, maxItemStack);
    }

    public double xpMergeRadius() {
        return xpMergeRadius;
    }

    public int scanIntervalTicks() {
        return scanIntervalTicks;
    }

    public double nameVisibleRange() {
        return nameVisibleRange;
    }

    public int nameVisibleIntervalTicks() {
        return nameVisibleIntervalTicks;
    }

    public int spawnerPerCycleCap() {
        return spawnerPerCycleCap;
    }

    public String spawnerHologramFormat() {
        return spawnerHologramFormat;
    }

    public boolean spawnerBreakRequiresSilkTouch() {
        return spawnerBreakRequiresSilkTouch;
    }

    public String givespawnerNameFormat() {
        return givespawnerNameFormat;
    }
}
