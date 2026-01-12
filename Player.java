package org.example.entity;

import org.example.game.util.RNG;
import org.example.item.Inventory;
import org.example.item.ItemType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class Player extends Actor {

    public enum Stat { HP, MP, ATK, SPEED, WILL, INT }
    public int gold = 100; // Starting gold

    public boolean canAfford(int price) {
        return gold >= price;
    }

    public boolean spendGold(int amount) {
        if (gold >= amount) {
            gold -= amount;
            return true;
        }
        return false;
    }

    public void earnGold(int amount) {
        gold += amount;
    }
    // -------------------------
    // Spells
    // -------------------------
    public enum SpellType {
        MAGIC_STAB,
        SLOW_POKE,
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
// Physical Moves
// -------------------------
    public enum PhysicalMove {
        SLASH,      // 90% acc, 100% dmg
        SMASH,      // 75% acc, 140% dmg, speed bonus
        LUNGE,      // 85% acc, 110% dmg, crit on hit OR lose turn on miss
        PARRY,      // 100% acc, reduces next enemy attack to 0
        SWEEP       // 100% acc (can't be dodged), 80% dmg
    }

    private final EnumSet<PhysicalMove> movesKnown = EnumSet.noneOf(PhysicalMove.class);

    public List<PhysicalMove> knownMovesInOrder() {
        ArrayList<PhysicalMove> out = new ArrayList<>();
        for (PhysicalMove m : PhysicalMove.values()) {
            if (movesKnown.contains(m)) out.add(m);
        }
        return out;
    }

    public boolean knowsMove(PhysicalMove m) {
        return movesKnown.contains(m);
    }

    public boolean learnMove(PhysicalMove m) {
        return movesKnown.add(m);
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

    // ✅ NEW: total ATK getters (useful for UI + damage logic consistency)
    public int getBaseAtkMin() { return baseAtkMin; }
    public int getBaseAtkMax() { return baseAtkMax; }

    public int getAtkMinTotal() { return baseAtkMin + weaponBonusMin; }
    public int getAtkMaxTotal() { return baseAtkMax + weaponBonusMax; }

    // ✅ NEW: the ONE true base sword/weapon roll
    // Spells that "share sword base damage" should call this.
    public int rollBaseWeaponDamage(RNG rng) {
        int min = getAtkMinTotal();
        int max = getAtkMaxTotal();
        if (max < min) max = min;
        return rng.range(min, max);
    }

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
        super("You", x, y, 22, 10, 3, 6,
                5, // speed
                5, // intelligence
                5  // will
        );

        this.baseAtkMin = this.atkMin;
        this.baseAtkMax = this.atkMax;

        learnSpell(SpellType.MAGIC_STAB);
        learnMove(PhysicalMove.SLASH);  // ✅ NEW: Start with basic move
        equipWeapon("Rusty Sword", 0, 0);
    }

    public int keyCount() {
        return inv.count(ItemType.KEY);
    }

    public void addKey(int n) {
        if (n <= 0) return;
        inv.add(ItemType.KEY, n);
    }

    public boolean useKey() {
        return inv.consumeOne(ItemType.KEY);
    }

    // ✅ UPDATED: normal damage now funnels through the base weapon roll
    public int rollDamage(RNG rng) {
        return rollBaseWeaponDamage(rng);
    }

    // ✅ UPDATED: keep this method if you still want "spell roll" to be a bit higher.
    // But if you want spells to share EXACT sword base damage, then have spells call
    // rollBaseWeaponDamage(rng) directly (recommended).
    public int rollSpellDamage(RNG rng) {
        // Your previous behavior was atkMin+2 .. atkMax+4
        // We'll preserve that here, but based on totals.
        int min = getAtkMinTotal() + 2;
        int max = getAtkMaxTotal() + 4;
        if (max < min) max = min;
        return rng.range(min, max);
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
                baseAtkMin += 1;
                baseAtkMax += 1;
                recomputeAtk();
            }
            case SPEED -> {
                // Actor already has speed (you pass it into super(...))
                // Assuming Actor exposes it as a field or setter. If it's private, see note below.
                this.speed += 1;
            }
            case INT -> {
                this.intelligence += 1;
            }
            case WILL -> {
                this.will += 1;
            }
        }
    }
}