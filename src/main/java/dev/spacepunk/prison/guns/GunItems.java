package dev.spacepunk.prison.guns;

import dev.spacepunk.prison.guns.GunsConfig.AmmoDef;
import dev.spacepunk.prison.guns.GunsConfig.GunDef;
import dev.spacepunk.prison.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds and reads gun, ammo and gun-part items. A gun stores its id and loaded rounds on the item. */
public final class GunItems {
    static final String NS = "spacepunk";
    static final NamespacedKey GUN = new NamespacedKey(NS, "gun_id");
    static final NamespacedKey LOADED = new NamespacedKey(NS, "gun_loaded");
    static final NamespacedKey AMMO = new NamespacedKey(NS, "ammo_type");
    static final NamespacedKey PARTS = new NamespacedKey(NS, "gun_parts");

    private GunsConfig cfg;

    GunItems(GunsConfig cfg) { this.cfg = cfg; }

    void config(GunsConfig cfg) { this.cfg = cfg; }

    // ---- reading ----

    public String gunId(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        return pdc == null ? null : pdc.get(GUN, PersistentDataType.STRING);
    }

    public GunDef gun(ItemStack item) {
        String id = gunId(item);
        return id == null ? null : cfg.guns.get(id);
    }

    public int loaded(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        if (pdc == null) return 0;
        Integer v = pdc.get(LOADED, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public String ammoType(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        return pdc == null ? null : pdc.get(AMMO, PersistentDataType.STRING);
    }

    public boolean isParts(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        return pdc != null && pdc.has(PARTS, PersistentDataType.BYTE);
    }

    /** True for any item this module made (guns, ammo, parts). */
    public boolean isOurs(ItemStack item) {
        return gunId(item) != null || ammoType(item) != null || isParts(item);
    }

    private static PersistentDataContainer pdc(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer();
    }

    // ---- building ----

    public ItemStack gun(GunDef g, int loaded) {
        ItemStack item = new ItemStack(cfg.gunMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(GUN, PersistentDataType.STRING, g.id());
        meta.setMaxStackSize(1);
        meta.displayName(Text.item(g.name()));
        if (cfg.itemModels) meta.setItemModel(new NamespacedKey(NS, "gun/" + g.id()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        AmmoDef a = cfg.ammo.get(g.ammo());
        List<String> lore = new ArrayList<>();
        if (!g.lore().isEmpty()) lore.add("<#8a8070><i>" + g.lore());
        lore.add("");
        String dmg = g.pellets() > 1 ? fmt(g.damage()) + " x" + g.pellets() : fmt(g.damage());
        lore.add("<gray>Damage <white>" + dmg + "</white>  <dark_gray>·</dark_gray>  Headshot <white>x" + fmt(g.headshot()));
        lore.add("<gray>Fire rate <white>" + fmt(20.0 * g.burst() / Math.max(1, g.fireDelay() + (g.burst() - 1) * g.burstDelay()))
                + "/s</white>  <dark_gray>·</dark_gray>  Range <white>" + (int) g.range() + "m");
        lore.add("<gray>Magazine <white>" + g.magazine() + "</white>  <dark_gray>·</dark_gray>  Ammo " + (a == null ? g.ammo() : a.name()));
        if (g.pierce() > 0) lore.add("<gray>Pierces <white>" + g.pierce() + "</white> target" + (g.pierce() == 1 ? "" : "s"));
        lore.add("");
        lore.add("<#c8743a>Right-click</#c8743a> <gray>to fire" + (g.zoom() > 0 ? ", <#c8743a>sneak</#c8743a> to scope" : ", <#c8743a>sneak</#c8743a> to steady"));
        lore.add("<#c8743a>Q</#c8743a> <gray>or</gray> <#c8743a>F</#c8743a> <gray>to reload");
        lore.add("<dark_gray>Only fires in PvP zones. Drops when you die.");
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        setLoaded(item, g, loaded);
        return item;
    }

    /** Writes the loaded round count and the durability bar that shows it. */
    public void setLoaded(ItemStack item, GunDef g, int loaded) {
        ItemMeta meta = item.getItemMeta();
        int v = Math.max(0, Math.min(g.magazine(), loaded));
        meta.getPersistentDataContainer().set(LOADED, PersistentDataType.INTEGER, v);
        if (meta instanceof Damageable d) {
            d.setMaxDamage(g.magazine() + 1);
            d.setDamage(g.magazine() - v);
        }
        item.setItemMeta(meta);
    }

    public ItemStack ammo(AmmoDef a, int amount) {
        ItemStack item = new ItemStack(cfg.ammoMaterial, Math.max(1, Math.min(99, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(AMMO, PersistentDataType.STRING, a.id());
        meta.setMaxStackSize(99);
        meta.displayName(Text.item(a.name()));
        if (cfg.itemModels) meta.setItemModel(new NamespacedKey(NS, "ammo/" + a.id()));
        List<String> lore = new ArrayList<>();
        if (!a.lore().isEmpty()) lore.add("<#8a8070><i>" + a.lore());
        lore.add("<gray>Ammo for: <white>" + String.join(", ", cfg.guns.values().stream()
                .filter(g -> g.ammo().equals(a.id())).map(g -> Text.MM.stripTags(g.name())).toList()));
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack parts(int amount) {
        ItemStack item = new ItemStack(cfg.partsMaterial, Math.max(1, Math.min(99, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PARTS, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(99);
        meta.displayName(Text.item(cfg.partsName));
        if (cfg.itemModels) meta.setItemModel(new NamespacedKey(NS, "gun/parts"));
        List<String> lore = new ArrayList<>();
        if (!cfg.partsLore.isEmpty()) lore.add("<#8a8070><i>" + cfg.partsLore);
        lore.add("<gray>Spend at the <white>/gunsmith</white> on better guns.");
        lore.add("<dark_gray>Found in Undercroft supply drops.");
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
