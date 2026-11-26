package dev.spacepunk.prison.mines;

/** Fills a region with a block mix. Called on the main thread; onDone always runs on the main thread. */
interface Refiller {
    String name();

    void fill(Region region, BlockMix mix, Runnable onDone);

    default void shutdown() {}
}
