package org.example.entity;

import org.example.game.util.RNG;

public final class Enemy extends Actor {
    private Enemy(String name, int x, int y, int maxHp, int atkMin, int atkMax) {
        super(name, x, y, maxHp, atkMin, atkMax);
    }

    public static Enemy basic(int x, int y, int floor) {
        // Slight scaling by floor
        int hp = 8 + Math.min(10, floor * 2);
        int a1 = 2 + Math.min(3, floor / 2);
        int a2 = 4 + Math.min(4, floor / 2);
        String name = (floor < 3) ? "Goblin" : (floor < 6 ? "Skeleton" : "Cultist");
        return new Enemy(name, x, y, hp, a1, a2);
    }

    public int rollDamage(RNG rng) {
        return rng.range(atkMin, atkMax);
    }
}