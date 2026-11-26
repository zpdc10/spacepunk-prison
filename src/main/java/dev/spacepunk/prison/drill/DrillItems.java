package dev.spacepunk.prison.drill;

import dev.spacepunk.prison.drill.DrillConfig.FrameDef;
import dev.spacepunk.prison.drill.DrillConfig.ModuleDef;
import dev.spacepunk.prison.util.Numbers;
import dev.spacepunk.prison.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds and recognises drill and module items. The item is only a view; state lives in {@link DrillData}. */
public final class DrillItems {
    public static final String NS = "spacepunk";
    private final NamespacedKey ownerKey;
    private final NamespacedKey moduleKey;
    private final NamespacedKey markKey;
    private final NamespacedKey boreKey;
    private DrillConfig cfg;

    public DrillItems(Plugin plugin, DrillConfig cfg) {
        this.ownerKey = new NamespacedKey(plugin, "drill_owner");
        this.moduleKey = new NamespacedKey(plugin, "drill_module");
        this.markKey = new NamespacedKey(plugin, "drill_module_mk");
        this.boreKey = new NamespacedKey(NS, "drill_bore");
        this.cfg = cfg;
    }

    public void config(DrillConfig cfg) { this.cfg = cfg; }

    // ---- recognising ----

    /** Owner UUID if this is a drill, else null. */
    public UUID owner(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isDrill(ItemStack item) { return owner(item) != null; }

    /** Module id if this is a module item, else null. */
    public String moduleId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(moduleKey, PersistentDataType.STRING);
    }

    /** Module mark (1 = Mk I) of a module item, or 0 if it isn't one. */
    public int moduleMark(ItemStack item) {
        if (moduleId(item) == null) return 0;
        Integer mk = item.getItemMeta().getPersistentDataContainer().get(markKey, PersistentDataType.INTEGER);
        return mk == null ? 1 : Math.max(1, mk);
    }

    // ---- building ----

    public ItemStack drill(UUID owner, DrillData d) {
        ItemStack item = new ItemStack(cfg.itemMaterial);
        apply(item, owner, d);
        return item;
    }

    /** Rewrites name, lore, model and speed on an existing drill item to match the data. */
    public void apply(ItemStack item, UUID owner, DrillData d) {
        DrillMath.Effective eff = new DrillMath.Effective(cfg, d);
        FrameDef f = eff.frame;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        meta.displayName(Text.item(f.coloredName() + " <#d8d0c0>Drill</#d8d0c0>"));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "drill/" + f.id()));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(f.index() >= 3);
        meta.setMaxStackSize(1);

        meta.removeAttributeModifier(Attribute.BLOCK_BREAK_SPEED);
        meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(boreKey, eff.breakSpeed - 1.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        ModuleRig rig = eff.rig;
        lore.add("<dark_gray>" + f.name() + " frame" + (rig.cellCount() > 0 ? " · " + rig.width + "x" + rig.height + " module rig" : ""));
        if (!f.lore().isEmpty()) lore.add("<#8a8070><i>" + f.lore());
        lore.add("");
        boolean any = false;
        for (Enchant e : Enchant.values()) {
            int lvl = d.level(e);
            if (lvl <= 0) continue;
            any = true;
            lore.add(cfg.enchant(e).coloredName() + " <white>" + lvl);
        }
        if (!any) lore.add("<dark_gray>No upgrades yet");
        if (!rig.entries.isEmpty()) {
            lore.add("");
            for (ModuleRig.Entry e : rig.entries) {
                lore.add((e.online ? "<gray>▪ " : "<red>▪ ") + e.def.coloredName(e.placed.mk) + (e.online ? "" : " <red>(offline)"));
            }
            for (DrillConfig.ComboDef combo : rig.activeCombos) lore.add("<gold>✦ " + combo.name());
        }
        lore.add("");
        lore.add("<gray>Speed <white>x" + DrillMath.fmt(eff.breakSpeed) + "</white> · Heat limit <white>"
                + Math.round(eff.heatCapacity) + "</white>");
        lore.add("<gray>Blocks drilled <white>" + Numbers.shortFormat(d.blocksMined));
        lore.add("");
        lore.add("<#c8743a>Shift + right-click</#c8743a> <gray>to open the drill bay");
        lore.add("<#c8743a>Q</#c8743a> <gray>to vent heat");
        lore.add("<dark_gray>Soulbound");
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
    }

    public ItemStack module(ModuleDef m) { return module(m, 1); }

    public ItemStack module(ModuleDef m, int mk) {
        int mark = Math.max(1, Math.min(cfg.maxMark, mk));
        ItemStack item = new ItemStack(m.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(moduleKey, PersistentDataType.STRING, m.id());
        meta.getPersistentDataContainer().set(markKey, PersistentDataType.INTEGER, mark);
        meta.displayName(Text.item(m.coloredName(mark)));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "module/" + m.model()));
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(mark >= 3);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.lore(Text.lore(moduleLore(m, mark, 1.0, true)));
        item.setItemMeta(meta);
        return item;
    }

    /** Module tooltip: rarity, family, shape picture, effects at this mark, rules. */
    public List<String> moduleLore(ModuleDef m, int mk, double drain, boolean asItem) {
        List<String> lore = new ArrayList<>();
        DrillConfig.FamilyDef fam = cfg.family(m.family());
        lore.add(m.rarity().color + m.rarity().label + " " + DrillConfig.kindLabel(m.kind()) + (fam == null ? "" : " <dark_gray>· " + fam.coloredName()));
        if (!m.lore().isEmpty()) lore.add("<#8a8070><i>" + m.lore());
        lore.add("");
        lore.addAll(DrillMath.effectLines(cfg, m, mk, drain));
        if (drain > 1.0) lore.add("<dark_purple>Downsides boosted by a Void neighbour");
        lore.add("");
        String fill = m.rarity().color;
        lore.add("<gray>Shape: <white>" + m.shape().label + (m.needsCore() ? " <gold>(needs a core cell)" : ""));
        lore.addAll(m.shape().picture(0, fill));
        if (m.edge()) lore.add("<yellow>⚠ Must sit on the edge of the rig");
        if (m.needs() != null) {
            DrillConfig.FamilyDef f = cfg.family(m.needs());
            lore.add("<yellow>⚠ Must touch a " + (f == null ? m.needs() : f.coloredName()) + "<yellow> module");
        }
        if (m.avoid() != null) {
            DrillConfig.FamilyDef f = cfg.family(m.avoid());
            lore.add("<yellow>⚠ Can't touch a " + (f == null ? m.avoid() : f.coloredName()) + "<yellow> module");
        }
        if (m.drain() > 1.0) lore.add("<dark_purple>⚠ Drains touching modules: their downsides grow");
        if (fam != null && fam.max() < 99) lore.add("<dark_gray>Max " + fam.max() + " " + fam.name() + " on a rig");
        if (mk < cfg.maxMark) lore.add("<dark_gray>Fuse two " + DrillMath.roman(mk) + " to make " + DrillMath.roman(mk + 1));
        else lore.add("<gold>Top mark");
        if (asItem) {
            lore.add("");
            switch (m.kind()) {
                case DrillConfig.KIND_ROD -> {
                    lore.add("<gray>Fit it on your Rod Rig: <white>/rig rod");
                    lore.add("<gray>Boosts your fishing rod.");
                }
                case DrillConfig.KIND_EXO -> {
                    lore.add("<gray>Fit it on your Exo-Rig: <white>/rig exo");
                    lore.add("<gray>Boosts you, whatever you're doing.");
                }
                case DrillConfig.KIND_GUN -> {
                    lore.add("<gray>Fit it on a Weapon Rig: <white>/rig gun");
                    lore.add("<gray>Each gun model has its own rig.");
                }
                default -> {
                    lore.add("<gray>Fit it in your drill's module rig");
                    lore.add("<gray>(shift + right-click the drill, or <white>/rig</white><gray>).");
                }
            }
            lore.add("<dark_gray>Tradeable on the Black Market (/ah)");
        }
        return lore;
    }

    /** A menu icon: name, lore, optional pack model. */
    public ItemStack icon(Material mat, String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lore(lore));
        if (model != null && cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, model));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
