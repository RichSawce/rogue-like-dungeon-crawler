package org.example.entity;

import org.example.game.util.RNG;
import org.example.item.Inventory;
import org.example.item.ItemType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class Player extends Actor {

    public enum Stat { HP, MP, ATK }

    // -------------------------
    // Spells
    // -------------------------
    public enum SpellType {
        MAGIC_STAB,
        ICE_SHARD,
        FLASH_FREEZE,
        FIRE_SWORD,
        HEAL
    }

    private final EnumSet<SpellType> spellsKnown = EnumSet.noneOf(SpellType.class);

    public List<SpellType> knownSpellsInOrder() {
        ArrayList<SpellType> out = new ArrayList<>();
        // fixed order for UI
        for (SpellType s : SpellType.values()) {
            if (spellsKnown.contains(s)) out.add(s);
        }
        return out;
    }

    public boolean knows(SpellType s) {
        return spellsKnown.contains(s);
    }

    public boolean learnSpell(SpellType s) {
        return spellsKnown.add(s);
    }

    // -------------------------
    // Weapon / scaling ATK boosts
    // Keep base ATK separate from weapon bonuses
    // -------------------------
    public String weaponName = "Rusty Sword";
    private int baseAtkMin;
    private int baseAtkMax;

    private int weaponBonusMin = 0;
    private int weaponBonusMax = 0;

    public void recomputeAtk() {
        this.atkMin = baseAtkMin + weaponBonusMin;
        this.atkMax = baseAtkMax + weaponBonusMax;
    }

    public void equipWeapon(String name, int bonusMin, int bonusMax) {
        this.weaponName = name;
        this.weaponBonusMin = bonusMin;
        this.weaponBonusMax = bonusMax;
        recomputeAtk();
    }

    public String getWeaponName() { return weaponName; }
    public int getWeaponBonusMin() { return weaponBonusMin; }
    public int getWeaponBonusMax() { return weaponBonusMax; }

    // -------------------------
    // Leveling / stats
    // -------------------------
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

        // Base ATK is whatever Actor set
        this.baseAtkMin = this.atkMin;
        this.baseAtkMax = this.atkMax;

        // Default spell: Magic Stab (your old "Spell")
        learnSpell(SpellType.MAGIC_STAB);

        // Default weapon (no bonus)
        equipWeapon("Rusty Sword", 0, 0);
    }

    public int getBaseAtkMin() { return baseAtkMin; }
    public int getBaseAtkMax() { return baseAtkMax; }

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

        expToNext = (int) Math.round(expToNext * 1.35) + 2;

        switch (stat) {
            case HP -> {
                maxHp += 4;
                hp = maxHp;
            }
            case MP -> {
                maxMp += 2;
                mp = maxMp;
            }
            case ATK -> {
                // Increase BASE atk, then recompute with weapon boosts
                baseAtkMin += 1;
                baseAtkMax += 1;
                recomputeAtk();
            }
        }
    }
}