package dev.spacepunk.prison.quests;

import dev.spacepunk.prison.util.Text;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Quest characters as villagers placed by admins (/questadmin npc CHARACTER). Right-clicking one opens the quest log:
 * the contract board opens contracts, story characters open the story and greet you with their current line.
 * The resource pack can restyle them later; they stay vanilla villagers so no client mods are needed.
 */
final class QuestNpcs implements Listener {
    static final String BOARD = "board";
    private final QuestManager quests;
    private final QuestMenu menu;
    private final NamespacedKey key;

    QuestNpcs(Plugin plugin, QuestManager quests, QuestMenu menu) {
        this.quests = quests;
        this.menu = menu;
        this.key = new NamespacedKey(plugin, "quest_npc");
    }

    /** Spawns a quest NPC at the location. Returns false for an unknown character. */
    boolean spawn(Location at, String character) {
        String name;
        if (character.equals(BOARD)) {
            name = "<gold>Contract Board";
        } else {
            QuestConfig.Character c = quests.cfg().character(character);
            if (c == null) return false;
            name = c.display() + (c.title().isEmpty() ? "" : " <dark_gray>· <gray>" + c.title());
        }
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.customName(Text.mm(name));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(key, PersistentDataType.STRING, character);
        });
        return true;
    }

    /** Removes quest NPCs within radius. Returns how many. */
    int removeNear(Location at, double radius) {
        int n = 0;
        for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (e.getType() == EntityType.VILLAGER && e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        String character = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (character == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (character.equals(BOARD)) {
            menu.open(p, QuestMenu.Tab.CONTRACTS);
            return;
        }
        QuestData d = quests.data(p.getUniqueId());
        QuestConfig.Character c = quests.cfg().character(character);
        if (d != null && c != null) {
            QuestConfig.QuestDef step = quests.nextStoryStep(d);
            if (step != null && character.equals(step.character()) && !step.intro().isEmpty()) {
                quests.say(p, c, step.intro().get(step.intro().size() - 1));
            } else {
                quests.say(p, c, "Keep your head down and your hold full, {player}.");
            }
        }
        menu.open(p, QuestMenu.Tab.STORY);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(key, PersistentDataType.STRING)) e.setCancelled(true);
    }
}
