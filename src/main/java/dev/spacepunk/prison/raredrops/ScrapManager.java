package dev.spacepunk.prison.raredrops;

import dev.spacepunk.prison.api.DrillService;
import dev.spacepunk.prison.api.HudService;
import dev.spacepunk.prison.api.ObjectiveTypes;
import dev.spacepunk.prison.api.Objectives;
import dev.spacepunk.prison.api.ScrapService;
import dev.spacepunk.prison.api.Services;
import dev.spacepunk.prison.raredrops.RareConfig.Drop;
import dev.spacepunk.prison.raredrops.RareConfig.PartDef;
import dev.spacepunk.prison.raredrops.RareConfig.Source;
import dev.spacepunk.prison.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Scrap parts: rolls finds for every activity, delivers and announces them, counts and takes parts. Main thread. */
final class ScrapManager implements ScrapService {
    private final RareDropsManager rare;

    ScrapManager(RareDropsManager rare) { this.rare = rare; }

    private RareConfig cfg() { return rare.cfg(); }

    @Override
    public int roll(Player p, String source, int trials, double chanceMultiplier) {
        if (p == null || trials <= 0) return 0;
        RareData d = rare.data(p.getUniqueId());
        Source src = cfg().sources.get(source);
        if (d == null || src == null || src.chance() <= 0) return 0;
        DrillService ds = Services.find(DrillService.class).orElse(null);
        double drill = 1.0;
        if (ds != null) {
            // Mining-side finds use the drill rig; fishing finds use the Rod Rig.
            boolean fishing = FISHING.equals(source) || SEA_CREATURE.equals(source);
            drill = fishing ? ds.gearMultipliers(p.getUniqueId(), "rod").getOrDefault("scrap", 1.0) : ds.scrapMultiplier(p.getUniqueId());
        }
        double chance = Math.min(1.0, src.chance() * chanceMultiplier * drill);
        int hits = RareDropsManager.successes(trials, chance, src.maxPerEvent());
        int found = 0;
        for (int i = 0; i < hits; i++) {
            PartDef part = pickPart(source);
            if (part == null) break;
            String state = pickState();
            Drop drop = cfg().partDrop(part.id(), state);
            if (drop == null) continue;
            boolean fit = rare.give(p, d, drop, 1);
            d.found.merge(drop.id(), 1L, Long::sum);
            d.scrapFound++;
            announce(p, drop, fit);
            Objectives.report(p, ObjectiveTypes.SCRAP_FIND, 1, part.id(), "state:" + state.toLowerCase(java.util.Locale.ROOT),
                    "source:" + source, "rarity:" + part.tier().id());
            found++;
        }
        if (found > 0) rare.save(p.getUniqueId());
        return found;
    }

    private PartDef pickPart(String source) {
        List<PartDef> pool = new ArrayList<>();
        double total = 0;
        for (PartDef part : cfg().parts.values()) {
            if (part.weight() > 0 && part.from(source)) {
                pool.add(part);
                total += part.weight();
            }
        }
        if (pool.isEmpty()) return null;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (PartDef part : pool) {
            r -= part.weight();
            if (r <= 0) return part;
        }
        return pool.getLast();
    }

    private String pickState() {
        RareConfig c = cfg();
        double total = c.friedWeight + c.workingWeight + c.pristineWeight;
        if (total <= 0) return "WORKING";
        double r = ThreadLocalRandom.current().nextDouble() * total;
        if ((r -= c.friedWeight) < 0) return "FRIED";
        if (r - c.workingWeight < 0) return "WORKING";
        return "PRISTINE";
    }

    private void announce(Player p, Drop drop, boolean fit) {
        boolean pristine = "PRISTINE".equals(drop.state());
        int tier = drop.tier().index();
        String where = fit ? " <dark_gray>→</dark_gray> <gray>inventory" : " <dark_gray>→</dark_gray> <yellow>at your feet";
        String flash = (pristine ? "<#ffe08a>✦ Pristine scrap:</#ffe08a> " : "<#c8a070>⚙ Scrap:</#c8a070> ") + drop.coloredName() + where;
        HudService hud = Services.find(HudService.class).orElse(null);
        if (hud != null) hud.flash(p, flash);
        else p.sendActionBar(Text.mm(flash));
        p.playSound(Sound.sound(Key.key(pristine ? "block.amethyst_block.chime" : "block.chain.place"), Sound.Source.MASTER, 0.9f, pristine ? 1.5f : 1.2f));
        Component name = Text.mm(drop.coloredName()).hoverEvent(rare.items().create(drop, 1).asHoverEvent());
        if ((pristine && tier >= 3) || tier >= 4) {
            p.showTitle(Title.title(Text.mm(drop.coloredName()), Text.mm("<gray>Scrap part salvaged!")));
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<white>" + p.getName() + "</white> <gray>salvaged ").append(name));
        } else if (pristine || tier >= 2) {
            p.sendMessage(Text.mm(Text.PREFIX + "<gray>Salvaged ").append(name)
                    .append(Text.mm(fit ? " <gray>(in your inventory)" : " <yellow>(dropped at your feet)")));
        }
    }

    // ---- ScrapService ----

    @Override public List<String> partIds() { return List.copyOf(cfg().parts.keySet()); }

    @Override
    public String partName(String partId, State state) {
        Drop d = cfg().partDrop(partId, state.name());
        return d == null ? partId : d.coloredName();
    }

    @Override
    public Optional<Part> part(ItemStack item) {
        Drop d = rare.items().drop(item);
        if (d == null || d.type() != RareConfig.Type.PART) return Optional.empty();
        return Optional.of(new Part(d.partId(), State.parse(d.state()), d.tier().id()));
    }

    @Override
    public ItemStack create(String partId, State state, int amount) {
        Drop d = cfg().partDrop(partId, state.name());
        return d == null ? null : rare.items().create(d, amount);
    }

    @Override
    public int count(Player p, String partId, State atLeast) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            Optional<Part> part = part(it);
            if (part.isPresent() && part.get().partId().equals(partId) && part.get().state().ordinal() >= atLeast.ordinal()) n += it.getAmount();
        }
        return n;
    }

    @Override
    public boolean take(Player p, String partId, State atLeast, int amount) {
        if (amount <= 0) return true;
        if (count(p, partId, atLeast) < amount) return false;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (State st : State.values()) {
            if (st.ordinal() < atLeast.ordinal()) continue;
            for (int i = 0; i < contents.length && amount > 0; i++) {
                Optional<Part> part = part(contents[i]);
                if (part.isEmpty() || !part.get().partId().equals(partId) || part.get().state() != st) continue;
                int n = Math.min(amount, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - n);
                if (contents[i].getAmount() <= 0) contents[i] = null;
                amount -= n;
            }
        }
        inv.setStorageContents(contents);
        return true;
    }

    @Override
    public boolean give(Player p, ItemStack item) {
        boolean fit = true;
        for (ItemStack over : p.getInventory().addItem(item).values()) {
            fit = false;
            p.getWorld().dropItem(p.getLocation(), over, it -> {
                it.setOwner(p.getUniqueId());
                it.setPickupDelay(0);
                it.setUnlimitedLifetime(true);
                it.setGlowing(true);
            });
        }
        if (!fit) Text.send(p, "<yellow>Inventory full:</yellow> <gray>the rest dropped at your feet.");
        return fit;
    }
}
