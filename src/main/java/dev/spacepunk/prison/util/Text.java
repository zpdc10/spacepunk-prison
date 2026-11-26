package dev.spacepunk.prison.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.List;

/** MiniMessage helpers. All player-facing text uses MiniMessage strings. */
public final class Text {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    /** Prefix used on chat messages. Grungy terminal look. */
    public static final String PREFIX = "<dark_gray>[<#c8743a>STATION</#c8743a>]</dark_gray> ";

    private Text() {}

    public static Component mm(String miniMessage) { return MM.deserialize(miniMessage); }

    /** For item names/lore: removes the default italic. */
    public static Component item(String miniMessage) {
        return MM.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(List<String> lines) { return lines.stream().map(Text::item).toList(); }

    public static void send(CommandSender to, String miniMessage) { to.sendMessage(mm(PREFIX + miniMessage)); }
}
