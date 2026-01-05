package org.example.game;

import org.example.entity.Enemy;


public final class Battle {
    public enum Phase {
        PLAYER_MENU,
        ITEM_MENU,
        SPELL_MENU,
        ENEMY_DELAY,
        ENEMY_ACT,
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

    // Status effects
    public int foeFrozenTurns = 0;          // enemy skips turns
    public int foeSlowTurns = 0;            // enemy accuracy penalty duration
    public int foeAccuracyPenaltyPct = 0;   // e.g. 20 means -20% accuracy

    // Fire Sword buff (player)
    public boolean fireSwordActive = false; // lasts the whole battle

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

    // Enemy defeat animation timer (for split/fade fling)
    public int enemyDefeatFrames = 0;
    public static final int ENEMY_DEFEAT_FRAMES_MAX = 36;

    // tune delay between attacker motion and victim reaction
    public static final int HIT_LAG_FRAMES = 4;

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

    public Battle(Enemy foe) {
        this.foe = foe;
    }
}