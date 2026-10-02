package com.forge.stack;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * All PersistentDataContainer keys used by ForgeStack.
 */
public final class StackKeys {

    private final NamespacedKey count;
    private final NamespacedKey stacked;
    private final NamespacedKey spawnerBorn;
    private final NamespacedKey spawnerCount;
    private final NamespacedKey spawnerType;

    public StackKeys(Plugin plugin) {
        this.count = new NamespacedKey(plugin, "count");
        this.stacked = new NamespacedKey(plugin, "stacked");
        this.spawnerBorn = new NamespacedKey(plugin, "spawner_born");
        this.spawnerCount = new NamespacedKey(plugin, "spawner_count");
        this.spawnerType = new NamespacedKey(plugin, "spawner_type");
    }

    /** Entity PDC: number of mobs in the stack (INTEGER). Absent = unstacked. */
    public NamespacedKey count() {
        return count;
    }

    /** Entity PDC: marks names set by ForgeStack (BYTE). Distinguishes our stack names from player name tags. */
    public NamespacedKey stacked() {
        return stacked;
    }

    /** Entity PDC: spawned by a spawner (BYTE). Used by spawner-only mode. */
    public NamespacedKey spawnerBorn() {
        return spawnerBorn;
    }

    /** Spawner TileState PDC: stacked spawner count (INTEGER). */
    public NamespacedKey spawnerCount() {
        return spawnerCount;
    }

    /** Spawner item PDC: EntityType name the item places (STRING). */
    public NamespacedKey spawnerType() {
        return spawnerType;
    }
}
