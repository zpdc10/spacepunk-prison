package dev.spacepunk.prison.api;

/**
 * Shared quest objective type ids and the targets each one reports. The quests module reports the "core" types
 * itself from existing events. The others belong to systems still being built: the module that owns the activity
 * reports it with {@link Objectives#report} and registers it once with {@link Objectives#register}.
 * The quests module loads right after the resource pack, before every other feature module, so calling
 * {@link Objectives#register} from your module's enable() works.
 */
public final class ObjectiveTypes {
    private ObjectiveTypes() {}

    // ---- core, reported by the quests module
    /** Blocks broken in a mine. Targets: "mine:&lt;id&gt;", "deck:&lt;1-5&gt;", "grade:&lt;n&gt;". */
    public static final String MINE_BLOCKS = "mine_blocks";
    /** Blocks of one kind broken in a mine. Targets: item id, e.g. "minecraft:copper_ore". */
    public static final String MINE_ITEM = "mine_item";
    /** Credits earned by selling ore (amount = Credits). Targets: none. */
    public static final String SELL_CREDITS = "sell_credits";
    /** Tokens found while mining (amount = Tokens). */
    public static final String EARN_TOKENS = "earn_tokens";
    /** Grade rank-ups (amount = grades gained). Targets: "grade:&lt;new grade&gt;". */
    public static final String RANK_UP = "rank_up";
    /** Absolute: progress is the player's grade. */
    public static final String REACH_GRADE = "reach_grade";
    /** Prestiges. Targets: "prestige:&lt;new prestige&gt;". */
    public static final String PRESTIGE = "prestige";
    /** Drill enchant levels bought. Targets: "enchant:&lt;id&gt;". */
    public static final String ENCHANT_UPGRADE = "enchant_upgrade";
    /** Drill frames installed. Targets: "frame:&lt;id&gt;". */
    public static final String FRAME_UPGRADE = "frame_upgrade";
    /** Cargo hold capacity upgrades bought. */
    public static final String CARGO_UPGRADE = "cargo_upgrade";
    /** Auto-Seller unlocked. */
    public static final String AUTOSELL_UNLOCK = "autosell_unlock";
    /** Items handed in from the cargo hold through the quest log. Targets: item id. */
    public static final String DELIVER_CARGO = "deliver_cargo";
    /** Minutes played. */
    public static final String PLAY_MINUTES = "play_minutes";
    /** Daily parkour courses finished (reported by the parkour module). Targets: "mine:&lt;id&gt;", "deck:&lt;1-5&gt;". */
    public static final String PARKOUR_COMPLETE = "parkour_complete";
    /** Contracts completed (reported by the quests module). Targets: "daily", "weekly". */
    public static final String CONTRACT_COMPLETE = "contract_complete";

    // ---- reported by systems still being built (contracts and tutorials for them stay hidden until registered)
    /** Fishing: fish caught. Targets: fish item id, "rarity:&lt;common|rare|...&gt;", "zone:&lt;id&gt;". */
    public static final String CATCH_FISH = "catch_fish";
    /** Workshop: items produced by a machine (amount = items). Targets: output item id, "machine:&lt;id&gt;". */
    public static final String PROCESS_ITEM = "process_item";
    /** Workshop: machines built or upgraded. Targets: "machine:&lt;id&gt;". */
    public static final String MACHINE_UPGRADE = "machine_upgrade";
    /** Gangs: joined or founded a gang. */
    public static final String GANG_JOIN = "gang_join";
    /** Gangs: Credits deposited into a gang bank or contribution points earned. */
    public static final String GANG_CONTRIBUTE = "gang_contribute";
    /** Auction house: items sold. Targets: item id. */
    public static final String AUCTION_SELL = "auction_sell";
    /** Auction house: items bought. */
    public static final String AUCTION_BUY = "auction_buy";
    /** Rare drops found. Targets: drop id, "rarity:&lt;tier&gt;". */
    public static final String RARE_DROP = "rare_drop";
    /** Scrap parts found. Targets: part id, "state:&lt;fried|working|pristine&gt;", "source:&lt;activity&gt;", "rarity:&lt;tier&gt;". */
    public static final String SCRAP_FIND = "scrap_find";
    /** Crates opened. Targets: "crate:&lt;id&gt;". */
    public static final String CRATE_OPEN = "crate_open";
    /** Undercroft: fights won / players downed. */
    public static final String UNDERCROFT_WIN = "undercroft_win";
    /** Undercroft: contraband smuggled out (amount = runs or value, owner decides). */
    public static final String SMUGGLE = "smuggle";
    /** Guns: shots hit or kills with guns. Targets: "gun:&lt;id&gt;". */
    public static final String GUN_KILL = "gun_kill";
}
