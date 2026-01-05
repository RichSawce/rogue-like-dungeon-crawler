package org.example.entity;

import org.example.game.Battle;
import org.example.game.util.RNG;

public final class Enemy extends Actor {

    public enum Type { GOBLIN, SKELETON, CULTIST, SLIME, ZOMBIE }

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

        int hp, a1, a2, xp;
        String name;

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
            default -> { // GOBLIN
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

        int dmg;

        switch (type) {

            case SLIME -> {
                int move = rng.nextInt(100);

                if (move < 30) {
                    String m = "Split Splash";
                    trigEnemyAttack(battle);

                    // Slightly less accurate multi-hit
                    if (!rollHit(rng, 78, battle)) return missLine(m);

                    int d1 = Math.max(1, rollDamage(rng) - 1);
                    int d2 = Math.max(1, rollDamage(rng) - 1);
                    player.hp -= (d1 + d2);

                    trigPlayerHit(battle);
                    return name + " uses " + m + "! (" + d1 + "+" + d2 + ")";
                } else if (move < 55) {
                    String m = "Ooze Touch";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 85, battle)) return missLine(m);

                    dmg = Math.max(1, rollDamage(rng) - 1);
                    player.hp -= dmg;

                    int heal = 2 + rng.nextInt(3); // 2-4
                    this.hp = Math.min(this.maxHp, this.hp + heal);

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + " and regenerates +" + heal + ".";
                } else {
                    String m = "Slime Slam";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 88, battle)) return missLine(m);

                    dmg = rollDamage(rng);
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + ".";
                }
            }

            case ZOMBIE -> {
                int move = rng.nextInt(100);

                if (move < 35) {
                    String m = "Rotten Bite";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 82, battle)) return missLine(m);

                    dmg = rollDamage(rng) + 1;
                    player.hp -= dmg;

                    int heal = Math.max(1, dmg / 2);
                    this.hp = Math.min(this.maxHp, this.hp + heal);

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + " and heals +" + heal + "!";
                } else if (move < 50) {
                    String m = "Heavy Lunge";
                    trigEnemyAttack(battle);

                    // Big move, lower accuracy
                    if (!rollHit(rng, 75, battle)) return missLine(m);

                    dmg = rollDamage(rng) + 3;
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + "!";
                } else {
                    String m = "Clubbing Swing";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 86, battle)) return missLine(m);

                    dmg = rollDamage(rng);
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + ".";
                }
            }

            case SKELETON -> {
                int move = rng.nextInt(100);

                if (move < 30 && !battle.foeGuarded) {
                    String m = "Bone Guard";
                    trigEnemyAttack(battle);
                    battle.foeGuarded = true;
                    return name + " uses " + m + "!";
                } else if (move < 55) {
                    String m = "Bone Throw";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 80, battle)) return missLine(m);

                    dmg = rollDamage(rng) + 2;
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + "!";
                } else {
                    String m = "Rattle Slash";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 88, battle)) return missLine(m);

                    dmg = rollDamage(rng);
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + ".";
                }
            }

            case CULTIST -> {
                int move = rng.nextInt(100);

                if (move < 35) {
                    String m = "Hex Bolt";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 80, battle)) return missLine(m);

                    dmg = rollDamage(rng) + 3;
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " casts " + m + " for " + dmg + "!";
                } else if (move < 60) {
                    String m = "Dark Drain";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 83, battle)) return missLine(m);

                    dmg = rollDamage(rng) + 1;
                    player.hp -= dmg;

                    int heal = 2 + rng.nextInt(3); // 2-4
                    this.hp = Math.min(this.maxHp, this.hp + heal);

                    trigPlayerHit(battle);
                    return name + " uses " + m + " (" + dmg + ") and heals +" + heal + "!";
                } else {
                    String m = "Ritual Lash";
                    trigEnemyAttack(battle);

                    if (!rollHit(rng, 87, battle)) return missLine(m);

                    dmg = rollDamage(rng);
                    player.hp -= dmg;

                    trigPlayerHit(battle);
                    return name + " uses " + m + " for " + dmg + ".";
                }
            }

            default -> { // GOBLIN
                String m = "Stab";
                trigEnemyAttack(battle);

                if (!rollHit(rng, 90, battle)) return missLine(m);

                dmg = rollDamage(rng);
                player.hp -= dmg;

                trigPlayerHit(battle);
                return name + " uses " + m + " for " + dmg + ".";
            }
        }
    }
}