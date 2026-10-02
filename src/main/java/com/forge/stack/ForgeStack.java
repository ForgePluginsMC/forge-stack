package com.forge.stack;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * ForgeStack — entity, item and spawner stacking with hologram counts.
 * Dependency-free, Paper 26.3 native, zero deprecated API usage.
 */
public final class ForgeStack extends JavaPlugin {

    private StackKeys keys;
    private StackSettings settings;
    private StackManager stacks;
    private SpawnerManager spawners;
    private BukkitTask scanTask;
    private BukkitTask nameTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.keys = new StackKeys(this);
        this.settings = new StackSettings(getConfig(), getLogger());
        this.stacks = new StackManager(this);
        this.spawners = new SpawnerManager(this);

        getServer().getPluginManager().registerEvents(new StackListener(this), this);

        StackCommand command = new StackCommand(this);
        if (getCommand("fstack") != null) {
            getCommand("fstack").setExecutor(command);
            getCommand("fstack").setTabCompleter(command);
        }

        long interval = settings.scanIntervalTicks();
        this.scanTask = getServer().getScheduler().runTaskTimer(this, () -> stacks.scan(), interval, interval);
        long nameInterval = settings.nameVisibleIntervalTicks();
        this.nameTask = getServer().getScheduler().runTaskTimer(this, () -> stacks.updateNameVisibility(),
                nameInterval, nameInterval);

        // Re-hologram stacked spawners across already-loaded chunks (restarts).
        getServer().getScheduler().runTask(this, () -> spawners.restoreHolograms());

        getLogger().info("ForgeStack 1.0.0 enabled.");
    }

    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (nameTask != null) {
            nameTask.cancel();
            nameTask = null;
        }
        if (spawners != null) {
            spawners.removeAllHolograms();
        }
    }

    /** Reloads config and re-caches all settings. */
    public void reload() {
        reloadConfig();
        this.settings = new StackSettings(getConfig(), getLogger());
        if (scanTask != null) {
            scanTask.cancel();
        }
        if (nameTask != null) {
            nameTask.cancel();
        }
        long interval = settings.scanIntervalTicks();
        this.scanTask = getServer().getScheduler().runTaskTimer(this, () -> stacks.scan(), interval, interval);
        long nameInterval = settings.nameVisibleIntervalTicks();
        this.nameTask = getServer().getScheduler().runTaskTimer(this, () -> stacks.updateNameVisibility(),
                nameInterval, nameInterval);
    }

    public StackKeys keys() {
        return keys;
    }

    public StackSettings settings() {
        return settings;
    }

    public StackManager stacks() {
        return stacks;
    }

    public SpawnerManager spawners() {
        return spawners;
    }
}
