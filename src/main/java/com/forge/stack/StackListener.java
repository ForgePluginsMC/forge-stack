package com.forge.stack;

import java.util.Set;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Wires Bukkit events into the stack managers.
 */
public final class StackListener implements Listener {

    private final ForgeStack plugin;

    public StackListener(ForgeStack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Set<CreatureSpawnEvent.SpawnReason> skipped = plugin.settings().skipSpawnReasons();
        if (skipped.contains(event.getSpawnReason())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (plugin.stacks().tryMergeSpawn(living, event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            plugin.stacks().onDeath(event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        plugin.spawners().onSpawnerSpawn(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        plugin.spawners().onPlace(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.spawners().onBreak(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.spawners().onEggConvert(event);
        }
    }
}
