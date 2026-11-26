package dev.spacepunk.prison.quests;

/** Where a quest comes from. The prefix is used in tracked-quest keys and EconomyService reasons. */
enum QuestKind {
    STORY("story"), TUTORIAL("tutorial"), DAILY("daily"), WEEKLY("weekly");

    final String key;

    QuestKind(String key) { this.key = key; }

    boolean contract() { return this == DAILY || this == WEEKLY; }
}
