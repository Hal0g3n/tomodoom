package com.example.tomodoom.doomscroll;

/**
 * When a doom-scroll session earns a "Time to look up" warning: at {@link #FIRST_WARNING_HP} HP
 * lost, then each time the session's loss doubles (10, 20, 40, 80 … HP). Pure Java.
 */
final class DoomScrollWarningPolicy {

    /** HP a session must cost before its first warning. */
    static final int FIRST_WARNING_HP = 10;

    private DoomScrollWarningPolicy() {
    }

    /**
     * @param lastWarnedHp the threshold of the session's last warning, or 0 if none yet.
     * @return the next threshold: {@link #FIRST_WARNING_HP}, then double the last one (capped at
     * {@link Integer#MAX_VALUE}).
     */
    static int nextThreshold(int lastWarnedHp) {
        if (lastWarnedHp < FIRST_WARNING_HP) {
            return FIRST_WARNING_HP;
        }
        return lastWarnedHp > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : lastWarnedHp * 2;
    }

    /**
     * @param sessionHpLost HP the session has cost so far.
     * @param lastWarnedHp  the threshold of the session's last warning, or 0 if none yet.
     * @return the highest threshold now reached above {@code lastWarnedHp} (one warning even if
     * several were crossed at once), or 0 if no warning is due.
     */
    static int warningDue(int sessionHpLost, int lastWarnedHp) {
        int due = 0;
        int threshold = nextThreshold(lastWarnedHp);
        if (threshold <= lastWarnedHp) {
            return 0; // already warned at the top threshold
        }
        while (threshold <= sessionHpLost) {
            due = threshold;
            if (threshold == Integer.MAX_VALUE) {
                break;
            }
            threshold = nextThreshold(threshold);
        }
        return due;
    }
}
