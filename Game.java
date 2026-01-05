package org.example.game;

import org.example.entity.Enemy;
import org.example.entity.Player;
import org.example.game.util.RNG;
import org.example.world.Dungeon;
import org.example.world.DungeonGenerator;
import org.example.world.Fov;
import org.example.entity.Chest;
import org.example.item.ItemType;

import java.util.ArrayList;
import java.util.List;

public final class Game {
    public enum State { MAIN_MENU, DUNGEON, BATTLE, LEVEL_UP, INVENTORY, GAME_OVER }

    private final TurnSystem turn = new TurnSystem();
    private final Fov fov = new Fov();

    private final List<Chest> chests = new ArrayList<>();

    public List<Chest> chests() { return chests; }
    // Inventory UI
    private int invPage = 0;           // 0 = Items, 1 = Spells
    private int invIndex = 0;
    public int invIndex() { return invIndex; }
    private int invScroll = 0;         // scroll offset for items

    private int spellIndex = 0;        // selection index for spells
    private int spellScroll = 0;       // scroll offset for spells

    // How many rows fit in the list area (renderer will draw exactly this many)
    private static final int INV_VISIBLE_ROWS = 7;

    private RNG rng;
    private long seed;
    private int floor;

    private Dungeon dungeon;
    private Player player;
    private final List<Enemy> enemies = new ArrayList<>();

    private String lastLog = "Find the stairs (>) and press Enter.";

    // UI log timing
    private int logFramesLeft = 0;      // >0 = countdown, 0 = hidden, <0 = sticky
    private static final int UI_FPS = 60; // adjust if your game loop uses a different FPS

    // -----------------------------
    // Screen fade (Game Boy-ish stepped fade)
    // -----------------------------
    private float fadeAlpha = 0f;     // 0..1 (renderer uses this)
    private int fadePhase = 0;        // 0=none, 1=fade out, 2=fade in

    private int fadeStep = 0;         // 0..FADE_STEPS (0=none, FADE_STEPS=full)
    private int fadeHoldLeft = 0;     // pauses at each step for chunky fade

    private boolean deathWipeActive = false;
    private float deathWipeProgress = 0f; // 0..1
    private int deathWipeFrames = 0;

    private static final int DEATH_WIPE_FRAMES_MAX = 78; // tune: 36..60

    private boolean battleExitPending = false;
    private boolean battleExitVictory = false;

    // New-game transition (from main menu / game over)
    private boolean newGamePending = false;
    private long newGameSeed = 0L;

    // Battle-start transition
    private boolean battleStartPending = false;
    private Enemy battleStartFoe = null;

    // Fade tuning knobs
    private static final int FADE_STEPS = 4;          // 4 steps -> GB3/GB2/GB1/GB0 feel
    private static final int FADE_STEP_HOLD = 4;      // pause frames per step (increase for choppier)
    private static final int BATTLE_WIN_HOLD_FRAMES = 36; // small pause after defeat anim

    // Gate fade progression so it can't advance multiple steps before a repaint
    private boolean fadeAwaitingPresent = false;

    private State state = State.DUNGEON;

    private Battle battle;

    private String menuHint = "Press ENTER to Start";

    public java.util.List<ItemType> getUsableBattleItems() {
        java.util.ArrayList<ItemType> out = new java.util.ArrayList<>();
        for (ItemType t : player.inv.nonEmptyTypes()) {
            if (t == ItemType.HP_POTION || t == ItemType.MP_POTION) {
                out.add(t);
            }
        }
        return out;
    }

    public String itemLabel(ItemType t) {
        int c = player.inv.count(t);
        return switch (t) {
            case HP_POTION -> "HP Potion  x" + c + "  (+8 HP)";
            case MP_POTION -> "MP Potion  x" + c + "  (+6 MP)";

            // swords (small boosts that stack nicely with level ATK ups)
            case SWORD_WORN   -> "Worn Sword  x" + c + "  (Equip: +0/+1 ATK)";
            case SWORD_BRONZE -> "Bronze Sword x" + c + " (Equip: +1/+1 ATK)";
            case SWORD_IRON   -> "Iron Sword   x" + c + " (Equip: +1/+2 ATK)";
            case SWORD_STEEL  -> "Steel Sword  x" + c + " (Equip: +2/+2 ATK)";
            case SWORD_KNIGHT -> "Knight Sword x" + c + " (Equip: +2/+3 ATK)";

            // spell tomes
            case TOME_ICE_SHARD     -> "Tome: Ice Shard x" + c + " (Learn spell)";
            case TOME_FLASH_FREEZE  -> "Tome: Flash Freeze x" + c + " (Learn spell)";
            case TOME_FIRE_SWORD    -> "Tome: Fire Sword x" + c + " (Learn spell)";
            case TOME_HEAL          -> "Tome: Heal x" + c + " (Learn spell)";
        };
    }

    public void newRun(long seed) {
        this.seed = seed;
        this.rng = new RNG(seed);
        this.floor = 1;
        this.state = State.DUNGEON;
        this.battle = null;
        this.lastLog = "New run. Good luck.";
        this.player = null;
        generateFloor();
    }

    public void goToMainMenu() {
        state = State.MAIN_MENU;
        battle = null;
        dungeon = null;
        enemies.clear();
        lastLog = "";

        fadePhase = 2;
        fadeStep = FADE_STEPS;
        fadeAlpha = 1f;
        fadeHoldLeft = 0;

        fadeAwaitingPresent = true; // add this
    }

    private void generateFloor() {
        enemies.clear();

        DungeonGenerator gen = new DungeonGenerator(rng);
        this.dungeon = gen.generate(); // uses GameConfig.DUNGEON_W/H

        // Place player at start
        int[] start = dungeon.getStart();
        if (player == null) {
            player = new Player(start[0], start[1]);
        } else {
            player.x = start[0];
            player.y = start[1];
        }

        // Place enemies
        int n = rng.range(GameConfig.START_ENEMIES_MIN, GameConfig.START_ENEMIES_MAX) + (floor - 1) / 2;
        for (int i = 0; i < n; i++) {
            int[] p = dungeon.findRandomFloor(rng);
            // avoid spawning on player
            if (p[0] == player.x && p[1] == player.y) { i--; continue; }
            enemies.add(Enemy.spawnForFloor(p[0], p[1], floor, rng));
        }

        // Place chests (a few per floor)
        chests.clear();
        int chestCount = 1 + rng.nextInt(2); // 1-2 per floor (tune this)
        for (int i = 0; i < chestCount; i++) {
            int[] p = dungeon.findRandomFloor(rng);

            // avoid player, enemies, and other chests
            if ((p[0] == player.x && p[1] == player.y) || getEnemyAt(p[0], p[1]) != null || getChestAt(p[0], p[1]) != null) {
                i--;
                continue;
            }

            int r = rng.nextInt(100);

            ItemType loot;
            if (r < 40) loot = ItemType.HP_POTION;
            else if (r < 70) loot = ItemType.MP_POTION;
            else if (r < 78) loot = ItemType.SWORD_WORN;
            else if (r < 84) loot = ItemType.SWORD_BRONZE;
            else if (r < 89) loot = ItemType.TOME_ICE_SHARD;
            else if (r < 93) loot = ItemType.TOME_FIRE_SWORD;
            else if (r < 97) loot = ItemType.TOME_HEAL;
            else loot = ItemType.TOME_FLASH_FREEZE;
            chests.add(new Chest(p[0], p[1], loot));
        }

        // Update FOV initially
        recomputeFov();
        turn.reset();

        setLog("Floor " + floor + ". Move with arrows/WASD. Bump to attack.", 2.5);
        state = State.DUNGEON;
        battle = null;
    }

    public void update(Input input) {
        tickLogTimer();
        tickScreenFade();
        tickDeathWipe();

        if (deathWipeActive) return; // freeze game during death transition

        // --- Global restart (R) with fade (works on ALL screens, including Main Menu) ---
        if (!isFading() && input.wasTapped(java.awt.event.KeyEvent.VK_R)) {
            startNewGameFade(System.currentTimeMillis());
            return;
        }

        // If fading out/in between dungeon/battle, freeze gameplay inputs + turns
        if (isFading()) return;
        if (state == State.MAIN_MENU) {
            updateMainMenu(input);
            return;
        }
        if (state == State.INVENTORY) {
            updateInventory(input);
            return;
        }

        if (state == State.BATTLE) {
            updateBattle(input);
            return;
        }

        if (state == State.LEVEL_UP) {
            updateLevelUp(input);
            return;
        }

        if (state == State.GAME_OVER) {
            updateGameOver(input);
            return;
        }

        // --- Dungeon mode below ---
        if (!turn.isPlayerTurn()) {
            enemyTurn();
            turn.endEnemyTurn();
            recomputeFov();
            return;
        }

        // Player turn
        int dx = 0, dy = 0;

        // Movement taps
        if (input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A)) dx = -1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D)) dx = 1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) dy = -1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) dy = 1;

        if (dx != 0 || dy != 0) {
            boolean acted = tryPlayerMoveOrAttack(dx, dy);
            if (acted) {
                turn.endPlayerTurn();
                recomputeFov();
            }
            return;
        }

        // Wait
        if (input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
            setLog("You wait.", 1.5);
            turn.endPlayerTurn();
            recomputeFov();
            return;
        }

        // Open inventory
        if (input.wasTapped(java.awt.event.KeyEvent.VK_I)) {
            state = State.INVENTORY;
            lastLog = "Inventory: ←/→ tabs   ↑/↓ select   ENTER use (items)   ESC close";
            invPage = 0;
            invIndex = 0;
            invScroll = 0;
            spellIndex = 0;
            spellScroll = 0;
            return;
        }

        // Stairs
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER)) {
            if (dungeon.isStairsDown(player.x, player.y)) {
                floor++;

                if (player.canLevelUp()) {
                    state = State.LEVEL_UP;
                    lastLog = "LEVEL UP! Choose: 1=HP  2=MP  3=ATK";
                    return;
                }

                setLog("You descend...", 2.5);
                generateFloor();
            } else {
                setLog("No stairs here.", 2.5);
            }
        }
    }

    private boolean tryPlayerMoveOrAttack(int dx, int dy) {
        int nx = player.x + dx;
        int ny = player.y + dy;

        // Attack if enemy occupies target
        // If enemy occupies target: start battle instead of instant hit
        Enemy e = getEnemyAt(nx, ny);
        if (e != null) {
            startBattle(e);
            return true;
        }

        // Otherwise move if passable
        if (dungeon.isWalkable(nx, ny) && getEnemyAt(nx, ny) == null) {
            player.x = nx;
            player.y = ny;

            Chest chest = getChestAt(player.x, player.y);
            if (chest != null) {
                chest.opened = true;
                player.inv.add(chest.loot, 1);
                setLog("You found a " + itemName(chest.loot) + "!", 2.5);
            }

            return true;
        }

        setLog("Bumped into wall.", 2.5);
        return true;
    }

    private void enemyTurn() {
        // Simple: each enemy moves 1 step toward player if in “awareness” range, else random wander.
        for (Enemy e : new ArrayList<>(enemies)) {
            if (e.hp <= 0) continue;

            int dist = Math.abs(player.x - e.x) + Math.abs(player.y - e.y);

            // Attack if adjacent
            if (dist == 1) {
                int dmg = e.rollDamage(rng);
                player.hp -= dmg;
                setLog( e.name + " hits you for " + dmg + ".", 2.5);
                if (player.hp <= 0) {
                    player.hp = 0;
                    startDeathWipeToGameOver();
                }
                continue;
            }

            int dx = 0, dy = 0;

            if (dist <= 8) {
                // chase (Manhattan step)
                if (player.x < e.x) dx = -1;
                else if (player.x > e.x) dx = 1;
                else if (player.y < e.y) dy = -1;
                else if (player.y > e.y) dy = 1;
            } else {
                // wander
                int r = rng.nextInt(5);
                if (r == 0) dx = -1;
                else if (r == 1) dx = 1;
                else if (r == 2) dy = -1;
                else if (r == 3) dy = 1;
                // r == 4: stand still
            }

            int nx = e.x + dx;
            int ny = e.y + dy;

            // Don’t step onto player/enemy; only walkable tiles.
            if (dungeon.isWalkable(nx, ny) && (nx != player.x || ny != player.y) && getEnemyAt(nx, ny) == null) {
                e.x = nx;
                e.y = ny;
            }
        }
    }

    private void recomputeFov() {
        fov.compute(dungeon, player.x, player.y, GameConfig.PLAYER_FOV_RADIUS);
        dungeon.applyVisibilityFromFov(fov);
    }

    private Enemy getEnemyAt(int x, int y) {
        for (Enemy e : enemies) {
            if (e.x == x && e.y == y && e.hp > 0) return e;
        }
        return null;
    }

    private void startBattle(Enemy e) {
        // Start a chunky fade-out, then enter battle at full dark, then fade in.
        startBattleEnterFade(e);
    }

    private void endBattleVictory() {
        // Remove the defeated enemy from the dungeon
        enemies.remove(battle.foe);

        int gained = battle.foe.xpValue();
        player.gainExp(gained);

        setLog("You gained " + gained + " EXP.", 2.5);
        battle = null;
        state = State.DUNGEON;

        recomputeFov();
        turn.reset();
    }

    private void endBattleRun() {
        setLog("You fled!", 2.5);
        battle = null;
        state = State.DUNGEON;

        recomputeFov();
        turn.reset();
    }

    private void updateInventory(Input input) {

        // close
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) || input.wasTapped(java.awt.event.KeyEvent.VK_I)) {
            state = State.DUNGEON;
            return;
        }

        // switch tabs (left/right)
        if (input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A)) {
            invPage = (invPage + 2 - 1) % 2; // 0<->1
        } else if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D)) {
            invPage = (invPage + 1) % 2;
        }

        // PAGE 0: ITEMS
        if (invPage == 0) {

            java.util.List<ItemType> items = player.inv.nonEmptyTypes();
            int n = items.size();

            if (n == 0) {
                // still allow tab switch / exit
                invIndex = 0;
                invScroll = 0;
                return;
            }

            // clamp selection
            if (invIndex < 0) invIndex = 0;
            if (invIndex >= n) invIndex = n - 1;

            // nav up/down
            int dir = 0;
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) dir = +1;

            if (dir != 0) {
                invIndex += dir;
                if (invIndex < 0) invIndex = 0;
                if (invIndex >= n) invIndex = n - 1;
            }

            // keep selection visible in the scroll window
            if (invIndex < invScroll) invScroll = invIndex;
            if (invIndex >= invScroll + INV_VISIBLE_ROWS) invScroll = invIndex - INV_VISIBLE_ROWS + 1;
            if (invScroll < 0) invScroll = 0;
            if (invScroll > Math.max(0, n - INV_VISIBLE_ROWS)) invScroll = Math.max(0, n - INV_VISIBLE_ROWS);

            // use / equip
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                ItemType chosen = items.get(invIndex);

                switch (chosen) {
                    // weapons: equip (do NOT consume)
                    case SWORD_WORN -> {
                        player.equipWeapon("Worn Sword", 0, 1);
                        setLog("Equipped Worn Sword. (+0/+1 ATK)", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case SWORD_BRONZE -> {
                        player.equipWeapon("Bronze Sword", 1, 1);
                        setLog("Equipped Bronze Sword. (+1/+1 ATK)", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case SWORD_IRON -> {
                        player.equipWeapon("Iron Sword", 1, 2);
                        setLog("Equipped Iron Sword. (+1/+2 ATK)", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case SWORD_STEEL -> {
                        player.equipWeapon("Steel Sword", 2, 2);
                        setLog("Equipped Steel Sword. (+2/+2 ATK)", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case SWORD_KNIGHT -> {
                        player.equipWeapon("Knight Sword", 2, 3);
                        setLog("Equipped Knight Sword. (+2/+3 ATK)", 2.5);
                        state = State.DUNGEON;
                        return;
                    }

                    // tomes: consume to learn
                    case TOME_ICE_SHARD -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        player.learnSpell(Player.SpellType.ICE_SHARD);
                        setLog("Learned spell: Ice Shard!", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case TOME_FLASH_FREEZE -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        player.learnSpell(Player.SpellType.FLASH_FREEZE);
                        setLog("Learned spell: Flash Freeze!", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case TOME_FIRE_SWORD -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        player.learnSpell(Player.SpellType.FIRE_SWORD);
                        setLog("Learned spell: Fire Sword!", 2.5);
                        state = State.DUNGEON;
                        return;
                    }
                    case TOME_HEAL -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        player.learnSpell(Player.SpellType.HEAL);
                        setLog("Learned spell: Heal!", 2.5);
                        state = State.DUNGEON;
                        return;
                    }

                    // potions (consume + uses a turn)
                    case HP_POTION -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        int healed = player.healHp(8);
                        setLog("You drink an HP Potion. +" + healed + " HP.", 2.5);

                        state = State.DUNGEON;
                        turn.endPlayerTurn();
                        recomputeFov();
                        return;
                    }
                    case MP_POTION -> {
                        if (!player.useItem(chosen)) { setLog("You don't have that.", 2.5); return; }
                        int restored = player.healMp(6);
                        setLog("You drink an MP Potion. +" + restored + " MP.", 2.5);

                        state = State.DUNGEON;
                        turn.endPlayerTurn();
                        recomputeFov();
                        return;
                    }
                }
            }

            return;
        }

        // PAGE 1: SPELLS
        if (invPage == 1) {

            java.util.List<Player.SpellType> spells = player.knownSpellsInOrder();
            int n = spells.size();

            if (n == 0) {
                spellIndex = 0;
                spellScroll = 0;
                return;
            }

            // clamp
            if (spellIndex < 0) spellIndex = 0;
            if (spellIndex >= n) spellIndex = n - 1;

            // nav up/down
            int dir = 0;
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) dir = +1;

            if (dir != 0) {
                spellIndex += dir;
                if (spellIndex < 0) spellIndex = 0;
                if (spellIndex >= n) spellIndex = n - 1;
            }

            // keep visible
            if (spellIndex < spellScroll) spellScroll = spellIndex;
            if (spellIndex >= spellScroll + INV_VISIBLE_ROWS) spellScroll = spellIndex - INV_VISIBLE_ROWS + 1;
            if (spellScroll < 0) spellScroll = 0;
            if (spellScroll > Math.max(0, n - INV_VISIBLE_ROWS)) spellScroll = Math.max(0, n - INV_VISIBLE_ROWS);

            // No “use” in inventory for spells (battle casting only)
            return;
        }
    }

    private void updateBattle(Input input) {
        if (battle == null) {
            state = State.DUNGEON;
            return;
        }
        // Tick sprite anim timers (runs every frame while in battle)
        battle.tickAnims();

        // If a fade is running, ignore inputs/menus until it finishes
        if (isFading()) return;

        // Simple 3-option menu: Fight / Spell / Run
        if (battle.phase == Battle.Phase.PLAYER_MENU) {
            // menu navigation
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) {
                battle.menuIndex = (battle.menuIndex + 3) % 4;
            } else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) {
                battle.menuIndex = (battle.menuIndex + 1) % 4;
            }

            // confirm
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                // 0 = FIGHT
                if (battle.menuIndex == 0) {

                    int dmg = player.rollDamage(rng);

                    // Fire Sword: small burn damage added to normal attacks (battle-only)
                    if (battle.fireSwordActive) {
                        dmg += 2; // small, consistent bonus
                    }

                    battle.playerAtkFrames = 10; // attacker reacts now
                    battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8); // victim reacts a few frames later

                    // If foe is guarding, reduce this hit once
                    if (battle.foeGuarded) {
                        dmg = Math.max(1, dmg / 2);
                        battle.foeGuarded = false;
                        battle.log = "The " + battle.foe.name + " blocks! (reduced damage)";
                    } else {
                        battle.log = battle.fireSwordActive
                                ? "You attack (Fire Sword) for " + dmg + "!"
                                : "You attack for " + dmg + "!";
                    }

                    battle.foe.hp -= dmg;

                    if (battle.foe.hp <= 0) {
                        battle.foe.hp = 0;

                        battle.startEnemyDefeatAnim();
                        battle.log = "The " + battle.foe.name + " was defeated!";

                        battle.phase = Battle.Phase.WON;
                    } else {
                        battle.phase = Battle.Phase.ENEMY_DELAY;
                        battle.timerFrames = 30;
                    }
                    return;
                }

                // 1 = SPELL (open submenu)
                if (battle.menuIndex == 1) {
                    battle.phase = Battle.Phase.SPELL_MENU;
                    battle.spellIndex = 0;
                    battle.log = "Choose a spell.";
                    return;
                }

                // 2 = ITEM (open submenu)
                if (battle.menuIndex == 2) {
                    battle.phase = Battle.Phase.ITEM_MENU;
                    battle.itemIndex = 0;
                    battle.log = "Choose an item.";
                    return;
                }

                // 3 = RUN
                if (battle.menuIndex == 3) {
                    // simple: always succeed for now
                    battle.log = "You ran away!";
                    startBattleExitFade(false);
                    return;
                }
            }
        }

        if (battle.phase == Battle.Phase.ITEM_MENU) {

            // Build list of items you actually have right now
            java.util.List<ItemType> items = getUsableBattleItems();
            int itemCount = items.size();

            // If empty, show message + allow back out
            if (itemCount == 0) {
                // Renderer will show "No usable items." once in the menu area.
                // Keep the log line empty so it doesn't duplicate.
                battle.log = "";

                if (input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_BACK_SPACE) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
                    battle.phase = Battle.Phase.PLAYER_MENU;
                    battle.log = "Choose an action.";
                }
                return;
            }

            // Keep cursor valid
            if (battle.itemIndex < 0) battle.itemIndex = 0;
            if (battle.itemIndex >= itemCount) battle.itemIndex = itemCount - 1;

            // Back out
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_BACK_SPACE)) {
                battle.phase = Battle.Phase.PLAYER_MENU;
                battle.log = "Choose an action.";
                return;
            }

            // Navigation: Left/Up prev, Right/Down next
            int dir = 0;

            if (input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_UP)   || input.wasTapped(java.awt.event.KeyEvent.VK_W)) {
                dir = -1;
            }

            if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_DOWN)  || input.wasTapped(java.awt.event.KeyEvent.VK_S)) {
                dir = +1;
            }

            if (dir != 0) {
                battle.itemIndex = (battle.itemIndex + dir) % itemCount;
                if (battle.itemIndex < 0) battle.itemIndex += itemCount; // fix negative modulo
            }

            // Use selected item
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                ItemType chosen = items.get(battle.itemIndex);

                // Consume
                if (!player.useItem(chosen)) {
                    battle.log = "You don't have that.";
                    return; // doesn't consume turn
                }

                // Apply effect
                switch (chosen) {
                    case HP_POTION -> {
                        int healed = player.healHp(8);
                        battle.log = "You drink an HP Potion. +" + healed + " HP.";
                    }
                    case MP_POTION -> {
                        int restored = player.healMp(6);
                        battle.log = "You drink an MP Potion. +" + restored + " MP.";
                    }
                    default -> battle.log = "Nothing happens.";
                }

                // Consumes turn -> enemy after delay
                battle.phase = Battle.Phase.ENEMY_DELAY;
                battle.timerFrames = 30;
                return;
            }

            return;
        }

        if (battle.phase == Battle.Phase.SPELL_MENU) {

            var p = player;
            java.util.List<Player.SpellType> spells = p.knownSpellsInOrder();
            int n = spells.size();

            if (n == 0) {
                battle.log = "You know no spells.";
                if (input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_BACK_SPACE) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                        input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
                    battle.phase = Battle.Phase.PLAYER_MENU;
                    battle.log = "Choose an action.";
                }
                return;
            }

            // clamp cursor
            if (battle.spellIndex < 0) battle.spellIndex = 0;
            if (battle.spellIndex >= n) battle.spellIndex = n - 1;

            // back
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_BACK_SPACE)) {
                battle.phase = Battle.Phase.PLAYER_MENU;
                battle.log = "Choose an action.";
                return;
            }

            // nav
            int dir = 0;
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A)) dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D)) dir = +1;

            if (dir != 0) {
                battle.spellIndex = (battle.spellIndex + dir) % n;
                if (battle.spellIndex < 0) battle.spellIndex += n;
            }

            // cast
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                Player.SpellType chosen = spells.get(battle.spellIndex);

                // Spell costs
                int cost = switch (chosen) {
                    case MAGIC_STAB -> 3;
                    case ICE_SHARD -> 5;
                    case FLASH_FREEZE -> 6;
                    case FIRE_SWORD -> 5;
                    case HEAL -> 7;
                };

                if (player.mp < cost) {
                    battle.log = "Not enough MP!";
                    return;
                }

                // Pay MP
                player.mp -= cost;

                // Apply spell
                switch (chosen) {
                    case MAGIC_STAB -> {
                        int dmg = player.rollSpellDamage(rng);

                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                        if (battle.foeGuarded) {
                            dmg = Math.max(1, dmg / 2);
                            battle.foeGuarded = false;
                            battle.log = "Magic Stab hits through guard! (" + dmg + ")  (-" + cost + " MP)";
                        } else {
                            battle.log = "Magic Stab hits for " + dmg + "!  (-" + cost + " MP)";
                        }

                        battle.foe.hp -= dmg;
                    }

                    case ICE_SHARD -> {
                        int dmg = Math.max(1, player.rollSpellDamage(rng) - 1);

                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                        // Slow: increase enemy miss chance for 3 enemy actions
                        battle.foeSlowTurns = Math.max(battle.foeSlowTurns, 3);
                        battle.foeAccuracyPenaltyPct = 20; // enemy -20% accuracy while slow is active

                        battle.log = "Ice Shard hits for " + dmg + " and slows the foe!  (-" + cost + " MP)";
                        battle.foe.hp -= dmg;
                    }

                    case FLASH_FREEZE -> {
                        // Freeze: enemy misses 2 turns, no damage
                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 6);

                        battle.foeFrozenTurns = Math.max(battle.foeFrozenTurns, 2);

                        battle.log = "Flash Freeze! The foe is frozen solid!  (-" + cost + " MP)";
                    }

                    case FIRE_SWORD -> {
                        battle.fireSwordActive = true;
                        battle.log = "Fire Sword! Your blade ignites for the rest of the battle.  (-" + cost + " MP)";
                    }

                    case HEAL -> {
                        int healed = player.healHp(15);
                        battle.log = "Heal restores +" + healed + " HP.  (-" + cost + " MP)";
                    }
                }

                // Check kill if spell dealt damage
                if (battle.foe.hp <= 0) {
                    battle.foe.hp = 0;

                    battle.startEnemyDefeatAnim();
                    battle.log = "The " + battle.foe.name + " was defeated!";

                    battle.phase = Battle.Phase.WON;
                    return;
                }

                // Enemy turn next
                battle.phase = Battle.Phase.ENEMY_DELAY;
                battle.timerFrames = 30;
                return;
            }

            return;
        }

        if (battle.phase == Battle.Phase.ENEMY_ACT) {

            // Frozen: enemy loses turns
            if (battle.foeFrozenTurns > 0) {
                battle.foeFrozenTurns--;
                battle.log = "The " + battle.foe.name + " is frozen and can't move!";

                // decrement slow duration per enemy action too
                if (battle.foeSlowTurns > 0) {
                    battle.foeSlowTurns--;
                    if (battle.foeSlowTurns <= 0) {
                        battle.foeAccuracyPenaltyPct = 0;
                    }
                }

                battle.phase = Battle.Phase.PLAYER_MENU;
                return;
            }

            // Normal enemy act
            battle.log = battle.foe.performBattleMove(rng, player, battle);

            // Slow duration counts down on each enemy action
            if (battle.foeSlowTurns > 0) {
                battle.foeSlowTurns--;
                if (battle.foeSlowTurns <= 0) {
                    battle.foeAccuracyPenaltyPct = 0;
                }
            }

            if (player.hp < 0) player.hp = 0;

            if (player.hp <= 0) {
                battle.phase = Battle.Phase.LOST;
            } else {
                battle.phase = Battle.Phase.PLAYER_MENU;
            }
            return;
        }


        if (battle.phase == Battle.Phase.WON) {

            // Wait for your enemy defeat split/fade to finish
            if (battle.enemyDefeatFrames > 0) return;

            // Small hold after the defeat animation finishes (optional but feels nice)
            if (battle.timerFrames <= 0) battle.timerFrames = BATTLE_WIN_HOLD_FRAMES;
            battle.timerFrames--;

            if (battle.timerFrames <= 0) {
                startBattleExitFade(true); // victory -> fade out, switch to dungeon at black, fade in
            }
            return;
        }

        if (battle.phase == Battle.Phase.LOST) {
            battle.log = "You were defeated...";
            player.hp = 0;
            startDeathWipeToGameOver();
            return;
        }
        if (battle.phase == Battle.Phase.ENEMY_DELAY) {
            battle.timerFrames--;
            if (battle.timerFrames <= 0) {
                battle.phase = Battle.Phase.ENEMY_ACT;
            }
            return;
        }
    }

    private Chest getChestAt(int x, int y) {
        for (Chest c : chests) {
            if (c.x == x && c.y == y && !c.opened) return c;
        }
        return null;
    }

    private String itemName(ItemType t) {
        return switch (t) {
            case HP_POTION -> "HP Potion";
            case MP_POTION -> "MP Potion";
            case SWORD_WORN -> "Worn Sword";
            case SWORD_BRONZE -> "Bronze Sword";
            case SWORD_IRON -> "Iron Sword";
            case SWORD_STEEL -> "Steel Sword";
            case SWORD_KNIGHT -> "Knight Sword";
            case TOME_ICE_SHARD -> "Tome: Ice Shard";
            case TOME_FLASH_FREEZE -> "Tome: Flash Freeze";
            case TOME_FIRE_SWORD -> "Tome: Fire Sword";
            case TOME_HEAL -> "Tome: Heal";
        };
    }

    private void tickLogTimer() {
        // Only count down timed messages
        if (logFramesLeft > 0) {
            logFramesLeft--;
            if (logFramesLeft <= 0) {
                lastLog = "";
                logFramesLeft = 0;
            }
        }
    }

    private void beginFadeOutNow() {
        fadePhase = 1;          // fade out
        fadeStep = 1;           // start at first visible step immediately
        fadeAlpha = fadeStep / (float) FADE_STEPS;
        fadeHoldLeft = FADE_STEP_HOLD;

        // IMPORTANT: ensure at least 1 frame is rendered with the current screen + overlay
        fadeAwaitingPresent = true;
    }
    private void startBattleExitFade(boolean victory) {
        if (fadePhase != 0) return;

        battleExitPending = true;
        battleExitVictory = victory;

        beginFadeOutNow();
    }
    private void startBattleEnterFade(Enemy foe) {
        if (fadePhase != 0) return;

        battleStartPending = true;
        battleStartFoe = foe;

        beginFadeOutNow();
    }

    private void startNewGameFade(long seed) {
        if (fadePhase != 0) return;

        newGamePending = true;
        newGameSeed = seed;

        // IMPORTANT: use the same fade kickoff as battle transitions
        beginFadeOutNow();
    }

    private void tickScreenFade() {
        if (fadePhase == 0) return;
        // If we haven't presented a frame since the last fade change, don't advance.
        if (fadeAwaitingPresent) return;

        // Hold at the current step for a chunky “GB fade” feel
        if (fadeHoldLeft > 0) {
            fadeHoldLeft--;
            return;
        }

        if (fadePhase == 1) {
            // Fade OUT: step 0 -> FADE_STEPS
            fadeStep++;
            if (fadeStep > FADE_STEPS) fadeStep = FADE_STEPS;

            fadeAlpha = fadeStep / (float) FADE_STEPS;

            // Pause at each step
            fadeHoldLeft = FADE_STEP_HOLD;

            if (fadeStep >= FADE_STEPS) {
                // At full dark: do the transition

                if (battleExitPending) {
                    if (battleExitVictory) endBattleVictory();
                    else endBattleRun();
                    battleExitPending = false;
                }

                if (battleStartPending) {
                    if (player != null && player.hp <= 0) player.hp = 1;

                    state = State.BATTLE;
                    battle = new Battle(battleStartFoe);
                    battle.log = "A " + battleStartFoe.name + " challenges you!";

                    battleStartPending = false;
                    battleStartFoe = null;
                }

                // ✅ NEW: start a new run (from main menu) while fully dark
                if (newGamePending) {
                    newRun(newGameSeed);
                    newGamePending = false;
                }

                // Now fade back IN
                fadePhase = 2;
                fadeHoldLeft = FADE_STEP_HOLD;
                fadeAwaitingPresent = true; // show at least one fully-dark frame before fading in
            }
            return;
        }

        if (fadePhase == 2) {
            // Fade IN: step FADE_STEPS -> 0
            fadeStep--;
            if (fadeStep < 0) fadeStep = 0;

            fadeAlpha = fadeStep / (float) FADE_STEPS;

            // Pause at each step
            fadeHoldLeft = FADE_STEP_HOLD;

            if (fadeStep <= 0) {
                fadeAlpha = 0f;
                fadePhase = 0;
            }
        }
    }

    // Temporary message (seconds can be 1.5, 2, 3, etc.)
    private void setLog(String msg, double seconds) {
        lastLog = (msg == null) ? "" : msg;
        if (seconds <= 0) {
            logFramesLeft = 0;
            lastLog = "";
            return;
        }
        logFramesLeft = Math.max(1, (int) Math.round(seconds * UI_FPS));
    }

    // Sticky message (stays until replaced by another setLog/setLogSticky call)
    private void setLogSticky(String msg) {
        lastLog = (msg == null) ? "" : msg;
        logFramesLeft = -1;
    }

    private void updateLevelUp(Input input) {
        // pick stat: 1/2/3
        if (input.wasTapped(java.awt.event.KeyEvent.VK_1)) {
            player.levelUp(Player.Stat.HP);
            setLog("Level up: +HP!", 2.5);
            generateFloor();
            return;
        }
        if (input.wasTapped(java.awt.event.KeyEvent.VK_2)) {
            player.levelUp(Player.Stat.MP);
            setLog("Level up: +MP!", 2.5);
            generateFloor();
            return;
        }
        if (input.wasTapped(java.awt.event.KeyEvent.VK_3)) {
            player.levelUp(Player.Stat.ATK);
            setLog("Level up: +ATK!", 2.5);
            generateFloor();
            return;
        }
    }

    private void updateMainMenu(Input input) {
        // If a fade is running, ignore menu inputs
        if (isFading()) return;

        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

            startNewGameFade(System.currentTimeMillis());
        }
    }

    private void updateGameOver(Input input) {

        // If a fade is running, ignore inputs
        if (isFading()) return;

        if (input.wasTapped(java.awt.event.KeyEvent.VK_Y) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

            startNewGameFade(System.currentTimeMillis());
            return;
        }

        if (input.wasTapped(java.awt.event.KeyEvent.VK_N)) {
            goToMainMenu();
        }
    }

    private void startDeathWipeToGameOver() {
        if (deathWipeActive) return;

        // ensure HP is clamped
        if (player != null && player.hp < 0) player.hp = 0;

        deathWipeActive = true;
        deathWipeProgress = 0f;
        deathWipeFrames = 0;

        // cancel any other fades so this is the only transition
        fadePhase = 0;
        fadeStep = 0;
        fadeHoldLeft = 0;
        fadeAlpha = 0f;
    }

    private void tickDeathWipe() {
        if (!deathWipeActive) return;

        deathWipeFrames++;
        deathWipeProgress = Math.min(1f, deathWipeFrames / (float) DEATH_WIPE_FRAMES_MAX);

        if (deathWipeProgress >= 1f) {
            // Now we are fully dark — go to GAME_OVER and fade IN a touch (optional but feels good)
            deathWipeActive = false;

            state = State.GAME_OVER;
            setLogSticky("You died on floor " + floor + ". Press R to restart.");

            // slight fade-in to the Game Over screen
            fadePhase = 2;
            fadeStep = FADE_STEPS;
            fadeAlpha = 1f;
            fadeHoldLeft = 0;
        }
    }

    // Call once per rendered frame (after drawing)
    public void onFramePresented() {
        fadeAwaitingPresent = false;
    }

    public int spellCost(Player.SpellType s) {
        return switch (s) {
            case MAGIC_STAB -> 3;
            case ICE_SHARD -> 5;
            case FLASH_FREEZE -> 6;
            case FIRE_SWORD -> 5;
            case HEAL -> 7;
        };
    }

    public String spellLabel(Player.SpellType s) {
        int cost = spellCost(s);
        return switch (s) {
            case MAGIC_STAB   -> "Magic Stab   (-" + cost + " MP) dmg";
            case ICE_SHARD    -> "Ice Shard    (-" + cost + " MP) dmg + slow";
            case FLASH_FREEZE -> "Flash Freeze (-" + cost + " MP) freeze";
            case FIRE_SWORD   -> "Fire Sword   (-" + cost + " MP) battle buff";
            case HEAL         -> "Heal         (-" + cost + " MP) +15 HP";
        };
    }

    // Getter for renderer
    public Battle battle() { return battle; }

    // Getters for UI/rendering
    public Dungeon dungeon() { return dungeon; }
    public Player player() { return player; }
    public List<Enemy> enemies() { return enemies; }
    public String lastLog() { return lastLog; }
    public int floor() { return floor; }
    public long seed() { return seed; }
    public State state() { return state; }
    public float fadeAlpha() { return fadeAlpha; }
    public boolean isFading() { return fadePhase != 0; }
    public boolean isDeathWipeActive() { return deathWipeActive; }
    public float deathWipeProgress() { return deathWipeProgress; }
    public int invPage() { return invPage; }
    public int invScroll() { return invScroll; }
    public int spellIndex() { return spellIndex; }
    public int spellScroll() { return spellScroll; }
    public int invVisibleRows() { return INV_VISIBLE_ROWS; }

}