package org.example.entity;

import org.example.game.Battle;
import org.example.game.util.RNG;

public final class Enemy extends Actor {

    public enum Type { GOBLIN, SKELETON, CULTIST, SLIME, ZOMBIE }
    private static final int ENEMY_BASE_ACC = 85; // generic baseline for enemy moves
    private final int xpValue;
    public final Type type;

    private Enemy(Type type, String name, int x, int y, int maxHp, int atkMin, int atkMax, int xpValue,
                  int speed, int intelligence, int will) {
        super(name, x, y, maxHp, 0, atkMin, atkMax, speed, intelligence, will);
        this.type = type;
        this.xpValue = xpValue;
    }

    // ----------------------------
    // SPAWNING (weighted by floor)
    // ----------------------------
    public static Enemy spawnForFloor(int x, int y, int floor, RNG rng) {

        // Weights increase as floor rises
        // (You can tune these numbers freely.)
        int wGoblin  = Math.max(10, 40 - floor * 3);          // fades out slowly
        int wSlime   = Math.min(35,  5 + floor * 2);          // ramps early
        int wZombie  = (floor >= 3) ? Math.min(30, floor * 2) : 0;
        int wSkeleton= (floor >= 4) ? Math.min(35, 5 + floor * 2) : 0;
        int wCultist = (floor >= 6) ? Math.min(40, (floor - 5) * 4) : 0;

        int total = wGoblin + wSlime + wZombie + wSkeleton + wCultist;
        int r = rng.nextInt(total);

        Type t;
        if ((r -= wGoblin) < 0) t = Type.GOBLIN;
        else if ((r -= wSlime) < 0) t = Type.SLIME;
        else if ((r -= wZombie) < 0) t = Type.ZOMBIE;
        else if ((r -= wSkeleton) < 0) t = Type.SKELETON;
        else t = Type.CULTIST;

        return build(t, x, y, floor, rng);
    }

    // ----------------------------
    // STAT BUILD PER TYPE
    // ----------------------------
    private static Enemy build(Type t, int x, int y, int floor, RNG rng) {

        int tier = Math.max(1, (floor + 1) / 2); // floors 1-2 => 1, 3-4 => 2, ...
        int baseHp = 7 + Math.min(14, tier * 2);
        int baseA1 = 2 + Math.min(4, floor / 2);
        int baseA2 = 4 + Math.min(5, floor / 2);

        // defaults (will be overwritten)
        int hp = baseHp;
        int a1 = baseA1;
        int a2 = baseA2;
        int xp = 3 + tier; // baseline XP; tune freely
        String name;

        int spd = 8, intel = 6, wil = 6;

        int floorBoost = Math.min(6, floor / 2); // 0..6

        switch (t) {
            case SLIME -> {
                name = "Slime";
                hp = baseHp + 2 + tier;          // tanky-ish
                a1 = Math.max(1, baseA1 - 1);
                a2 = Math.max(a1, baseA2 - 1);
                xp = 3 + tier;

                spd = 6 + floorBoost;
                intel = 4 + floorBoost / 2;
                wil = 10 + floorBoost;
            }
            case ZOMBIE -> {
                name = "Zombie";
                hp = baseHp + 4 + tier;
                a1 = baseA1;
                a2 = baseA2;
                xp = 4 + tier;

                spd = 5 + floorBoost;
                intel = 4 + floorBoost / 2;
                wil = 12 + floorBoost;
            }
            case SKELETON -> {
                name = "Skeleton";
                hp = baseHp;
                a1 = baseA1 + 1;
                a2 = baseA2 + 1;
                xp = 5 + tier;

                spd = 9 + floorBoost;
                intel = 6 + floorBoost / 2;
                wil = 8 + floorBoost / 2;
            }
            case CULTIST -> {
                name = "Cultist";
                hp = baseHp - 1;
                a1 = baseA1;
                a2 = baseA2;
                xp = 6 + tier;

                spd = 8 + floorBoost;
                intel = 12 + floorBoost;
                wil = 9 + floorBoost / 2;
            }
            case GOBLIN -> {
                name = "Goblin";
                hp = baseHp;
                a1 = baseA1;
                a2 = baseA2;
                xp = 4 + tier;

                spd = 10 + floorBoost;
                intel = 6 + floorBoost / 2;
                wil = 7 + floorBoost / 2;
            }
            default -> {
                name = "Foe";
            }
        }

        // final sanity (prevents weird negatives if you tune offsets later)
        hp = Math.max(1, hp);
        a1 = Math.max(1, a1);
        a2 = Math.max(a1, a2);
        xp = Math.max(1, xp);

        return new Enemy(t, name, x, y, hp, a1, a2, xp, spd, intel, wil);
    }

    public int goldDrop() {
        // 2-5 gold based on type
        return switch (type) {
            case GOBLIN -> 2 + (int)(Math.random() * 4); // 2-5
            case SKELETON -> 3 + (int)(Math.random() * 3); // 3-5
            case SLIME -> 2 + (int)(Math.random() * 3); // 2-4
            case ZOMBIE -> 3 + (int)(Math.random() * 3); // 3-5
            case CULTIST -> 4 + (int)(Math.random() * 2); // 4-5
        };
    }

    public int rollDamage(RNG rng) {
        return rng.range(atkMin, atkMax);
    }

    private boolean rollHit(RNG rng, int accuracyPct, Battle battle) {
        int pen = (battle == null) ? 0 : battle.foeAccuracyPenaltyPct;
        int finalAcc = Math.max(5, Math.min(95, accuracyPct - pen));
        return rng.nextInt(100) < finalAcc;
    }

    private String missLine(String moveName) {
        return name + " uses " + moveName + "... MISS!";
    }

    private void trigEnemyAttack(Battle battle) {
        battle.enemyAtkFrames = 10;
    }

    private void trigPlayerHit(Battle battle) {
        battle.queuePlayerHit(Battle.HIT_LAG_FRAMES, 8);
    }

    public int xpValue() {
        return xpValue;
    }

    // ----------------------------
    // BATTLE MOVESET
    // returns log string; applies damage/heal/flags itself
    // ----------------------------
    public String performBattleMove(RNG rng, Player player, Battle battle) {

        // ✅ NEW: Check if player is parrying
        if (battle.parryActive) {
            battle.parryActive = false;
            return "The " + name + " attacks... but you parry it perfectly!";
        }
        // Decide move per type
        String moveName;
        int acc;
        boolean appliesSlow = false;
        int slowTurns = 0;

        int dmg = rollDamage(rng);

        switch (type) {
            case GOBLIN -> {
                // Mostly reliable
                moveName = rng.nextInt(100) < 70 ? "Slash" : "Lunge";
                acc = (moveName.equals("Slash")) ? 88 : 80;
            }
            case SLIME -> {
                moveName = rng.nextInt(100) < 60 ? "Slam" : "Splash";
                acc = (moveName.equals("Slam")) ? 82 : 90;
                dmg = Math.max(1, dmg - 1);
            }
            case ZOMBIE -> {
                moveName = "Claw";
                acc = 84;
            }
            case SKELETON -> {
                moveName = rng.nextInt(100) < 50 ? "Thrust" : "Skewer";
                acc = (moveName.equals("Thrust")) ? 86 : 76;
                dmg += (moveName.equals("Skewer")) ? 2 : 0;
            }
            case CULTIST -> {
                if (rng.nextInt(100) < 35) {
                    moveName = "Ice Curse";
                    acc = 78;
                    appliesSlow = true;
                    slowTurns = 3;
                    dmg = 0;
                } else {
                    moveName = "Hex Bolt";
                    acc = 82;
                    dmg = Math.max(1, dmg - 1);
                }
            }
            default -> { // fallback (shouldn't happen)
                moveName = "Slash";
                acc = 85;
            }
        }

        // Attacker animation
        trigEnemyAttack(battle);

        // 1) Player dodge check
        int dodgePen = Battle.applyWillVsSlowPenalty(rng, battle.playerDodgePenaltyPct, player.will(), battle.playerSlowTurns);

        if (Battle.rollDodge(
                rng,
                12,
                this.speed(),      // attacker speed
                player.speed(),    // defender speed
                dodgePen
        )) {
            return name + " uses " + moveName + "... but you DODGE!";
        }

        // 2) Miss check (enemy moves can miss)
        int accPen = Battle.applyWillVsSlowPenalty(rng, battle.foeAccuracyPenaltyPct, this.will(), battle.foeSlowTurns);

        if (!Battle.rollPhysicalHit(
                rng,
                acc,
                this.speed(),
                player.speed(),
                accPen
        )) {
            return missLine(moveName);
        }

        // 3) Apply effect / damage
        if (appliesSlow) {
            boolean resisted = Battle.rollStatusResist(rng, this.intelligence(), player.will());

            if (!resisted) {
                int dur = slowTurns + (this.intelligence() / 10); // tiny scaling
                battle.applyPlayerSlow(dur, 30, 20);
                return name + " casts " + moveName + "! You are SLOWED!";
            } else {
                return name + " casts " + moveName + "... but you resist the curse!";
            }
        }

        // Hit reaction
        trigPlayerHit(battle);

        player.hp = Math.max(0, player.hp - dmg);
        return name + " uses " + moveName + " and hits for " + dmg + "!";
    }
}