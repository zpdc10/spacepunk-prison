package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Builds and recognises rare drop items. The drop id in the item's data is the only state. */
final class RareItems {
    static final String NS = "spacepunk";
    private final NamespacedKey dropKey;
    private RareConfig cfg;

    RareItems(Plugin plugin, RareConfig cfg) {
        this.dropKey = new NamespacedKey(plugin, "rare_drop");
        this.cfg = cfg;
    }

    void config(RareConfig cfg) { this.cfg = cfg; }

    /** Drop id if this is a rare drop item, else null. */
    String dropId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(dropKey, PersistentDataType.STRING);
    }

    Drop drop(ItemStack item) { return cfg.drop(dropId(item)); }

    ItemStack create(Drop d, int amount) {
        ItemStack item = new ItemStack(d.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(dropKey, PersistentDataType.STRING, d.id());
        meta.displayName(Text.item(d.coloredName()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "raredrop/" + d.model()));
        meta.setMaxStackSize(64);
        meta.setEnchantmentGlintOverride(d.tier().index() >= 3 || "PRISTINE".equals(d.state()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.lore(Text.lore(lore(d)));
        item.setItemMeta(meta);
        return item;
    }

    List<String> lore(Drop d) {
        List<String> lore = new ArrayList<>();
        String kind = switch (d.type()) {
            case RELIC -> "Relic";
            case CACHE -> "Salvage Cache";
            case FRAGMENT -> "Blueprint Fragment";
            case PART -> switch (d.state() == null ? "" : d.state()) {
                case "FRIED" -> "Scrap Part · Fried";
                case "PRISTINE" -> "Scrap Part · Pristine";
                default -> "Scrap Part";
            };
        };
        lore.add(d.tier().colored("<bold>" + d.tier().name().toUpperCase() + "</bold> " + kind));
        if (!d.lore().isEmpty()) lore.add("<#8a8070><i>" + d.lore());
        lore.add("");
        switch (d.type()) {
            case RELIC -> {
                RareConfig.RelicSet set = cfg.sets.get(d.set());
                if (set != null) lore.add("<gray>Part of the " + set.coloredName() + " <gray>collection.");
                lore.add("<#c8743a>Right-click</#c8743a> <gray>to catalog it (<white>/salvage</white>).");
            }
            case CACHE -> {
                lore.add("<gray>Holds Credits" + (d.tokensMax() > 0 ? " and Tokens" : "") + ", more at higher grades.");
                lore.add("<#c8743a>Right-click</#c8743a> <gray>to crack it open, <#c8743a>sneak</#c8743a> <gray>for the stack.");
            }
            case FRAGMENT -> {
                int need = cfg.fragmentsNeeded(d.moduleRarity());
                lore.add("<gray>Decode <white>" + need + "</white> into a new " + titleCase(d.moduleRarity()));
                lore.add("<gray>module schematic in <white>/salvage</white>.");
            }
            case PART -> {
                switch (d.state() == null ? "" : d.state()) {
                    case "FRIED" -> {
                        lore.add("<gray>Burnt out. Repair it at the <white>Refurbisher</white>");
                        lore.add("<gray>in your workshop (it may come out pristine).");
                    }
                    case "PRISTINE" -> {
                        lore.add("<gold>Perfect condition.</gold> <gray>Counts as a working part,");
                        lore.add("<gray>and fuses modules to <white>Mk IV</white> and <white>Mk V</white>.");
                    }
                    default -> lore.add("<gray>Builds drill, rod and weapon modules at the <white>Assembly Bench</white>.");
                }
            }
        }
        lore.add("<dark_gray>Tradeable on the Black Market (/ah)");
        return lore;
    }

    /** A plain menu icon. */
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

    static String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
