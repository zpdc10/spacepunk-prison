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
    private final NamespacedKey boreKey;
    private DrillConfig cfg;

    public DrillItems(Plugin plugin, DrillConfig cfg) {
        this.ownerKey = new NamespacedKey(plugin, "drill_owner");
        this.moduleKey = new NamespacedKey(plugin, "drill_module");
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
        lore.add("<dark_gray>" + f.name() + " frame · " + f.sockets() + " socket" + (f.sockets() == 1 ? "" : "s"));
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
        if (f.sockets() > 0) {
            lore.add("");
            for (int i = 0; i < f.sockets(); i++) {
                ModuleDef m = cfg.module(d.socket(i));
                lore.add(m == null ? "<dark_gray>[ empty socket ]" : "<gray>[ </gray>" + m.coloredName() + "<gray> ]");
            }
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

    public ItemStack module(ModuleDef m) {
        ItemStack item = new ItemStack(m.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(moduleKey, PersistentDataType.STRING, m.id());
        meta.displayName(Text.item(m.coloredName()));
        if (cfg.useItemModels) meta.setItemModel(new NamespacedKey(NS, "module/" + m.model()));
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        meta.lore(Text.lore(moduleLore(m, true)));
        item.setItemMeta(meta);
        return item;
    }

    public List<String> moduleLore(ModuleDef m, boolean asItem) {
        List<String> lore = new ArrayList<>();
        lore.add(m.rarity().color + m.rarity().label + " drill module");
        if (!m.lore().isEmpty()) lore.add("<#8a8070><i>" + m.lore());
        lore.add("");
        for (String s : m.pros()) lore.add("<green>+ " + s);
        for (String s : m.cons()) lore.add("<red>- " + s);
        if (asItem) {
            lore.add("");
            lore.add("<gray>Open your drill bay and click this");
            lore.add("<gray>module to slot it into a free socket.");
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
