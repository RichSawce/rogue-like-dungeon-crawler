package org.example.game.util;

import java.util.Random;

public final class RNG {
    private final Random r;

    public RNG(long seed) {
        this.r = new Random(seed);
    }

    public int nextInt(int boundExclusive) {
        return r.nextInt(boundExclusive);
    }

    public int range(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("bad range");
        int span = (maxInclusive - minInclusive) + 1;
        return minInclusive + r.nextInt(span);
    }

    public boolean chance(double p) {
        return r.nextDouble() < p;
    }

    public long seedPeek() {
        // Not the real seed, but useful to show something stable-ish per run if needed.
        return r.nextLong();
    }
}