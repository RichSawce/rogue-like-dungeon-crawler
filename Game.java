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

    // Inventory screen cursor (0 = HP potion, 1 = MP potion)
    private int invIndex = 0;

    public List<Chest> chests() { return chests; }
    public int invIndex() { return invIndex; }

    private RNG rng;
    private long seed;
    private int floor;

    private Dungeon dungeon;
    private Player player;
    private final List<Enemy> enemies = new ArrayList<>();

    private String lastLog = "Find the stairs (>) and press Enter.";
    private State state = State.DUNGEON;

    private Battle battle;

    private String menuHint = "Press ENTER to Start";

    private java.util.List<ItemType> getUsableBattleItems() {
        return player.inv.nonEmptyTypes();
    }

    private String itemLabel(ItemType t) {
        int c = player.inv.count(t);
        return switch (t) {
            case HP_POTION -> "HP Potion  x" + c + "  (+8 HP)";
            case MP_POTION -> "MP Potion  x" + c + "  (+6 MP)";
            default -> t.name() + "  x" + c;
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

            ItemType loot = (rng.nextInt(100) < 50) ? ItemType.HP_POTION : ItemType.MP_POTION;
            chests.add(new Chest(p[0], p[1], loot));
        }

        // Update FOV initially
        recomputeFov();
        turn.reset();

        lastLog = "Floor " + floor + ". Move with arrows/WASD. Bump to attack.";
        state = State.DUNGEON;
        battle = null;
    }

    public void update(Input input) {
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
            lastLog = "You wait.";
            turn.endPlayerTurn();
            recomputeFov();
            return;
        }

        // Open inventory
        if (input.wasTapped(java.awt.event.KeyEvent.VK_I)) {
            state = State.INVENTORY;
            lastLog = "Inventory: Use an item (consumes a turn). ESC to close.";
            invIndex = 0;
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

                lastLog = "You descend...";
                generateFloor();
            } else {
                lastLog = "No stairs here.";
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
                lastLog = "You found a " + itemName(chest.loot) + "!";
            }

            return true;
        }

        lastLog = "Bump.";
        return true; // still consumes a turn (classic roguelike feel)
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
                lastLog = e.name + " hits you for " + dmg + ".";
                if (player.hp <= 0) {
                    player.hp = 0;
                    state = State.GAME_OVER;
                    lastLog = "You died on floor " + floor + ". Press R to restart.";
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
        state = State.BATTLE;
        battle = new Battle(e);
        battle.log = "A " + e.name + " challenges you!";
    }

    private void endBattleVictory() {
        // Remove the defeated enemy from the dungeon
        enemies.remove(battle.foe);

        int gained = battle.foe.xpValue();
        player.gainExp(gained);

        lastLog = "You gained " + gained + " EXP.";
        battle = null;
        state = State.DUNGEON;

        recomputeFov();
        turn.reset();
    }

    private void endBattleRun() {
        lastLog = "You fled!";
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

        // Build list of items you actually have
        java.util.List<ItemType> items = player.inv.nonEmptyTypes();
        int n = items.size();

        // If empty: show message and don't allow "use"
        if (n == 0) {
            lastLog = "Inventory: empty.";
            return;
        }

        // Keep cursor valid (important when item counts change)
        if (invIndex < 0) invIndex = 0;
        if (invIndex >= n) invIndex = n - 1;

        // nav (wrap)
        int dir = 0;
        if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) dir = -1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) dir = +1;

        if (dir != 0) {
            invIndex = (invIndex + dir) % n;
            if (invIndex < 0) invIndex += n;
        }

        // use
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

            ItemType chosen = items.get(invIndex);

            if (!player.useItem(chosen)) {
                lastLog = "You don't have that.";
                return;
            }

            switch (chosen) {
                case HP_POTION -> {
                    int healed = player.healHp(8);
                    lastLog = "You drink an HP Potion. +" + healed + " HP.";
                }
                case MP_POTION -> {
                    int restored = player.healMp(6);
                    lastLog = "You drink an MP Potion. +" + restored + " MP.";
                }
                default -> lastLog = "Nothing happens.";
            }

            // Consumes a turn in dungeon (keeps balance)
            state = State.DUNGEON;
            turn.endPlayerTurn();
            recomputeFov();
        }
    }

    private void updateBattle(Input input) {
        if (battle == null) {
            state = State.DUNGEON;
            return;
        }
        // Tick sprite anim timers (runs every frame while in battle)
        battle.tickAnims();

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

                    battle.playerAtkFrames = 10; // attacker reacts now
                    battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8); // victim reacts a few frames later

                    // If foe is guarding, reduce this hit once
                    if (battle.foeGuarded) {
                        dmg = Math.max(1, dmg / 2);
                        battle.foeGuarded = false;
                        battle.log = "The " + battle.foe.name + " blocks! (reduced damage)";
                    } else {
                        battle.log = "You attack for " + dmg + "!";
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

                // 1 = SPELL
                if (battle.menuIndex == 1) {
                    int cost = 3;
                    if (player.mp < cost) {
                        battle.log = "Not enough MP!";
                        return;
                    }
                    player.mp -= cost;

                    int dmg = player.rollSpellDamage(rng);

                    battle.playerAtkFrames = 10; // attacker reacts now
                    battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8); // victim reacts a few frames later

                    if (battle.foeGuarded) {
                        dmg = Math.max(1, dmg / 2);
                        battle.foeGuarded = false;
                        battle.log = "The " + battle.foe.name + " guards through it!";
                    } else {
                        battle.log = "Spell hits for " + dmg + "! (-" + cost + " MP)";
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
                    endBattleRun();
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

        if (battle.phase == Battle.Phase.ENEMY_ACT) {
            battle.log = battle.foe.performBattleMove(rng, player, battle);

            if (player.hp < 0) player.hp = 0;

            if (player.hp <= 0) {
                battle.phase = Battle.Phase.LOST;
            } else {
                battle.phase = Battle.Phase.PLAYER_MENU;
            }
            return;
        }

        if (battle.phase == Battle.Phase.WON) {
            // Press Enter/Space to return
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
                endBattleVictory();
            }
            return;
        }

        if (battle.phase == Battle.Phase.LOST) {
            battle.log = "You were defeated...";
            state = State.GAME_OVER;
            // keep battle object around if you want, or clear it:
            // battle = null;
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
        return (t == ItemType.HP_POTION) ? "HP Potion" : "MP Potion";
    }

    private void updateLevelUp(Input input) {
        // pick stat: 1/2/3
        if (input.wasTapped(java.awt.event.KeyEvent.VK_1)) {
            player.levelUp(Player.Stat.HP);
            lastLog = "Level up: +HP!";
            generateFloor();
            return;
        }
        if (input.wasTapped(java.awt.event.KeyEvent.VK_2)) {
            player.levelUp(Player.Stat.MP);
            lastLog = "Level up: +MP!";
            generateFloor();
            return;
        }
        if (input.wasTapped(java.awt.event.KeyEvent.VK_3)) {
            player.levelUp(Player.Stat.ATK);
            lastLog = "Level up: +ATK!";
            generateFloor();
            return;
        }
    }

    private void updateMainMenu(Input input) {
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
            newRun(System.currentTimeMillis());
        }
    }

    private void updateGameOver(Input input) {

        if (input.wasTapped(java.awt.event.KeyEvent.VK_Y) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
            newRun(System.currentTimeMillis());
            return;
        }

        if (input.wasTapped(java.awt.event.KeyEvent.VK_N)) {
            goToMainMenu();
        }
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
}