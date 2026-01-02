package org.example.game;

import org.example.entity.Enemy;
import org.example.entity.Player;
import org.example.game.util.RNG;
import org.example.world.Dungeon;
import org.example.world.DungeonGenerator;
import org.example.world.Fov;

import java.util.ArrayList;
import java.util.List;

public final class Game {
    public enum State { PLAYING, DEAD }

    private final TurnSystem turn = new TurnSystem();
    private final Fov fov = new Fov();

    private RNG rng;
    private long seed;
    private int floor;

    private Dungeon dungeon;
    private Player player;
    private final List<Enemy> enemies = new ArrayList<>();

    private String lastLog = "Find the stairs (>) and press Enter.";
    private State state = State.PLAYING;

    public void newRun(long seed) {
        this.seed = seed;
        this.rng = new RNG(seed);
        this.floor = 1;
        this.state = State.PLAYING;
        this.lastLog = "New run. Good luck.";
        generateFloor();
    }

    private void generateFloor() {
        enemies.clear();

        DungeonGenerator gen = new DungeonGenerator(rng);
        this.dungeon = gen.generate(); // uses GameConfig.DUNGEON_W/H

        // Place player at start
        int[] start = dungeon.getStart();
        player = new Player(start[0], start[1]);

        // Place enemies
        int n = rng.range(GameConfig.START_ENEMIES_MIN, GameConfig.START_ENEMIES_MAX) + (floor - 1) / 2;
        for (int i = 0; i < n; i++) {
            int[] p = dungeon.findRandomFloor(rng);
            // avoid spawning on player
            if (p[0] == player.x && p[1] == player.y) { i--; continue; }
            enemies.add(Enemy.basic(p[0], p[1], floor));
        }

        // Update FOV initially
        recomputeFov();
        turn.reset();

        lastLog = "Floor " + floor + ". Move with arrows/WASD. Bump to attack.";
    }

    public void update(Input input) {
        if (state == State.DEAD) {
            return;
        }

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

        // Stairs
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER)) {
            if (dungeon.isStairsDown(player.x, player.y)) {
                floor++;
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
        Enemy e = getEnemyAt(nx, ny);
        if (e != null) {
            int dmg = player.rollDamage(rng);
            e.hp -= dmg;
            lastLog = "You hit " + e.name + " for " + dmg + ".";

            if (e.hp <= 0) {
                lastLog = "You defeated " + e.name + "!";
                enemies.remove(e);
            }
            return true;
        }

        // Otherwise move if passable
        if (dungeon.isWalkable(nx, ny) && getEnemyAt(nx, ny) == null) {
            player.x = nx;
            player.y = ny;
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
                    state = State.DEAD;
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

    // Getters for UI/rendering
    public Dungeon dungeon() { return dungeon; }
    public Player player() { return player; }
    public List<Enemy> enemies() { return enemies; }
    public String lastLog() { return lastLog; }
    public int floor() { return floor; }
    public long seed() { return seed; }
    public State state() { return state; }
}