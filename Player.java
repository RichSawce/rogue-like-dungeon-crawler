package org.example.entity;

import org.example.game.util.RNG;

public final class Player extends Actor {
    public Player(int x, int y) {
        super("You", x, y, 22, 3, 6);
    }

    public int rollDamage(RNG rng) {
        return rng.range(atkMin, atkMax);
    }
}