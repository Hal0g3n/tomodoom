package com.example.tomodoom.stats;

import androidx.annotation.NonNull;

/**
 * A copy of all recorded stats at one moment, published by {@code StatsRepository}. The ledgers
 * inside are private copies that nobody mutates, so a snapshot is safe to read on any thread.
 * Build a {@link StatsReport} from it to get the numbers the Stats page shows.
 */
public final class StatsSnapshot {

    @NonNull
    private final TokenUsageLedger tokens;
    @NonNull
    private final HpHistory hp;
    @NonNull
    private final DoomScrollLog doomScroll;

    /** Takes ownership of the given copies; callers must not mutate them afterwards. */
    public StatsSnapshot(@NonNull TokenUsageLedger tokens, @NonNull HpHistory hp,
                         @NonNull DoomScrollLog doomScroll) {
        this.tokens = tokens;
        this.hp = hp;
        this.doomScroll = doomScroll;
    }

    @NonNull
    public TokenUsageLedger getTokens() {
        return tokens;
    }

    @NonNull
    public HpHistory getHp() {
        return hp;
    }

    @NonNull
    public DoomScrollLog getDoomScroll() {
        return doomScroll;
    }
}
