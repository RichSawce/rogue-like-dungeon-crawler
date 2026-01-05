package org.example.game;

import org.example.entity.Player;
import org.example.entity.Enemy;
import org.example.game.util.RNG;

public final class Battle {

    public enum Phase {
        PLAYER_MENU,
        ITEM_MENU,
        SPELL_MENU,
        ENEMY_DELAY,
        ENEMY_ACT,
        ENEMY_MESSAGE, // <-- ADD
        WON,
        LOST
    }

    public boolean foeGuarded = false;
    public final Enemy foe;

    public int menuIndex = 0;
    public int itemIndex = 0;

    public Phase phase = Phase.PLAYER_MENU;

    public String log = "A wild foe appears!";
    public int timerFrames = 0;

    // Spell menu cursor
    public int spellIndex = 0;

    // ----------------------------
    // Status effects (player)
    // ----------------------------
    public boolean fireSwordActive = false; // lasts the whole battle
    public int playerSlowTurns = 0;
    public int playerAccuracyPenaltyPct = 0; // e.g. 30 means -30% accuracy
    public int playerDodgePenaltyPct = 0;    // e.g. 20 means -20% dodge chance

    // ----------------------------
    // Status effects (enemy)
    // ----------------------------
    public int foeFrozenTurns = 0;           // enemy skips turns
    public int foeSlowTurns = 0;             // enemy accuracy penalty duration
    public int foeAccuracyPenaltyPct = 0;    // e.g. 20 means -20% accuracy
    public int foeDodgePenaltyPct = 0;       // e.g. 20 means -20% dodge chance
    /** Consume 1 frozen enemy turn (call when enemy is frozen and would act). */
    public void consumeFoeFreezeTurn() {
        if (foeFrozenTurns > 0) foeFrozenTurns--;
        if (foeFrozenTurns < 0) foeFrozenTurns = 0;
    }
    // ----------------------------
    // Battle sprite “juice” timers
    // ----------------------------
    public int playerAtkFrames = 0;
    public int enemyAtkFrames  = 0;
    public int playerHitFrames = 0;
    public int enemyHitFrames  = 0;

    // Delayed hit scheduling (attack first, hit reaction later)
    public int playerHitDelay = 0;
    public int enemyHitDelay  = 0;
    public int queuedPlayerHitFrames = 0;
    public int queuedEnemyHitFrames  = 0;
    // Delayed DAMAGE scheduling (so HP bars don't change instantly)
    public int foeDmgDelay = 0;
    public int playerDmgDelay = 0;

    public int queuedFoeDamage = 0;
    public int queuedPlayerDamage = 0;

    // Optional: if the queued damage will kill the foe, we can flip to WON when it lands
    public boolean queuedFoeDamageWillKill = false;
    public String queuedFoeDefeatLog = "";

    // Enemy defeat animation timer (for split/fade fling)
    public int enemyDefeatFrames = 0;
    public static final int ENEMY_DEFEAT_FRAMES_MAX = 36;

    // tune delay between attacker motion and victim reaction
    public static final int HIT_LAG_FRAMES = 4;

    public Battle(Enemy foe) {
        this.foe = foe;
    }

    // ----------------------------
    // Status application helpers
    // ----------------------------

    /** Apply slow to the PLAYER. Keeps strongest duration. */
    public void applyPlayerSlow(int turns, int accPenaltyPct, int dodgePenaltyPct) {
        playerSlowTurns = Math.max(playerSlowTurns, turns);
        playerAccuracyPenaltyPct = Math.max(playerAccuracyPenaltyPct, accPenaltyPct);
        playerDodgePenaltyPct = Math.max(playerDodgePenaltyPct, dodgePenaltyPct);
    }

    /** Apply slow to the FOE. Keeps strongest duration. */
    public void applyFoeSlow(int turns, int accPenaltyPct, int dodgePenaltyPct) {
        foeSlowTurns = Math.max(foeSlowTurns, turns);
        foeAccuracyPenaltyPct = Math.max(foeAccuracyPenaltyPct, accPenaltyPct);
        foeDodgePenaltyPct = Math.max(foeDodgePenaltyPct, dodgePenaltyPct);
    }

    /** Apply freeze to the FOE (skips turns). */
    public void applyFoeFreeze(int turns) {
        foeFrozenTurns = Math.max(foeFrozenTurns, turns);
    }

    // ----------------------------
    // Turn ticking
    // Call these ONCE per side's completed turn
    // ----------------------------

    /** Call after the player completes an action (fight/spell/item). */
    public void onPlayerTurnComplete() {
        // enemy statuses tick down on PLAYER completion (i.e., "enemy debuffs wear off as turns pass")
        tickFoeStatuses();
    }

    /** Call after the enemy completes an action (or gets skipped by freeze). */
    public void onEnemyTurnComplete() {
        tickPlayerStatuses();
    }

    private void tickPlayerStatuses() {
        if (playerSlowTurns > 0) {
            playerSlowTurns--;
            if (playerSlowTurns <= 0) {
                playerSlowTurns = 0;
                playerAccuracyPenaltyPct = 0;
                playerDodgePenaltyPct = 0;
            }
        }
    }

    private void tickFoeStatuses() {
        // NOTE: Freeze should tick down on ENEMY turns (when the enemy loses/uses a turn),
        // not here. So we do NOT decrement foeFrozenTurns here.

        if (foeSlowTurns > 0) {
            foeSlowTurns--;
            if (foeSlowTurns <= 0) {
                foeSlowTurns = 0;
                foeAccuracyPenaltyPct = 0;
                foeDodgePenaltyPct = 0;
            }
        }
    }

    /** Convenience: enemy is frozen this turn (should skip acting). */
    public boolean foeIsFrozen() {
        return foeFrozenTurns > 0;
    }

    // ----------------------------
    // Anim helpers
    // ----------------------------
    public void queueEnemyHit(int delayFrames, int hitFrames) {
        enemyHitDelay = Math.max(enemyHitDelay, delayFrames);
        queuedEnemyHitFrames = Math.max(queuedEnemyHitFrames, hitFrames);
    }

    public void queuePlayerHit(int delayFrames, int hitFrames) {
        playerHitDelay = Math.max(playerHitDelay, delayFrames);
        queuedPlayerHitFrames = Math.max(queuedPlayerHitFrames, hitFrames);
    }

    public void startEnemyDefeatAnim() {
        enemyDefeatFrames = ENEMY_DEFEAT_FRAMES_MAX;
    }

    public void tickAnims() {
        if (playerAtkFrames > 0) playerAtkFrames--;
        if (enemyAtkFrames  > 0) enemyAtkFrames--;
        if (playerHitFrames > 0) playerHitFrames--;
        if (enemyHitFrames  > 0) enemyHitFrames--;

        // delayed enemy hit
        if (enemyHitDelay > 0) {
            enemyHitDelay--;
            if (enemyHitDelay == 0 && queuedEnemyHitFrames > 0) {
                enemyHitFrames = Math.max(enemyHitFrames, queuedEnemyHitFrames);
                queuedEnemyHitFrames = 0;
            }
        }

        // delayed player hit
        if (playerHitDelay > 0) {
            playerHitDelay--;
            if (playerHitDelay == 0 && queuedPlayerHitFrames > 0) {
                playerHitFrames = Math.max(playerHitFrames, queuedPlayerHitFrames);
                queuedPlayerHitFrames = 0;
            }
        }

        // defeat animation countdown
        if (enemyDefeatFrames > 0) enemyDefeatFrames--;
    }

    public void queueFoeDamage(int delayFrames, int dmg, boolean willKill, String defeatLog) {
        foeDmgDelay = Math.max(foeDmgDelay, delayFrames);
        queuedFoeDamage = Math.max(queuedFoeDamage, dmg);
        queuedFoeDamageWillKill = queuedFoeDamageWillKill || willKill;
        if (defeatLog != null && !defeatLog.isBlank()) queuedFoeDefeatLog = defeatLog;
    }

    public void queuePlayerDamage(int delayFrames, int dmg) {
        playerDmgDelay = Math.max(playerDmgDelay, delayFrames);
        queuedPlayerDamage = Math.max(queuedPlayerDamage, dmg);
    }

    /**
     * Call once per frame while in battle (after tickAnims()).
     * Applies queued damage when its delay reaches 0.
     */
    public void tickDamage(Player player) {
        // --- foe damage ---
        if (foeDmgDelay > 0) {
            foeDmgDelay--;
            if (foeDmgDelay == 0 && queuedFoeDamage > 0) {
                foe.hp -= queuedFoeDamage;
                queuedFoeDamage = 0;

                if (foe.hp <= 0 || queuedFoeDamageWillKill) {
                    foe.hp = 0;
                    queuedFoeDamageWillKill = false;

                    // Start the defeat animation exactly when damage "lands"
                    startEnemyDefeatAnim();

                    if (queuedFoeDefeatLog != null && !queuedFoeDefeatLog.isBlank()) {
                        log = queuedFoeDefeatLog;
                    }

                    phase = Phase.WON;
                } else {
                    queuedFoeDamageWillKill = false;
                }
            }
        }

        // --- player damage ---
        if (playerDmgDelay > 0) {
            playerDmgDelay--;
            if (playerDmgDelay == 0 && queuedPlayerDamage > 0) {
                player.hp -= queuedPlayerDamage;
                queuedPlayerDamage = 0;

                if (player.hp < 0) player.hp = 0;
                if (player.hp <= 0) {
                    phase = Phase.LOST;
                }
            }
        }
    }

    // ----------------------------
    // Math helpers
    // ----------------------------
    public static int clampPct(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // Defender dodge roll (higher = more likely to avoid being hit)
    public static boolean rollDodge(RNG rng, int baseDodgePct, int dodgePenaltyPct) {
        int finalDodge = clampPct(baseDodgePct - dodgePenaltyPct, 0, 60);
        return rng.nextInt(100) < finalDodge;
    }

    // Attacker hit roll (accuracy check)
    public static boolean rollHit(RNG rng, int baseAccuracyPct, int accuracyPenaltyPct) {
        int finalAcc = clampPct(baseAccuracyPct - accuracyPenaltyPct, 5, 95);
        return rng.nextInt(100) < finalAcc;
    }
}