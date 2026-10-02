package com.forge.stack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;

/**
 * MiniMessage helpers with a safe fallback: a malformed admin template
 * degrades to plain text instead of breaking chat or throwing.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(@Nullable String input, TagResolver... resolvers) {
        if (input == null) {
            return Component.empty();
        }
        try {
            return MM.deserialize(input, resolvers);
        } catch (RuntimeException e) {
            return Component.text(stripTags(input));
        }
    }

    public static String stripTags(@Nullable String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("<[^>]*>", "");
    }
}
