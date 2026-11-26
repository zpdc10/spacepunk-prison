package dev.spacepunk.prison.core;

import dev.spacepunk.prison.SpacepunkPrison;

/**
 * One feature area (mines, progression, drill, resourcepack...). Each lives in its own package and is
 * registered in {@link SpacepunkPrison#modules()}. Modules talk to each other only through dev.spacepunk.prison.api.
 */
public interface FeatureModule {
    /** Short id, also used as the PlayerProfile moduleData key and config section name. */
    String id();

    /** Register listeners, commands, services. Called in plugin order after core is up. */
    void enable(SpacepunkPrison plugin) throws Exception;

    /** Save and clean up. Called in reverse order. */
    default void disable() {}
}
