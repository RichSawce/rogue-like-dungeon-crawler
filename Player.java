package org.example.entity;

import org.example.game.util.RNG;
import org.example.item.Inventory;
import org.example.item.ItemType;

public final class Player extends Actor {

    public enum Stat { HP, MP, ATK }

    public int level = 1;
    public int exp = 0;
    public int expToNext = 10;

    public final Inventory inv = new Inventory();

    public int healHp(int amount) {
        int before = hp;
        hp = Math.min(maxHp, hp + Math.max(0, amount));
        return hp - before;
    }

    public int healMp(int amount) {
        int before = mp;
        mp = Math.min(maxMp, mp + Math.max(0, amount));
        return mp - before;
    }

    public boolean useItem(ItemType type) {
        return inv.consumeOne(type);
    }

    public Player(int x, int y) {
        super("You", x, y, 22, 10, 3, 6);
    }

    public int rollDamage(RNG rng) {
        return rng.range(atkMin, atkMax);
    }

    public int rollSpellDamage(RNG rng) {
        return rng.range(atkMin + 2, atkMax + 4);
    }

    public void gainExp(int amount) {
        exp += Math.max(0, amount);
    }

    public boolean canLevelUp() {
        return exp >= expToNext;
    }

    public void levelUp(Stat stat) {
        if (!canLevelUp()) return;

        exp -= expToNext;
        level++;

        // scale next requirement a bit
        expToNext = (int) Math.round(expToNext * 1.35) + 2;

        switch (stat) {
            case HP -> {
                maxHp += 4;
                hp = maxHp; // heal on level-up
            }
            case MP -> {
                maxMp += 2;
                mp = maxMp;
            }
            case ATK -> {
                atkMin += 1;
                atkMax += 1;
            }
        }
    }
}