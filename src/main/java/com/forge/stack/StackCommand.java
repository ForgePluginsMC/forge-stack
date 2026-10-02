package com.forge.stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /fstack command: reload, stackall, clearall, givespawner, info.
 */
public final class StackCommand implements CommandExecutor, TabCompleter {

    private final ForgeStack plugin;

    public StackCommand(ForgeStack plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender sender, String miniMessage, TagResolver... resolvers) {
        sender.sendMessage(Text.parse(miniMessage, resolvers));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                send(sender, "<green>ForgeStack configuration reloaded.");
            }
            case "stackall" -> {
                int merges = plugin.stacks().scan();
                send(sender, "<green>Stack scan complete: <white><merges><green> merge(s).",
                        Placeholder.parsed("merges", String.valueOf(merges)));
            }
            case "clearall" -> {
                if (args.length < 2) {
                    send(sender, "<red>Usage: /" + label + " clearall <entities|items>");
                    return true;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "entities" -> {
                        int removed = plugin.stacks().clearStackedEntities();
                        send(sender, "<green>Removed <white><n><green> stacked entities.",
                                Placeholder.parsed("n", String.valueOf(removed)));
                    }
                    case "items" -> {
                        int removed = plugin.stacks().clearItems();
                        send(sender, "<green>Removed <white><n><green> dropped items.",
                                Placeholder.parsed("n", String.valueOf(removed)));
                    }
                    default -> send(sender, "<red>Usage: /" + label + " clearall <entities|items>");
                }
            }
            case "givespawner" -> {
                if (args.length < 3) {
                    send(sender, "<red>Usage: /" + label + " givespawner <type> <count> [player]");
                    return true;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    send(sender, "<red>Unknown entity type: " + args[1]);
                    return true;
                }
                int count;
                try {
                    count = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    send(sender, "<red>Count must be a number.");
                    return true;
                }
                count = Math.max(1, Math.min(1000, count));
                Player target = sender instanceof Player player ? player : null;
                if (args.length >= 4) {
                    target = Bukkit.getPlayerExact(args[3]);
                    if (target == null) {
                        send(sender, "<red>Player not found: " + args[3]);
                        return true;
                    }
                }
                if (target == null) {
                    send(sender, "<red>Only players can receive spawners from the console without a target.");
                    return true;
                }
                ItemStack item = plugin.spawners().createSpawnerItem(type, count);
                target.getInventory().addItem(item);
                send(sender, "<green>Gave <white><count>x <type><green> spawner to <white><player><green>.",
                        Placeholder.parsed("count", String.valueOf(count)),
                        Placeholder.parsed("type", StackManager.prettyName(type)),
                        Placeholder.parsed("player", target.getName()));
            }
            case "info" -> {
                int[] info = plugin.stacks().info();
                send(sender, "<gold>ForgeStack info:");
                send(sender, " <gray>Stacked entities: <white><n>",
                        Placeholder.parsed("n", String.valueOf(info[0])));
                send(sender, " <gray>Total mobs in stacks: <white><n>",
                        Placeholder.parsed("n", String.valueOf(info[1])));
                send(sender, " <gray>Stacked spawners: <white><n>",
                        Placeholder.parsed("n", String.valueOf(info[2])));
            }
            default -> usage(sender, label);
        }
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("ForgeStack commands:"));
        send(sender, " <gray>/" + label + " reload <dark_gray>- reload config");
        send(sender, " <gray>/" + label + " stackall <dark_gray>- force a merge scan now");
        send(sender, " <gray>/" + label + " clearall <entities|items> <dark_gray>- remove stacks/drops");
        send(sender, " <gray>/" + label + " givespawner <type> <count> [player] <dark_gray>- stacked spawner item");
        send(sender, " <gray>/" + label + " info <dark_gray>- stack statistics");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "stackall", "clearall", "givespawner", "info")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("clearall")) {
            for (String s : List.of("entities", "items")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("givespawner")) {
            String prefix = args[1].toUpperCase(Locale.ROOT);
            for (EntityType type : EntityType.values()) {
                if (type.isAlive() && type.name().startsWith(prefix)) {
                    out.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }
}
