package com.forge.stack;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Plain-JVM checks for ForgeStack's server-independent logic.
 * Run: javac with paper-deps on classpath, then java com.forge.stack.StackLogicTest.
 */
public final class StackLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        // prettyName
        check(StackManager.prettyName(EntityType.ZOMBIE).equals("Zombie"), "prettyName ZOMBIE");
        check(StackManager.prettyName(EntityType.CAVE_SPIDER).equals("Cave Spider"), "prettyName CAVE_SPIDER");
        check(StackManager.prettyName(EntityType.ENDER_DRAGON).equals("Ender Dragon"), "prettyName ENDER_DRAGON");

        // stackName renders MiniMessage with placeholders (no server needed)
        Component name = StackManager.stackName("<gray><count>x <white><type>", EntityType.ZOMBIE, 42);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        check(plain.equals("42x Zombie"), "stackName format, got: " + plain);

        // eggToType
        check(SpawnerManager.eggToType(Material.ZOMBIE_SPAWN_EGG) == EntityType.ZOMBIE, "eggToType zombie");
        check(SpawnerManager.eggToType(Material.CREEPER_SPAWN_EGG) == EntityType.CREEPER, "eggToType creeper");
        check(SpawnerManager.eggToType(Material.DIAMOND) == null, "eggToType non-egg null");
        check(SpawnerManager.eggToType(Material.SPAWNER) == null, "eggToType spawner-block null");

        // splitAmounts: pure stack-size math (ItemStack needs a server registry)
        check(StackManager.splitAmounts(3, 64, 5).equals(List.of(15)), "splitAmounts small");
        check(StackManager.splitAmounts(64, 64, 3).equals(List.of(64, 64, 64)), "splitAmounts exact");
        check(StackManager.splitAmounts(60, 64, 3).equals(List.of(64, 64, 52)), "splitAmounts remainder");
        check(StackManager.splitAmounts(7, 16, 10).equals(List.of(16, 16, 16, 16, 6)),
                "splitAmounts small-max");

        // Text fallback on malformed MiniMessage
        Component bad = Text.parse("<unclosed", Placeholder.parsed("x", "y"));
        check(bad != null, "Text.parse malformed does not throw");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
