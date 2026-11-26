package dev.spacepunk.prison.workshop;

import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import dev.spacepunk.prison.workshop.WorkshopConfig.Good;
import dev.spacepunk.prison.workshop.WorkshopConfig.MachineDef;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Builds and recognises workshop items: machines, conveyors and goods. Items are views; state is in BayData. */
final class WorkshopItems {
    private final Supplier<WorkshopConfig> cfg;
    private final Function<String, String> rawName;
    private final NamespacedKey machineKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey conveyorKey;
    private final NamespacedKey goodKey;

    WorkshopItems(Plugin plugin, Supplier<WorkshopConfig> cfg, Function<String, String> rawName) {
        this.cfg = cfg;
        this.rawName = rawName;
        this.machineKey = new NamespacedKey(plugin, "ws_machine");
        this.tierKey = new NamespacedKey(plugin, "ws_tier");
        this.conveyorKey = new NamespacedKey(plugin, "ws_conveyor");
        this.goodKey = new NamespacedKey(plugin, "ws_good");
    }

    static NamespacedKey model(String path) { return new NamespacedKey("spacepunk", path); }

    // ---- names ----

    /** Plain display name of an item key (quality prefix included, MiniMessage). */
    String name(String key) {
        String base = BayData.base(key);
        Good g = cfg.get().goods.get(base);
        String n = g != null ? g.name() : rawName.apply(base);
        int q = BayData.quality(key);
        return cfg.get().quality(q).prefix() + n;
    }

    String color(String key) {
        Good g = cfg.get().goods.get(BayData.base(key));
        if (g == null) return "<#9c8a6e>";
        return switch (g.stage()) {
            case RAW, BYPRODUCT -> "<#9c8a6e>";
            case CRUSHED -> "<#b0a090>";
            case WASHED -> "<#7fb8d6>";
            case REFINED -> "<#e0a050>";
            case ALLOY -> "<#d6c36a>";
            case COMPONENT -> "<#6fd6a8>";
        };
    }

    // ---- machines ----

    ItemStack machine(MachineDef def, int tier, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        WorkshopConfig c = cfg.get();
        it.editMeta(meta -> {
            meta.setItemModel(model(def.model()));
            meta.displayName(Text.item("<#c8743a>" + def.name() + " <gray>" + c.tier(tier).name()));
            meta.lore(Text.lore(machineLore(def, tier, true)));
            meta.addItemFlags(ItemFlag.values());
            if (tier >= 2) meta.setEnchantmentGlintOverride(true);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(machineKey, PersistentDataType.STRING, def.id());
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        });
        return it;
    }

    List<String> machineLore(MachineDef def, int tier, boolean placeHint) {
        WorkshopConfig c = cfg.get();
        WorkshopConfig.Tier t = c.tier(tier);
        List<String> lore = new ArrayList<>();
        if (!def.lore().isEmpty()) lore.add("<gray>" + def.lore());
        lore.add("");
        switch (def.role()) {
            case GENERATOR -> lore.add("<yellow>⚡ Makes " + fmt(def.speed() * t.speed()) + " power/s");
            case PUMP -> lore.add("<aqua>❄ Makes " + fmt(def.speed() * t.speed()) + " coolant/s");
            case RADIATOR -> lore.add("<aqua>Cools each touching machine by " + fmt(def.speed() * t.speed()) + " heat/s");
            case SOURCE -> lore.add("<gray>Pulls <white>" + fmt(def.speed() * t.speed()) + "</white> items/s");
            case PROCESSOR -> lore.add("<gray>Speed <white>x" + fmt(t.speed()));
            default -> { }
        }
        if (def.power() > 0) lore.add("<yellow>⚡ Uses " + fmt(def.power() * t.power()) + " power/s");
        if (def.coolant() > 0) lore.add("<aqua>❄ Uses " + fmt(def.coolant()) + " coolant/s");
        if (def.heat() > 0) lore.add("<red>🔥 " + fmt(def.heat() * t.heat()) + " heat/s while running");
        if (def.quality() && (t.refined() > 0 || t.pristine() > 0)) {
            lore.add("<#7fb8d6>Refined " + pct(t.refined()) + "<gray>, <light_purple>Pristine " + pct(t.pristine()));
        }
        if (placeHint) {
            lore.add("");
            lore.add("<dark_gray>Right-click a block in your bay to place.");
            lore.add("<dark_gray>Output points the way you face.");
            lore.add("<dark_gray>Sneak + right-click a placed machine to pick it up.");
        }
        return lore;
    }

    /** Machine id of a machine item, or null. */
    String machineId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(machineKey, PersistentDataType.STRING);
    }

    int machineTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer t = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return t == null ? 0 : t;
    }

    /** The model item an ItemDisplay shows for a placed machine. */
    ItemStack displayModel(String type) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(meta -> meta.setItemModel(model("workshop/machine/" + type)));
        return it;
    }

    // ---- conveyors ----

    ItemStack conveyor(int amount) {
        ItemStack it = new ItemStack(Material.REPEATER, Math.max(1, amount));
        it.editMeta(meta -> {
            meta.setItemModel(model("workshop/conveyor"));
            meta.displayName(Text.item("<#c8743a>Conveyor"));
            meta.lore(Text.lore(List.of(
                    "<gray>Carries items between machines.",
                    "<gray>Place it in your bay: it runs the way you face.",
                    "<gray>A machine pushing into a conveyor line feeds",
                    "<gray>the machine at the end of the line.",
                    "<dark_gray>Sneak + right-click a placed conveyor to pick it up.")));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(conveyorKey, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    boolean isConveyor(ItemStack item) {
        return item != null && item.getType() == Material.REPEATER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(conveyorKey, PersistentDataType.BYTE);
    }

    // ---- goods ----

    /** A physical goods item (spacepunk: goods only), or null for unknown ids. */
    ItemStack good(String key, int amount) {
        Good g = cfg.get().goods.get(BayData.base(key));
        if (g == null) return null;
        int q = BayData.quality(key);
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        it.editMeta(meta -> {
            meta.setItemModel(model(g.model()));
            meta.displayName(Text.item(color(key) + name(key)));
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + stageName(g.stage()));
            lore.add("<gray>Base value: <gold>" + Numbers.shortFormat(Math.round(g.price() * cfg.get().quality(q).price())) + " Credits");
            lore.add("");
            lore.add("<dark_gray>Sell at the Trade Terminal (/ws market).");
            lore.add("<dark_gray>Click it in the Bay Locker to store it.");
            meta.lore(Text.lore(lore));
            if (q >= 2) meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(goodKey, PersistentDataType.STRING, key);
        });
        return it;
    }

    /** Item key of a goods item, or null. */
    String goodKey(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(goodKey, PersistentDataType.STRING);
    }

    static String stageName(WorkshopConfig.Stage s) {
        return switch (s) {
            case RAW -> "Raw ore";
            case CRUSHED -> "Crushed";
            case WASHED -> "Washed";
            case REFINED -> "Refined good";
            case ALLOY -> "Alloy";
            case COMPONENT -> "Component";
            case BYPRODUCT -> "Byproduct";
        };
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return Long.toString(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static String pct(double v) { return Math.round(v * 100) + "%"; }
}
