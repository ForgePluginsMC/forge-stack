package com.forge.stack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Entity, item and XP-orb stacking logic. All methods must run on the main thread.
 */
public final class StackManager {

    private final ForgeStack plugin;
    private final StackKeys keys;

    public StackManager(ForgeStack plugin) {
        this.plugin = plugin;
        this.keys = plugin.keys();
    }

    // ------------------------------------------------------------------
    // Stack state
    // ------------------------------------------------------------------

    /** Number of mobs in this stack; 0 when the entity is not stacked. */
    public int getCount(LivingEntity entity) {
        Integer count = entity.getPersistentDataContainer().get(keys.count(), PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    /** True when this entity carries a ForgeStack stack name (vs a player name tag). */
    public boolean isStacked(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(keys.stacked(), PersistentDataType.BYTE);
    }

    public boolean isExcluded(LivingEntity entity) {
        if (entity instanceof Player) {
            return true;
        }
        StackSettings settings = plugin.settings();
        if (settings.noStackTypes().contains(entity.getType())) {
            return true;
        }
        if (entity instanceof Tameable tameable) {
            if (tameable.isTamed()) {
                return true;
            }
            if (tameable.getOwner() instanceof Player owner && owner.hasPermission("forgestack.bypass")) {
                return true;
            }
        }
        if (entity.isLeashed()) {
            return true;
        }
        if (entity instanceof Ageable ageable && !ageable.isAdult()) {
            return true;
        }
        // Player name tags are respected: only our own stack names may be overwritten.
        if (entity.customName() != null && !isStacked(entity)) {
            return true;
        }
        return false;
    }

    /** Writes stack state: PDC count, display name, AI off (the TPS win), no far despawn. */
    public void setStack(LivingEntity entity, int count) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keys.count(), PersistentDataType.INTEGER, count);
        pdc.set(keys.stacked(), PersistentDataType.BYTE, (byte) 1);
        entity.customName(stackName(entity.getType(), count));
        entity.setCustomNameVisible(isPlayerNear(entity));
        entity.setAI(false);
        entity.setRemoveWhenFarAway(false);
    }

    public Component stackName(EntityType type, int count) {
        return stackName(plugin.settings().entityNameFormat(), type, count);
    }

    static Component stackName(String format, EntityType type, int count) {
        return Text.parse(format,
                Placeholder.parsed("count", String.valueOf(count)),
                Placeholder.parsed("type", prettyName(type)));
    }

    /** ZOMBIE -> "Zombie", CAVE_SPIDER -> "Cave Spider". Pure logic, unit-tested. */
    public static String prettyName(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Merging
    // ------------------------------------------------------------------

    /**
     * Absorbs {@code from} into {@code into}. Returns the new count, or -1 when
     * the merge is not allowed (cap reached).
     */
    private int absorb(LivingEntity into, LivingEntity from) {
        StackSettings settings = plugin.settings();
        int total = Math.max(1, getCount(into)) + Math.max(1, getCount(from));
        if (total > settings.maxForType(into.getType())) {
            return -1;
        }
        from.remove();
        setStack(into, total);
        return total;
    }

    private boolean mergeable(LivingEntity entity, Set<LivingEntity> consumed) {
        return !consumed.contains(entity) && entity.isValid() && !entity.isDead() && !isExcluded(entity);
    }

    /**
     * Merge-on-spawn: folds a freshly spawned mob into a nearby stack.
     * Returns true when the spawn was consumed (caller should cancel the event).
     */
    public boolean tryMergeSpawn(LivingEntity spawned, CreatureSpawnEvent.SpawnReason reason) {
        StackSettings settings = plugin.settings();
        if (settings.spawnerOnlyMode()) {
            if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER) {
                spawned.getPersistentDataContainer().set(keys.spawnerBorn(), PersistentDataType.BYTE, (byte) 1);
            } else {
                return false;
            }
        }
        if (isExcluded(spawned)) {
            return false;
        }
        double radius = settings.mergeRadius();
        for (Entity nearby : spawned.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(spawned)) {
                continue;
            }
            if (target.getType() != spawned.getType() || !target.isValid() || target.isDead()) {
                continue;
            }
            if (isExcluded(target)) {
                continue;
            }
            if (settings.spawnerOnlyMode() && !isSpawnerBorn(target)) {
                continue;
            }
            if (absorb(target, spawned) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isSpawnerBorn(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(keys.spawnerBorn(), PersistentDataType.BYTE);
    }

    /** Periodic scan over every world. Returns the number of merges performed. */
    public int scan() {
        int merges = 0;
        for (World world : Bukkit.getWorlds()) {
            merges += scanEntities(world);
            mergeItems(world);
            mergeOrbs(world);
        }
        return merges;
    }

    /**
     * Toggles stacked-entity nameplates by player proximity, so "10x Salmon"
     * only renders when someone is close enough to read it. Runs on the main
     * thread; cheap enough to run every couple of seconds.
     */
    public void updateNameVisibility() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) {
                continue;
            }
            for (LivingEntity entity : world.getLivingEntities()) {
                if (getCount(entity) < 2 || !entity.isValid() || entity.isDead()) {
                    continue;
                }
                entity.setCustomNameVisible(isPlayerNear(entity));
            }
        }
    }

    /** True when any player in the entity's world is within the name-visible range. */
    public boolean isPlayerNear(LivingEntity entity) {
        double rangeSq = plugin.settings().nameVisibleRange();
        rangeSq *= rangeSq;
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private int scanEntities(World world) {
        StackSettings settings = plugin.settings();
        double radius = settings.mergeRadius();
        Set<LivingEntity> consumed = new HashSet<>();
        int merges = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!mergeable(entity, consumed)) {
                continue;
            }
            if (settings.spawnerOnlyMode() && !isSpawnerBorn(entity)) {
                continue;
            }
            int cap = settings.maxForType(entity.getType());
            int count = Math.max(1, getCount(entity));
            if (count >= cap) {
                continue;
            }
            for (Entity nearby : entity.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity other) || other.equals(entity)) {
                    continue;
                }
                if (!mergeable(other, consumed) || other.getType() != entity.getType()) {
                    continue;
                }
                if (settings.spawnerOnlyMode() && !isSpawnerBorn(other)) {
                    continue;
                }
                int total = count + Math.max(1, getCount(other));
                if (total > cap) {
                    continue;
                }
                other.remove();
                consumed.add(other);
                count = total;
                merges++;
            }
            if (count > Math.max(1, getCount(entity))) {
                setStack(entity, count);
            }
        }
        return merges;
    }

    // ------------------------------------------------------------------
    // Drops & EXP
    // ------------------------------------------------------------------

    /**
     * Multiplies a killed stack's drops and EXP. Only stackable materials are
     * multiplied (anti-dupe: a zombie's armor must never duplicate).
     */
    public void onDeath(EntityDeathEvent event) {
        StackSettings settings = plugin.settings();
        if (!settings.multiplyDrops()) {
            return;
        }
        int count = getCount(event.getEntity());
        if (count < 2) {
            return;
        }
        int multiplier = Math.min(count, settings.maxDropMultiplier());
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> multiplied = new ArrayList<>(drops.size() * 2);
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            if (drop.getType().getMaxStackSize() > 1) {
                multiplied.addAll(splitMultiplied(drop, multiplier));
            } else {
                multiplied.add(drop);
            }
        }
        drops.clear();
        drops.addAll(multiplied);
        event.setDroppedExp(event.getDroppedExp() * multiplier);
    }

    /** Splits amount*multiplier into valid max-size stack sizes. Pure logic, unit-tested. */
    static List<Integer> splitAmounts(int amount, int maxStackSize, int multiplier) {
        List<Integer> out = new ArrayList<>();
        int total = amount * multiplier;
        while (total > 0) {
            int size = Math.min(total, maxStackSize);
            out.add(size);
            total -= size;
        }
        return out;
    }

    static List<ItemStack> splitMultiplied(ItemStack drop, int multiplier) {
        List<ItemStack> out = new ArrayList<>();
        int max = drop.getType().getMaxStackSize();
        for (int size : splitAmounts(drop.getAmount(), max, multiplier)) {
            ItemStack copy = drop.clone();
            copy.setAmount(size);
            out.add(copy);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Item & XP-orb merging
    // ------------------------------------------------------------------

    private void mergeItems(World world) {
        StackSettings settings = plugin.settings();
        double radius = settings.itemMergeRadius();
        Set<Item> consumed = new HashSet<>();
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (consumed.contains(item) || !item.isValid() || item.isDead()) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            int cap = settings.maxForMaterial(stack.getType());
            int total = stack.getAmount();
            if (total >= cap) {
                continue;
            }
            for (Entity nearby : item.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof Item other) || other.equals(item)) {
                    continue;
                }
                if (consumed.contains(other) || !other.isValid() || other.isDead()) {
                    continue;
                }
                ItemStack otherStack = other.getItemStack();
                // isSimilar is PDC-aware: custom tagged items never merge into vanilla ones.
                if (!stack.isSimilar(otherStack)) {
                    continue;
                }
                int room = cap - total;
                if (room <= 0) {
                    break;
                }
                int move = Math.min(room, otherStack.getAmount());
                total += move;
                int left = otherStack.getAmount() - move;
                if (left <= 0) {
                    other.remove();
                    consumed.add(other);
                } else {
                    otherStack.setAmount(left);
                    other.setItemStack(otherStack);
                }
            }
            if (total != stack.getAmount()) {
                stack.setAmount(total);
                item.setItemStack(stack);
            }
        }
    }

    private void mergeOrbs(World world) {
        double radius = plugin.settings().xpMergeRadius();
        Set<ExperienceOrb> consumed = new HashSet<>();
        for (ExperienceOrb orb : world.getEntitiesByClass(ExperienceOrb.class)) {
            if (consumed.contains(orb) || !orb.isValid() || orb.isDead()) {
                continue;
            }
            int total = orb.getExperience();
            for (Entity nearby : orb.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof ExperienceOrb other) || other.equals(orb)) {
                    continue;
                }
                if (consumed.contains(other) || !other.isValid() || other.isDead()) {
                    continue;
                }
                total += other.getExperience();
                other.remove();
                consumed.add(other);
            }
            orb.setExperience(total);
        }
    }

    // ------------------------------------------------------------------
    // Admin helpers
    // ------------------------------------------------------------------

    public int clearStackedEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (getCount(entity) > 1) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int clearItems() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Returns {stackedEntities, totalStackedMobs, spawnerStacks}. */
    public int[] info() {
        int stacks = 0;
        int mobs = 0;
        int spawnerStacks = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                int count = getCount(entity);
                if (count > 1) {
                    stacks++;
                    mobs += count;
                }
            }
        }
        spawnerStacks = plugin.spawners().stackedSpawnerCount();
        return new int[]{stacks, mobs, spawnerStacks};
    }
}
