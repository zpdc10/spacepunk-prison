package dev.spacepunk.prison.fishing;

import dev.spacepunk.prison.fishing.FishingConfig.BaitDef;
import dev.spacepunk.prison.fishing.FishingConfig.EnchantDef;
import dev.spacepunk.prison.fishing.FishingConfig.FishDef;
import dev.spacepunk.prison.fishing.FishingConfig.JunkDef;
import dev.spacepunk.prison.fishing.FishingConfig.LureDef;
import dev.spacepunk.prison.fishing.FishingConfig.RodTier;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds and recognises fishing items: the rod (a view of {@link FishingData}), fish, trophies, junk and lures.
 * Every item carries a PDC tag "kind:id" so it survives renames and pack changes.
 */
final class FishingItems {
    static final String NS = "spacepunk";
    private final NamespacedKey rodOwnerKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey serialKey;
    private FishingConfig cfg;

    FishingItems(Plugin plugin, FishingConfig cfg) {
        rodOwnerKey = new NamespacedKey(plugin, "rod_owner");
        itemKey = new NamespacedKey(plugin, "fishing_item");
        weightKey = new NamespacedKey(plugin, "fishing_weight");
        serialKey = new NamespacedKey(plugin, "fishing_serial");
        this.cfg = cfg;
    }

    void config(FishingConfig cfg) { this.cfg = cfg; }

    // ------------------------------------------------------------------ recognising

    UUID rodOwner(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(rodOwnerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean isRod(ItemStack item) { return rodOwner(item) != null; }

    /** "fish:rust_minnow", "trophy:bilge_king", "junk:corroded_bolt", "lure:spinner", or null. */
    String tag(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    String kind(String tag) { return tag == null ? null : tag.substring(0, Math.max(0, tag.indexOf(':'))); }

    String id(String tag) { return tag == null ? null : tag.substring(tag.indexOf(':') + 1); }

    double weight(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Double d = item.getItemMeta().getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
        return d == null ? 0 : d;
    }

    /** Fish, trophies and junk: things the Fish Market buys. */
    boolean isSellable(ItemStack item) {
        String k = kind(tag(item));
        return "fish".equals(k) || "junk".equals(k) || "trophy".equals(k);
    }

    // ------------------------------------------------------------------ building

    ItemStack rod(UUID owner, FishingData d) {
        ItemStack item = new ItemStack(cfg.rodMaterial);
        apply(item, owner, d);
        return item;
    }

    void apply(ItemStack item, UUID owner, FishingData d) {
        RodTier t = cfg.tier(d.rodTier);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(rodOwnerKey, PersistentDataType.STRING, owner.toString());
        meta.displayName(Text.item(t.colored()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "rod/" + t.id()));
        meta.setUnbreakable(true);
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(t.index() >= 3);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Tier " + (t.index() + 1) + " rod · enchant cap " + t.maxEnchant() + " · "
                + t.lureSlots() + " lure slot" + (t.lureSlots() == 1 ? "" : "s"));
        if (!t.lore().isEmpty()) lore.add("<#8a8070><i>" + t.lore());
        lore.add("");
        boolean any = false;
        for (EnchantDef e : cfg.enchants.values()) {
            int lvl = d.enchant(e.id());
            if (lvl <= 0) continue;
            any = true;
            lore.add(e.colored() + " <white>" + lvl);
        }
        if (!any) lore.add("<dark_gray>No enchants yet");
        if (t.lureSlots() > 0) {
            lore.add("");
            for (int i = 0; i < t.lureSlots(); i++) {
                LureDef l = cfg.lures.get(d.lure(i));
                lore.add(l == null ? "<dark_gray>[ empty lure slot ]" : "<gray>[ </gray>" + lureName(l) + "<gray> ]");
            }
        }
        BaitDef b = cfg.baits.get(d.activeBait);
        lore.add("");
        lore.add(b == null || d.baitCount(b.id()) <= 0 ? "<gray>Bait: <dark_gray>none"
                : "<gray>Bait: <white>" + b.name() + " <dark_gray>x" + d.baitCount(b.id()));
        lore.add("<gray>Fishing level <white>" + d.level + "</white> · catches <white>" + Numbers.shortFormat(d.catches));
        lore.add("");
        lore.add("<#5fb4c8>Hold sneak</#5fb4c8> <gray>to reel in when a fish fights");
        lore.add("<#5fb4c8>Shift + left-click</#5fb4c8> <gray>to open the rod bay");
        lore.add("<dark_gray>Soulbound");
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
    }

    String lureName(LureDef l) { return cfg.rarity(l.rarity()).color() + l.name(); }

    ItemStack fish(FishDef f, int amount) {
        ItemStack item = new ItemStack(f.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        tag(meta, "fish:" + f.id());
        FishingConfig.Rarity r = cfg.rarity(f.rarity());
        meta.displayName(Text.item(r.color() + f.name()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "fish/" + f.id()));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(Text.lore(fishLore(f, -1)));
        item.setItemMeta(meta);
        return item;
    }

    /** Legendary catch: one of a kind, keeps its weight. */
    ItemStack trophy(FishDef f, double kg, String angler) {
        ItemStack item = new ItemStack(f.base());
        ItemMeta meta = item.getItemMeta();
        tag(meta, "trophy:" + f.id());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(weightKey, PersistentDataType.DOUBLE, kg);
        pdc.set(serialKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        FishingConfig.Rarity r = cfg.rarity(f.rarity());
        meta.displayName(Text.item(r.color() + "<bold>" + f.name()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "fish/" + f.id()));
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        List<String> lore = fishLore(f, kg);
        lore.add(lore.size() - 1, "<gray>Landed by <white>" + angler);
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> fishLore(FishDef f, double kg) {
        FishingConfig.Rarity r = cfg.rarity(f.rarity());
        FishingConfig.Area a = cfg.areas.get(f.area());
        List<String> lore = new ArrayList<>();
        lore.add(r.color() + r.name() + " fish <dark_gray>· " + (a == null ? "?" : a.colored()));
        if (!f.lore().isEmpty()) lore.add("<#8a8070><i>" + f.lore());
        lore.add("");
        if (kg > 0) lore.add("<gray>Weight <white>" + kg(kg));
        else lore.add("<gray>Usually " + kg(f.minKg()) + " to " + kg(f.maxKg()));
        lore.add("<dark_gray>Sell or gut it at the Fish Market (/fish)");
        return lore;
    }

    ItemStack junk(JunkDef j, int amount) {
        ItemStack item = new ItemStack(j.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        tag(meta, "junk:" + j.id());
        meta.displayName(Text.item(cfg.rarity("junk").color() + j.name()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "fishing/junk/" + j.id()));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(Text.lore(List.of("<#8a8070>Junk", "", "<dark_gray>Worth a few Credits at the Fish Market")));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack lure(LureDef l) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        tag(meta, "lure:" + l.id());
        meta.displayName(Text.item(lureName(l)));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "fishing/lure/" + l.id()));
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(Text.lore(lureLore(l, true)));
        item.setItemMeta(meta);
        return item;
    }

    List<String> lureLore(LureDef l, boolean asItem) {
        FishingConfig.Rarity r = cfg.rarity(l.rarity());
        List<String> lore = new ArrayList<>();
        lore.add(r.color() + r.name() + " lure");
        if (!l.lore().isEmpty()) lore.add("<#8a8070><i>" + l.lore());
        lore.add("");
        for (String s : l.effects().describe()) lore.add("<green>" + s);
        if (asItem) {
            lore.add("");
            lore.add("<gray>Open your rod bay and click this");
            lore.add("<gray>lure to slot it into your rod.");
        }
        return lore;
    }

    /** Menu icon with an optional pack model. */
    ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lore(lore));
        if (model != null && cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private void tag(ItemMeta meta, String tag) {
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
    }

    static String kg(double kg) {
        if (kg >= 100) return String.format(Locale.ROOT, "%.0f kg", kg);
        if (kg >= 10) return String.format(Locale.ROOT, "%.1f kg", kg);
        return String.format(Locale.ROOT, "%.2f kg", kg);
    }
}
