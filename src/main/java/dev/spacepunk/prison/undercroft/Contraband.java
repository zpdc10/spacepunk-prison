package dev.spacepunk.prison.undercroft;

import dev.spacepunk.prison.undercroft.UndercroftConfig.HotOre;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Ingredient;
import dev.spacepunk.prison.undercroft.UndercroftConfig.Stim;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and reads Undercroft items. Every item carries "kind:id" under spacepunk:contraband, for example
 * "stim:overdrive", "ingredient:sludge", "hot_ore:hot_scrap", "tag:bounty", "tool:lining", "crate:tech".
 */
public final class Contraband {
    static final String NS = "spacepunk";
    static final NamespacedKey KEY = new NamespacedKey(NS, "contraband");
    static final NamespacedKey TAG_VALUE = new NamespacedKey(NS, "bounty_value");
    static final NamespacedKey TAG_VICTIM = new NamespacedKey(NS, "bounty_victim");

    public enum Kind { STIM, INGREDIENT, HOT_ORE, TAG, TOOL, CRATE }

    public record Id(Kind kind, String id) {
        String encode() { return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id; }
    }

    private UndercroftConfig cfg;

    Contraband(UndercroftConfig cfg) { this.cfg = cfg; }

    void config(UndercroftConfig cfg) { this.cfg = cfg; }

    // ---- reading ----

    public Id read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (s == null) return null;
        int i = s.indexOf(':');
        if (i < 0) return null;
        try {
            return new Id(Kind.valueOf(s.substring(0, i).toUpperCase(java.util.Locale.ROOT)), s.substring(i + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Contraband that the checkpoint scanner looks for (everything except the Smuggler's Lining). */
    public boolean isContraband(ItemStack item) {
        Id id = read(item);
        return id != null && id.kind() != Kind.TOOL;
    }

    public boolean is(ItemStack item, Kind kind, String id) {
        Id r = read(item);
        return r != null && r.kind() == kind && (id == null || r.id().equals(id));
    }

    public long tagValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Long v = item.getItemMeta().getPersistentDataContainer().get(TAG_VALUE, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    /** Fence value of one unit at grade 1 (bounty tags carry their own, already-scaled value). */
    public long baseValue(Id id) {
        if (id == null) return 0;
        return switch (id.kind()) {
            case HOT_ORE -> { HotOre h = cfg.hotOres.get(id.id()); yield h == null ? 0 : h.value(); }
            case STIM -> { Stim s = cfg.stims.get(id.id()); yield s == null ? 0 : s.fenceValue(); }
            case INGREDIENT -> 10;
            case CRATE -> 500;
            default -> 0;
        };
    }

    // ---- building ----

    private ItemStack base(Id id, String name, String model, List<String> lore, int amount, boolean glint) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, Math.min(99, amount)));
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY, PersistentDataType.STRING, id.encode());
        meta.setMaxStackSize(99);
        meta.displayName(Text.item(name));
        meta.setItemModel(new NamespacedKey(NS, model));
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final String CONTRABAND_LINE = "<#ff5040>⚠ CONTRABAND</#ff5040> <dark_gray>· drops on death · scanned at the checkpoint";

    public ItemStack stim(String stimId, int amount) {
        Stim s = cfg.stims.get(stimId);
        if (s == null) return null;
        List<String> lore = new ArrayList<>();
        lore.add("<#8a8070><i>" + s.lore());
        lore.add("");
        lore.add("<gray>High <white>" + mmss(s.duration()) + "</white>: " + describe(s.high()));
        lore.add("<gray>Comedown <white>" + mmss(s.comedownDuration()) + "</white>: " + describe(s.comedown()));
        lore.add("");
        lore.add("<#c8743a>Right-click</#c8743a> <gray>to take. One stim at a time.");
        lore.add(CONTRABAND_LINE);
        return base(new Id(Kind.STIM, stimId), s.name(), "stim/" + stimId, lore, amount, true);
    }

    public ItemStack ingredient(String ingId, int amount) {
        Ingredient i = cfg.ingredients.get(ingId);
        if (i == null) return null;
        return base(new Id(Kind.INGREDIENT, ingId), i.name(), "contraband/" + ingId, List.of(
                "<#8a8070><i>" + i.lore(), "", "<gray>Stim ingredient. Cook it at an Undercroft lab.", CONTRABAND_LINE), amount, false);
    }

    public ItemStack hotOre(String oreId, int amount) {
        HotOre h = cfg.hotOres.get(oreId);
        if (h == null) return null;
        return base(new Id(Kind.HOT_ORE, oreId), h.name(), "contraband/" + oreId, List.of(
                "<#8a8070><i>" + h.lore(), "", "<gray>Worth far more than normal ore, but only", "<gray>the <white>/fence</white> will touch it.",
                CONTRABAND_LINE), amount, false);
    }

    public ItemStack bountyTag(String victim, long value) {
        ItemStack item = base(new Id(Kind.TAG, "bounty"), "<#d6a640>Bounty Tag <dark_gray>(" + victim + ")",
                "contraband/bounty_tag", List.of("<#8a8070><i>Somebody's dog tags. Still warm.", "",
                        "<gray>Turn in at the <white>/fence</white> for <gold>" + Numbers.shortFormat(value) + " Credits",
                        "<gray>and gang leaderboard points.", CONTRABAND_LINE), 1, false);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TAG_VALUE, PersistentDataType.LONG, value);
        meta.getPersistentDataContainer().set(TAG_VICTIM, PersistentDataType.STRING, victim);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack lining(int amount) {
        return base(new Id(Kind.TOOL, "lining"), "<#9aa0a6>Smuggler's Lining", "contraband/lining", List.of(
                "<#8a8070><i>Lead foil sewn into a coat. Scanners hate it.", "",
                "<gray>While carried, hides <white>" + cfg.liningHides + "</white> contraband stacks", "<gray>from the checkpoint scanner.",
                "<dark_gray>Not contraband itself. Drops on death."), amount, false);
    }

    public ItemStack techCrate(int amount) {
        return base(new Id(Kind.CRATE, "tech"), "<#6fe3ff>Smuggled Tech Crate", "contraband/tech_crate", List.of(
                "<#8a8070><i>Crate stencilled PROPERTY OF THE WARDEN.", "",
                "<gray>Gun parts, rare guns, Tokens, drill modules.", "<#c8743a>Right-click</#c8743a> <gray>outside the Undercroft to crack it open.",
                CONTRABAND_LINE), amount, true);
    }

    /** Builds any item by kind + id (admin give, loot tables). */
    public ItemStack create(String id, int amount) {
        if (cfg.stims.containsKey(id)) return stim(id, amount);
        if (cfg.ingredients.containsKey(id)) return ingredient(id, amount);
        if (cfg.hotOres.containsKey(id)) return hotOre(id, amount);
        if (id.equals("lining")) return lining(amount);
        if (id.equals("tech_crate")) return techCrate(amount);
        return null;
    }

    public List<String> allIds() {
        List<String> ids = new ArrayList<>();
        ids.addAll(cfg.stims.keySet());
        ids.addAll(cfg.ingredients.keySet());
        ids.addAll(cfg.hotOres.keySet());
        ids.add("lining");
        ids.add("tech_crate");
        return ids;
    }

    static String describe(UndercroftConfig.Phase p) {
        List<String> parts = new ArrayList<>();
        if (p.sell() != 1.0) parts.add((p.sell() > 1 ? "<green>" : "<red>") + "sell x" + fmt(p.sell()));
        if (p.mining() != 0) parts.add((p.mining() > 0 ? "<green>+" : "<red>") + Math.round(p.mining() * 100) + "% mining");
        if (p.gunDamage() != 1.0) parts.add((p.gunDamage() > 1 ? "<green>" : "<red>") + "gun damage x" + fmt(p.gunDamage()));
        p.effects().forEach((t, lvl) -> parts.add("<white>" + pretty(t.getKey().getKey()) + (lvl > 1 ? " " + roman(lvl) : "")));
        return parts.isEmpty() ? "<gray>nothing" : String.join("<gray>, ", parts);
    }

    static String pretty(String key) {
        String[] w = key.split("_");
        StringBuilder b = new StringBuilder();
        for (String s : w) {
            if (s.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return b.toString();
    }

    static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v); }

    static String mmss(long seconds) { return (seconds / 60) + ":" + String.format("%02d", seconds % 60); }
}
