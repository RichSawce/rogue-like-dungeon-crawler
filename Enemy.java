package org.example.entity;

import org.example.game.Battle;
import org.example.game.util.RNG;

public final class Enemy extends Actor {

    public enum Type { GOBLIN, SKELETON, CULTIST, SLIME, ZOMBIE }
    private static final int ENEMY_BASE_ACC = 85; // generic baseline for enemy moves
    private final int xpValue;
    public final Type type;

    private Enemy(Type type, String name, int x, int y, int maxHp, int atkMin, int atkMax, int xpValue) {
        super(name, x, y, maxHp, atkMin, atkMax);
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

        // A simple scaling baseline; each type offsets it
        int baseHp  = 7 + Math.min(14, floor * 2);
        int baseA1  = 2 + Math.min(4, floor / 2);
        int baseA2  = 4 + Math.min(5, floor / 2);

        int hp = 0, a1 = 0, a2 = 0, xp = 0;
        String name = "";

        switch (t) {
            case SLIME -> {
                name = "Slime";
                hp = baseHp + 2;          // tanky
                a1 = Math.max(1, baseA1 - 1);
                a2 = Math.max(a1 + 1, baseA2 - 1);
                xp = 3 + floor * 2;
            }
            case ZOMBIE -> {
                name = "Zombie";
                hp = baseHp + 5;          // very tanky
                a1 = baseA1;
                a2 = baseA2 + 1;          // heavier hits
                xp = 5 + floor * 2;
            }
            case SKELETON -> {
                name = "Skeleton";
                hp = baseHp + 1;
                a1 = baseA1;
                a2 = baseA2;
                xp = 6 + floor * 2;
            }
            case CULTIST -> {
                name = "Cultist";
                hp = Math.max(6, baseHp - 1);  // a bit squishier
                a1 = baseA1 + 1;
                a2 = baseA2 + 2;               // spikier
                xp = 8 + floor * 2;
            }
            case GOBLIN -> {
                name = "Goblin";
                hp = baseHp;
                a1 = baseA1;
                a2 = baseA2;
                xp = 4 + floor * 2;
            }
        }
        

        return new Enemy(t, name, x, y, hp, a1, a2, xp);
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
        if (Battle.rollDodge(rng, 12, battle.playerDodgePenaltyPct)) { // uses same base you set in Game, tune if desired
            return name + " uses " + moveName + "... but you DODGE!";
        }

        // 2) Miss check (enemy moves can miss)
        if (!Battle.rollHit(rng, acc, battle.foeAccuracyPenaltyPct)) {
            return missLine(moveName);
        }

        // 3) Apply effect / damage
        if (appliesSlow) {
            battle.applyPlayerSlow(slowTurns, 30, 20);

            return name + " casts " + moveName + "! You are SLOWED!";
        }

        // Hit reaction
        trigPlayerHit(battle);

        player.hp = Math.max(0, player.hp - dmg);
        return name + " uses " + moveName + " and hits for " + dmg + "!";
    }
}