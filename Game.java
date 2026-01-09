package org.example.game;

import org.example.item.GroundItem;
import org.example.entity.Enemy;
import org.example.entity.Player;
import org.example.game.util.RNG;
import org.example.world.Dungeon;
import org.example.world.DungeonGenerator;
import org.example.world.Fov;
import org.example.entity.Chest;
import org.example.item.ItemType;
import org.example.world.Tile;
import org.example.world.Town;
import org.example.world.WorldMap;   // you already have this interface
import org.example.world.TownGenerator;
import org.example.world.BuildingGenerator;
import org.example.world.BuildingLot;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class Game {
    public enum State {MAIN_MENU, DUNGEON, BATTLE, LEVEL_UP, INVENTORY, GAME_OVER}

    public enum Zone {TOWN, BUILDING, DUNGEON}

    private final TurnSystem turn = new TurnSystem();
    private final Fov fov = new Fov();

    private final List<Chest> chests = new ArrayList<>();

    public List<Chest> chests() {
        return chests;
    }

    private final List<GroundItem> groundItems = new ArrayList<>();

    public List<GroundItem> groundItems() {
        return groundItems;
    }
    private boolean townPortalActive = false;
    private int townPortalFloor = 1;
    private int townPortalDungeonX = 0, townPortalDungeonY = 0;
    private int townPortalTownX = 0, townPortalTownY = 0;

    private Zone zone = Zone.TOWN;

    // Town + Interiors use the same tile grid style as Dungeon for now
    private Town town;
    private Dungeon building; // keep as Dungeon for now (stub interior)
    // Cache building interiors so re-entering the same door reuses the same generated map
    private final Map<BuildingLot, Dungeon> buildingInteriorCache = new HashMap<>();
    // Cache dungeon floors so each floor is generated once per run.
    private static final class DungeonFloorState {
        final Dungeon dungeon;
        final java.util.List<Enemy> enemies;
        final java.util.List<Chest> chests;
        final java.util.List<GroundItem> groundItems;
        final boolean floorKeyObtained;

        DungeonFloorState(
                Dungeon dungeon,
                java.util.List<Enemy> enemies,
                java.util.List<Chest> chests,
                java.util.List<GroundItem> groundItems,
                boolean floorKeyObtained
        ) {
            this.dungeon = dungeon;
            this.enemies = enemies;
            this.chests = chests;
            this.groundItems = groundItems;
            this.floorKeyObtained = floorKeyObtained;
        }
    }

    // Spawn override for when we want to spawn somewhere other than dungeon start
    private boolean hasPendingDungeonSpawn = false;
    private int pendingDungeonSpawnX = 0, pendingDungeonSpawnY = 0;

    private final Map<Integer, DungeonFloorState> dungeonFloorCache = new HashMap<>();

    // Remember where to return when leaving a building
    private int returnX = 0, returnY = 0;

    // Remember where the crypt entrance is in Town (so we can come back cleanly)
    private int cryptTownX = 0, cryptTownY = 0;

    // Optional: remember where you entered the dungeon from (for coming back)
    private boolean enteredDungeonFromCrypt = false;
    // Inventory UI
    private int invPage = 0;           // 0 = Items, 1 = Spells, 2 = Stats
    private int invIndex = 0;

    public int invIndex() {
        return invIndex;
    }

    private int invScroll = 0;         // scroll offset for items

    private int spellIndex = 0;        // selection index for spells
    private int spellScroll = 0;       // scroll offset for spells

    // -----------------------------
// Level-up modal UI state
// -----------------------------
    private int levelUpStage = 0;      // 0 = confirm YES/NO, 1 = pick stat
    private int levelUpYesNoIndex = 0; // 0 = YES, 1 = NO
    private int levelUpStatIndex = 0;  // 0 = HP, 1 = MP, 2 = ATK

    private boolean levelUpDeferred = false;  // player said "No" earlier -> only offer at stairs
    private boolean levelUpOffered = false;   // prevents re-prompt spam when EXP stays >= expToNext
    private boolean levelUpFromStairs = false; // if true, after closing modal we descend

    public int levelUpStage() {
        return levelUpStage;
    }

    public int levelUpYesNoIndex() {
        return levelUpYesNoIndex;
    }

    public int levelUpStatIndex() {
        return levelUpStatIndex;
    }

    public boolean levelUpFromStairs() {
        return levelUpFromStairs;
    }

    public boolean levelUpDeferred() {
        return levelUpDeferred;
    }

    // How many rows fit in the list area (renderer will draw exactly this many)
    private static final int INV_VISIBLE_ROWS = 7;

    private RNG rng;
    private long seed;
    private int floor;

    private Dungeon dungeon;
    private Player player;
    private final List<Enemy> enemies = new ArrayList<>();

    private String lastLog = "Find the stairs (>) and press Enter.";

    private String lastLogBeforeInventory = "";

    public int[] outsideOfDoor(BuildingLot b) {
        return switch (b.doorSide) {
            case NORTH -> new int[]{b.doorX, b.doorY - 1};
            case SOUTH -> new int[]{b.doorX, b.doorY + 1};
            case WEST -> new int[]{b.doorX - 1, b.doorY};
            case EAST -> new int[]{b.doorX + 1, b.doorY};
        };
    }

    private int[] safeOutsideTileInTown(int x, int y) {
        // If the chosen tile is invalid, fall back to the town start.
        if (town != null && town.inBounds(x, y) && town.isWalkable(x, y)) {
            return new int[]{x, y};
        }
        int[] start = town.getStart();
        return new int[]{start[0], start[1]};
    }

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

    // ---- Battle tuning ----
    private static final int PLAYER_BASE_DODGE_PCT = 12;
    private static final int FOE_BASE_DODGE_PCT = 8;
    // ---- Battle message pacing (60fps) ----
    private static final int BATTLE_ENEMY_DELAY_FRAMES = 50;   // was 30 (~0.83s)
    private static final int BATTLE_ENEMY_MESSAGE_FRAMES = 75; // was 45 (~1.25s)

    // If SLOW is active, apply big penalties (already stored on battlefields)
    private static final int SLOW_ACC_PENALTY_PCT = 30;
    private static final int SLOW_DODGE_PENALTY_PCT = 20;

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
            case KEY -> "Key x" + c;
            case TOWN_PORTAL -> "Town Portal  x" + c + " (Opens a return portal)";

            // swords (small boosts that stack nicely with level ATK ups)
            // swords (small boosts that stack nicely with level ATK ups)
            case SWORD_WORN -> "Worn Sword  x" + c;
            case SWORD_BRONZE -> "Bronze Sword x" + c;
            case SWORD_IRON -> "Iron Sword   x" + c;
            case SWORD_STEEL -> "Steel Sword  x" + c;
            case SWORD_KNIGHT -> "Knight Sword x" + c;

            // spell tomes
            case TOME_ICE_SHARD -> "Tome: Ice Shard x" + c + " (Learn spell)";
            case TOME_SLOW_POKE -> "Tome: Slow Poke x" + c + " (Learn spell)";
            case TOME_FLASH_FREEZE -> "Tome: Flash Freeze x" + c + " (Learn spell)";
            case TOME_FIRE_SWORD -> "Tome: Fire Sword x" + c + " (Learn spell)";
            case TOME_HEAL -> "Tome: Heal x" + c + " (Learn spell)";
        };
    }

    // ---------- Magic damage helpers (INT scaling) ----------

    private int magicIntBonus() {
        // Simple and obvious: 1 INT = +1 to magic damage.
        // If you want slower scaling later, change to: player.intelligence() / 2
        return Math.max(0, player.intelligence());
    }

    private int spellBaseMin(Player.SpellType sp) {
        return switch (sp) {
            case ICE_SHARD -> 4;
            case FLASH_FREEZE -> 2;

            // Not part of the INT base-range system:
            case MAGIC_STAB -> 0;
            case SLOW_POKE -> 0;

            // Non-damage spells:
            case HEAL -> 0;
            case FIRE_SWORD -> 0;
        };
    }

    private int spellBaseMax(Player.SpellType sp) {
        return switch (sp) {
            case ICE_SHARD -> 7;
            case FLASH_FREEZE -> 4;

            // Not part of the INT base-range system:
            case MAGIC_STAB -> 0;
            case SLOW_POKE -> 0;

            // Non-damage spells:
            case HEAL -> 0;
            case FIRE_SWORD -> 0;
        };
    }

    public int spellDamageMin(Player.SpellType sp) {
        int base = spellBaseMin(sp);
        if (base <= 0) return 0;
        return base + magicIntBonus();
    }

    public int spellDamageMax(Player.SpellType sp) {
        int base = spellBaseMax(sp);
        if (base <= 0) return 0;
        return base + magicIntBonus();
    }

    public void newRun(long seed) {
        this.seed = seed;
        this.rng = new RNG(seed);

        this.floor = 1;
        this.battle = null;
        this.enemies.clear();
        this.chests.clear();
        this.groundItems.clear();
        this.buildingInteriorCache.clear();
        this.dungeonFloorCache.clear();

        // Create player and put them in Town
        this.player = null;

        // Build Town
        generateTown();

        this.state = State.DUNGEON; // “DUNGEON” here really means “free-roam mode”
        // If you want, rename later, but it works now.
        townPortalActive = false;
        hasPendingDungeonSpawn = false;

        setLog("Welcome to Town. Find the Crypt.", 3.0);
        recomputeFov();
        turn.reset();
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

    private boolean floorKeyObtained = false;

    private void generateTown() {
        // Town has no enemies/chests/ground items for now
        enemies.clear();
        chests.clear();
        groundItems.clear();
        buildingInteriorCache.clear();

        TownGenerator tg = new TownGenerator(rng);
        town = tg.generate(GameConfig.DUNGEON_W, GameConfig.DUNGEON_H); // or make TOWN_W/TOWN_H later

        zone = Zone.TOWN;

        int[] start = town.getStart();
        if (player == null) player = new Player(start[0], start[1]);
        player.x = start[0];
        player.y = start[1];
        townPortalTownX = start[0];
        townPortalTownY = start[1];

        int[] crypt = town.getCryptDoorPos();
        cryptTownX = crypt[0];
        cryptTownY = crypt[1];
    }

    private void generateFloor() {
        // If we've already generated this floor during this run, restore it.
        if (loadDungeonFloorFromCache(floor)) {

            // For now: spawn player at this floor's start.
            // Later, if you add "stairs up", you can spawn at stairs up instead.
            int[] start = dungeon.getStart();
            if (player == null) player = new Player(start[0], start[1]);
            player.x = start[0];
            player.y = start[1];

            if (hasPendingDungeonSpawn) {
                player.x = pendingDungeonSpawnX;
                player.y = pendingDungeonSpawnY;
                hasPendingDungeonSpawn = false;
            }

            // ✅ Restamp portal tile if it's active on this floor
            if (townPortalActive && zone == Zone.DUNGEON && floor == townPortalFloor && dungeon != null) {
                dungeon.setTile(townPortalDungeonX, townPortalDungeonY, Tile.TOWN_PORTAL);
            }

            ensureStairsDownExists();
            recomputeFov();
            turn.reset();

            setLog("You return to floor " + floor + ".", 2.0);
            state = State.DUNGEON;
            battle = null;
            return;

        }
        enemies.clear();
        chests.clear(); // IMPORTANT: clear first so starter chests aren't wiped later
        groundItems.clear();
        floorKeyObtained = false;

        DungeonGenerator gen = new DungeonGenerator(rng);
        this.dungeon = gen.generate(); // uses GameConfig.DUNGEON_W/H

        // Place player at start
        int[] start = dungeon.getStart();
        if (hasPendingDungeonSpawn) {
            player.x = pendingDungeonSpawnX;
            player.y = pendingDungeonSpawnY;
            hasPendingDungeonSpawn = false;
        }
        if (player == null) {
            player = new Player(start[0], start[1]);
        } else {
            player.x = start[0];
            player.y = start[1];
        }

        ensureStairsDownExists();
        // Spawn guaranteed starter test chests (floor 1) DELETE LATER
        placeStarterTestChests();

        // Place enemies
        int n = rng.range(GameConfig.START_ENEMIES_MIN, GameConfig.START_ENEMIES_MAX) + (floor - 1) / 2;
        for (int i = 0; i < n; i++) {
            int[] p = dungeon.findRandomRoomFloor(rng, true); // ✅ rooms only (can be in stairs room for normal loot if you want)

            // avoid spawning on player OR on a chest
            if ((p[0] == player.x && p[1] == player.y) || getChestAt(p[0], p[1]) != null) {
                i--;
                continue;
            }

            enemies.add(Enemy.spawnForFloor(p[0], p[1], floor, rng));
        }

        // Place chests (a few per floor) — GUARANTEE: exactly 1 key chest per floor
        int chestCount = 1 + rng.nextInt(2); // 1-2 per floor
        if (chestCount < 1) chestCount = 1;

// 1) Place the KEY chest first (ROOM ONLY, not inside stairs room)
        {
            int[] p;
            while (true) {
                p = dungeon.findRandomRoomFloor(rng, true); // ✅ rooms only, exclude stairs room

                if ((p[0] == player.x && p[1] == player.y) ||
                        getEnemyAt(p[0], p[1]) != null ||
                        getChestAt(p[0], p[1]) != null) continue;

                break;
            }
            chests.add(new Chest(p[0], p[1], ItemType.KEY));
        }

// 2) Place the remaining chests with normal loot (NO extra keys)
        for (int i = 1; i < chestCount; i++) {
            int[] p = dungeon.findRandomRoomFloor(rng, true); // ✅ rooms only (can be in stairs room for normal loot if you want)

            if ((p[0] == player.x && p[1] == player.y) ||
                    getEnemyAt(p[0], p[1]) != null ||
                    getChestAt(p[0], p[1]) != null) {
                i--;
                continue;
            }

            int r = rng.nextInt(100);

            ItemType loot;
            if (r < 25) loot = ItemType.HP_POTION;
            else if (r < 50) loot = ItemType.TOWN_PORTAL;
            else if (r < 70) loot = ItemType.MP_POTION;
            else if (r < 78) loot = ItemType.SWORD_WORN;
            else if (r < 84) loot = ItemType.SWORD_BRONZE;
            else if (r < 89) loot = ItemType.TOME_ICE_SHARD;
            else if (r < 91) loot = ItemType.SWORD_IRON;
            else if (r < 92) loot = ItemType.TOME_SLOW_POKE;
            else if (r < 93) loot = ItemType.TOME_FIRE_SWORD;
            else if (r < 97) loot = ItemType.TOME_HEAL;
            else if (r < 98) loot = ItemType.SWORD_STEEL;
            else loot = ItemType.TOME_FLASH_FREEZE;

            chests.add(new Chest(p[0], p[1], loot));
        }

        // Update FOV initially
        recomputeFov();
        turn.reset();

        setLog("Move with arrows/WASD. Walk to attack.", 2.5);
        state = State.DUNGEON;
        battle = null;
        saveCurrentDungeonFloorToCache();
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

            // ✅ If the player spent a turn from inventory (ex: drank potion),
            // let enemies act even while the inventory screen is open.
            if (!turn.isPlayerTurn()) {
                enemyTurn();
                turn.endEnemyTurn();
                recomputeFov();
            }

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

        refreshLevelUpOfferGuards();

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
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D))
            dx = 1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W))
            dy = -1;
        else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S))
            dy = 1;

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
            lastLogBeforeInventory = lastLog;
            state = State.INVENTORY;
            lastLog = "Inventory: ←/→ tabs   ↑/↓ select   ENTER use/equip/cast   ESC close";
            invPage = 0; // Items
            invIndex = 0;
            invScroll = 0;
            spellIndex = 0;
            spellScroll = 0;
            return;
        }

        // Stairs
        if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER)) {

            WorldMap map = activeMap();
            if (map == null) return;

            // --- TOWN: enter buildings / crypt ---
            if (zone == Zone.TOWN) {
                if (isOnTownPortalInTown()) {
                    takeTownPortalBackToDungeon();
                    return;
                }
                Tile t = town.tile(player.x, player.y);

                // Crypt entry tile (you already stamp it as Tile.CRYPT_DOOR)
                if (t == Tile.CRYPT_DOOR) {
                    enterDungeonFromCrypt();
                    return;
                }

                // Other building doors (you stamp as Tile.DOOR)
                if (t == Tile.DOOR) {
                    enterBuildingInterior();
                    return;
                }

                setLog("Nothing to interact with here.", 2.0);
                return;
            }

            // --- BUILDING: exit back to town ---
            if (zone == Zone.BUILDING) {
                if (building == null) return;

                Tile t = building.tile(player.x, player.y);

                // If your BuildingGenerator stamps the exit tile as Tile.DOOR
                if (t == Tile.DOOR) {
                    exitBuildingToTown();
                    turn.reset();
                    return;
                }

                setLog("Nothing to interact with here.", 2.0);
                return;
            }

            // --- DUNGEON: exit door / stairs down ---
            if (zone == Zone.DUNGEON) {
                if (isOnTownPortalInDungeon()) {
                    takeTownPortalToTown();
                    return;
                }
                // STAIRS UP (start tile)
                if (isOnStairsUp()) {
                    ascendOneFloor();
                    return;
                }

                // ✅ EXIT: allow leaving the dungeon via the entrance door
                if (isAtOrNextToDoor(dungeon, player.x, player.y)) {
                    exitDungeonToTown();
                    return;
                }

                // ✅ STAIRS DOWN
                if (dungeon.isStairsDown(player.x, player.y)) {

                    if (player.canLevelUp()) {
                        openLevelUpModal(true, true);
                        return;
                    }

                    descendAfterLevelUpDecision();
                    return;
                }

                setLog("No stairs here.", 2.5);
                return;
            }
        }

        // ✅ Add THIS line right here (end of update):
        refreshLevelUpOfferGuards();
    }

    private int[] outsideCryptDoor() {
        // Prefer any walkable adjacent tile. Order can be tuned to taste.
        int x = cryptTownX, y = cryptTownY;

        int[][] dirs = {{0, 1}, {0, -1}, {-1, 0}, {1, 0}}; // S, N, W, E
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1];
            if (town.inBounds(nx, ny) && town.isWalkable(nx, ny)) {
                return new int[]{nx, ny};
            }
        }
        return new int[]{x, y}; // fallback
    }

    private void enterDungeonFromCrypt() {
        if (isFading()) return;

        enteredDungeonFromCrypt = true;

        // Remember where to return in town
        int[] out = outsideCryptDoor();
        returnX = out[0];
        returnY = out[1];

        // Start dungeon fresh (or keep “current dungeon” if you want persistence later)
        zone = Zone.DUNGEON;

        floor = 1;
        generateFloor();

        setLog("You enter the Crypt...", 2.5);
        recomputeFov();
        turn.reset();
    }

    private boolean isAtOrNextToDoor(WorldMap map, int x, int y) {
        if (map == null) return false;

        // Standing on door
        if (map.tile(x, y) == Tile.DOOR) return true;

        // Adjacent to door
        return (map.tile(x + 1, y) == Tile.DOOR) ||
                (map.tile(x - 1, y) == Tile.DOOR) ||
                (map.tile(x, y + 1) == Tile.DOOR) ||
                (map.tile(x, y - 1) == Tile.DOOR);
    }

    private void exitDungeonToTown() {
        saveCurrentDungeonFloorToCache();
        enemies.clear();
        chests.clear();
        groundItems.clear();

        zone = Zone.TOWN;
        player.x = returnX;
        player.y = returnY;

        setLog("You emerge from the Crypt.", 2.5);
        recomputeFov();
        turn.reset();
    }

    private void enterBuildingInterior() {
        if (isFading()) return;

        // We are standing on a DOOR tile in town. Find which building owns it.
        BuildingLot lot = town.buildingAtDoor(player.x, player.y);

        if (lot != null) {
            int[] out = outsideOfDoor(lot);
            int[] safe = safeOutsideTileInTown(out[0], out[1]);
            returnX = safe[0];
            returnY = safe[1];
        } else {
            // Fallback: if for some reason we can't resolve the lot,
            // at least don't crash—return to current position.
            returnX = player.x;
            returnY = player.y;
        }

        // Create (or reuse) an interior “map” for THIS specific lot
        if (lot != null && buildingInteriorCache.containsKey(lot)) {
            building = buildingInteriorCache.get(lot);
        } else {
            BuildingGenerator bg = new BuildingGenerator(rng);
            building = bg.generateInterior();

            // Cache it so this building stays the same when you re-enter
            if (lot != null) {
                buildingInteriorCache.put(lot, building);
            }
        }

        zone = Zone.BUILDING;

        int[] spawn = building.getStart();
        player.x = spawn[0];
        player.y = spawn[1];

        setLog("You enter the building.", 2.5);
        recomputeFov();
        turn.reset();
    }

    private void exitBuildingToTown() {
        zone = Zone.TOWN;
        building = null;

        player.x = returnX;
        player.y = returnY;

        setLog("You step outside.", 2.5);
        recomputeFov();
        turn.reset();
    }

    private void refreshLevelUpOfferGuards() {
        // If they cannot level up anymore (e.g., leveled up), allow future offers
        if (player != null && !player.canLevelUp()) {
            levelUpOffered = false;
            levelUpDeferred = false; // optional: clears stale deferral once they're no longer eligible
        }
    }

    // Treat the dungeon start tile as STAIRS UP
    private boolean isOnStairsUp() {
        if (zone != Zone.DUNGEON || dungeon == null || player == null) return false;
        int[] s = dungeon.getStart();
        return player.x == s[0] && player.y == s[1];
    }

    // Find STAIRS DOWN tile by scanning (uses your existing dungeon.isStairsDown)
    private int[] findStairsDownPos() {
        if (dungeon == null) return null;
        for (int y = 0; y < GameConfig.DUNGEON_H; y++) {
            for (int x = 0; x < GameConfig.DUNGEON_W; x++) {
                if (dungeon.isStairsDown(x, y)) return new int[]{x, y};
            }
        }
        return null;
    }

    private void ascendOneFloor() {
        if (zone != Zone.DUNGEON) return;

        if (floor <= 1) {
            exitDungeonToTown();
            return;
        }

        saveCurrentDungeonFloorToCache();

        floor--;

        // Spawn at the stairs-down of the destination floor
        DungeonFloorState cached = dungeonFloorCache.get(floor);
        if (cached != null) {
            // temporarily point dungeon at cached to locate stairs down
            Dungeon prev = dungeon;
            dungeon = cached.dungeon;
            int[] sd = findStairsDownPos();
            dungeon = prev;

            if (sd != null) {
                hasPendingDungeonSpawn = true;
                pendingDungeonSpawnX = sd[0];
                pendingDungeonSpawnY = sd[1];
            }
        } else {
            // if not cached yet, we’ll still try to spawn near stairs-down after generation
            // fallback: spawn at start (generateFloor will apply if pending is set later)
            // You can skip setting pending here if you prefer.
        }

        setLog("You ascend...", 2.5);
        generateFloor();
    }


    private void openLevelUpModal(boolean fromStairs, boolean markAsOffered) {
        state = State.LEVEL_UP;
        levelUpStage = 0;          // confirm first
        levelUpYesNoIndex = 0;     // default YES
        levelUpStatIndex = 0;      // default HP
        levelUpFromStairs = fromStairs;

        if (markAsOffered) levelUpOffered = true;

        // Optional: keep log quiet while modal is open
        setLogSticky(fromStairs ? "Level up at the stairs?" : "Level up now?");
    }

    private void closeLevelUpModalToDungeon(String msg) {
        state = State.DUNGEON;
        levelUpFromStairs = false;

        if (msg != null && !msg.isBlank()) setLog(msg, 2.5);
        else setLogSticky("Move with arrows/WASD. Walk to attack.");
    }

    private void descendAfterLevelUpDecision() {
        // player is moving on; clear any “defer” and allow a fresh offer next time
        levelUpDeferred = false;
        levelUpOffered = false;
        levelUpFromStairs = false;

        saveCurrentDungeonFloorToCache();
        floor++;
        setLog("You descend...", 2.5);
        generateFloor();
    }

    private boolean tryPlayerMoveOrAttack(int dx, int dy) {
        WorldMap map = activeMap();
        if (map == null) return false;

        int nx = player.x + dx;
        int ny = player.y + dy;

        // Only allow enemies/battles in DUNGEON for now
        if (zone == Zone.DUNGEON) {
            Enemy e = getEnemyAt(nx, ny);
            if (e != null) {
                startBattle(e);
                return true;
            }
        }

        // Door unlock logic: only in DUNGEON (locked doors shouldn’t exist in town)
        if (zone == Zone.DUNGEON && dungeon != null
                && dungeon.inBounds(nx, ny)
                && dungeon.tile(nx, ny) == Tile.LOCKED_DOOR) {

            if (player.useKey()) {
                dungeon.setTile(nx, ny, Tile.FLOOR);
                setLog("You unlock the door. (Keys: " + player.keyCount() + ")", 2.5);

                player.x = nx;
                player.y = ny;
                handleTileAndGroundPickups();
                return true;
            } else {
                setLog("Locked door. You need a key.", 2.5);
                return true;
            }
        }

        // Normal move if passable
        if (map.isWalkable(nx, ny) && (zone != Zone.DUNGEON || getEnemyAt(nx, ny) == null)) {
            player.x = nx;
            player.y = ny;

            // Chests / ground items only make sense in dungeon for now
            if (zone == Zone.DUNGEON) {
                Chest chest = getChestAt(player.x, player.y);
                if (chest != null) {
                    chest.opened = true;

                    ItemType loot = chest.loot;
                    if (loot == ItemType.KEY && floorKeyObtained) loot = rollNonKeyChestLoot();

                    player.inv.add(loot, 1);

                    if (loot == ItemType.KEY) {
                        onKeyObtainedThisFloor();
                        setLog("You found a key! (Keys: " + player.keyCount() + ")", 2.5);
                    } else {
                        setLog("You found a " + itemName(loot) + "!", 2.5);
                    }
                }

                handleTileAndGroundPickups();
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
                setLog(e.name + " hits you for " + dmg + ".", 2.5);
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
        WorldMap map = activeMap();
        if (map == null || player == null) return;

        fov.compute(map, player.x, player.y, GameConfig.PLAYER_FOV_RADIUS);

        // Apply visibility to the correct map type
        if (zone == Zone.TOWN) {
            town.applyVisibilityFromFov(fov);
        } else if (zone == Zone.BUILDING) {
            building.applyVisibilityFromFov(fov);
        } else {
            dungeon.applyVisibilityFromFov(fov);
        }
    }

    private int countStairsDown() {
        if (dungeon == null) return 0;

        // If stairsX/stairsY were set via setStairsDown, this is authoritative
        for (int y = 0; y < dungeon.h(); y++) {
            for (int x = 0; x < dungeon.w(); x++) {
                if (dungeon.isStairsDown(x, y)) return 1;
            }
        }
        return 0;
    }
    private void saveCurrentDungeonFloorToCache() {
        if (zone != Zone.DUNGEON) return;
        if (dungeon == null) return;

        dungeonFloorCache.put(
                floor,
                new DungeonFloorState(
                        dungeon,
                        new java.util.ArrayList<>(enemies),
                        new java.util.ArrayList<>(chests),
                        new java.util.ArrayList<>(groundItems),
                        floorKeyObtained
                )
        );
    }

    private boolean loadDungeonFloorFromCache(int targetFloor) {
        DungeonFloorState st = dungeonFloorCache.get(targetFloor);
        if (st == null) return false;

        this.dungeon = st.dungeon;

        enemies.clear();
        chests.clear();
        groundItems.clear();

        enemies.addAll(st.enemies);
        chests.addAll(st.chests);
        groundItems.addAll(st.groundItems);

        floorKeyObtained = st.floorKeyObtained;
        return true;
    }

    private void ensureStairsDownExists() {
        if (dungeon == null || player == null) return;

        if (countStairsDown() > 0) return;

        // Force place stairs somewhere safe and walkable
        int[] p;
        while (true) {
            p = dungeon.findRandomRoomFloor(rng, true);

            if (p[0] == player.x && p[1] == player.y) continue;
            if (getEnemyAt(p[0], p[1]) != null) continue;
            if (getChestAt(p[0], p[1]) != null) continue;
            if (getGroundItemAt(p[0], p[1]) != null) continue;

            break;
        }

        dungeon.setStairsDown(p[0], p[1]);

        setLog("WARNING: stairs missing; forced spawn. seed=" + seed + " floor=" + floor, 4.0);
    }

    private Enemy getEnemyAt(int x, int y) {
        for (Enemy e : enemies) {
            if (e.x == x && e.y == y && e.hp > 0) return e;
        }
        return null;
    }

    private boolean isAnyKeyOnGround() {
        for (var gi : groundItems) {
            if (gi.type == ItemType.KEY) return true;
        }
        return false;
    }

    private void onKeyObtainedThisFloor() {
        floorKeyObtained = true;

        // Remove any key that might already be lying on the ground
        groundItems.removeIf(gi -> gi.type == ItemType.KEY);

        // If you ever use dungeon KEY tiles again, clear them too:
        if (dungeon != null) {
            dungeon.clearKeyTile();
        }
    }

    /**
     * Used when a KEY chest is opened but the key has already been obtained this floor.
     */
    private ItemType rollNonKeyChestLoot() {
        int r = rng.nextInt(100);

        if (r < 40) return ItemType.HP_POTION;
        if (r < 70) return ItemType.MP_POTION;
        if (r < 78) return ItemType.SWORD_WORN;
        if (r < 84) return ItemType.SWORD_BRONZE;
        if (r < 89) return ItemType.TOME_ICE_SHARD;
        if (r < 91) return ItemType.SWORD_IRON;
        if (r < 92) return ItemType.TOME_SLOW_POKE;
        if (r < 93) return ItemType.TOME_FIRE_SWORD;
        if (r < 97) return ItemType.TOME_HEAL;
        if (r < 98) return ItemType.SWORD_STEEL;
        return ItemType.TOME_FLASH_FREEZE;
    }

    private void startBattle(Enemy e) {
        // Start a chunky fade-out, then enter battle at full dark, then fade in.
        startBattleEnterFade(e);
    }

    private void endBattleVictory() {
        // Remove the defeated enemy from the dungeon
        enemies.remove(battle.foe);

        // ✅ 15% drop chance
        maybeDropEnemyLoot(battle.foe.x, battle.foe.y);

        int gained = battle.foe.xpValue();
        player.gainExp(gained);

        setLog("You gained " + gained + " EXP.", 2.5);

        // Tear down battle first (so we’re not “in battle” anymore)
        battle = null;

        // If player can level up, offer immediately (unless they previously declined)
        if (player.canLevelUp() && !levelUpDeferred && !levelUpOffered) {
            openLevelUpModal(false, true); // state becomes LEVEL_UP inside here
            recomputeFov();
            turn.reset();
            return; // ✅ IMPORTANT: don’t overwrite state after opening modal
        }

        // Otherwise return to dungeon normally
        state = State.DUNGEON;
        recomputeFov();
        turn.reset();
    }

    private void maybeDropEnemyLoot(int x, int y) {
        // 15% chance
        if (rng.nextInt(100) >= 15) return;

        // Don't drop on blocked tiles (just in case)
        if (!dungeon.inBounds(x, y) || !dungeon.isWalkable(x, y)) return;

        // Avoid stacking multiple items on one tile (optional rule)
        if (getGroundItemAt(x, y) != null) return;

        // Simple loot table (tune however you want):
        // 60% HP, 30% MP, 10% KEY (as a tile OR as an item; here we use tile KEY)
        int r = rng.nextInt(100);

        if (r < 10) {
            if (!floorKeyObtained && !isAnyKeyOnGround()) {

                // If the enemy died in the stairs room, DO NOT drop the key there.
                int dropX = x;
                int dropY = y;

                boolean badKeyDropSpot = dungeon.isInsideStairsRoom(x, y) || dungeon.isStairsDown(x, y);
                if (badKeyDropSpot) {
                    int[] p = dungeon.findRandomRoomFloor(rng, true); // exclude stairs room
                    dropX = p[0];
                    dropY = p[1];
                }

                // Avoid stacking
                if (getGroundItemAt(dropX, dropY) == null) {
                    groundItems.add(new GroundItem(dropX, dropY, ItemType.KEY));
                    setLog("An enemy dropped a key!", 2.5);
                }
            }
            return;
        }

        ItemType drop = (r < 70) ? ItemType.HP_POTION : ItemType.MP_POTION;
        groundItems.add(new org.example.item.GroundItem(x, y, drop));
        // Don't spam the log too much; optional:
        setLog("An enemy dropped something!", 3.5);
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

// Only restore if we’re still showing the generic inventory help text
            if (lastLog != null && lastLog.startsWith("Inventory:")) {
                lastLog = lastLogBeforeInventory;
            }

            lastLogBeforeInventory = "";
            return;
        }

        // switch tabs (left/right)
        if (input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A)) {
            invPage = (invPage + 3 - 1) % 3; // 0<->1<->2
        } else if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D)) {
            invPage = (invPage + 1) % 3;
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
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W))
                dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S))
                dir = +1;

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
                        return;
                    }
                    case SWORD_BRONZE -> {
                        player.equipWeapon("Bronze Sword", 1, 1);
                        setLog("Equipped Bronze Sword. (+1/+1 ATK)", 2.5);
                        return;
                    }
                    case SWORD_IRON -> {
                        player.equipWeapon("Iron Sword", 1, 2);
                        setLog("Equipped Iron Sword. (+1/+2 ATK)", 2.5);
                        return;
                    }
                    case SWORD_STEEL -> {
                        player.equipWeapon("Steel Sword", 2, 2);
                        setLog("Equipped Steel Sword. (+2/+2 ATK)", 2.5);
                        return;
                    }
                    case SWORD_KNIGHT -> {
                        player.equipWeapon("Knight Sword", 2, 3);
                        setLog("Equipped Knight Sword. (+2/+3 ATK)", 2.5);
                        return;
                    }

                    // tomes: consume to learn
                    case TOME_ICE_SHARD -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        player.learnSpell(Player.SpellType.ICE_SHARD);
                        setLog("Learned spell: Ice Shard!", 2.5);
                        return;
                    }
                    case TOME_SLOW_POKE -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        player.learnSpell(Player.SpellType.SLOW_POKE);
                        setLog("Learned spell: Slow Poke!", 2.5);
                        return;
                    }
                    case TOME_FLASH_FREEZE -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        player.learnSpell(Player.SpellType.FLASH_FREEZE);
                        setLog("Learned spell: Flash Freeze!", 2.5);
                        return;
                    }
                    case TOME_FIRE_SWORD -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        player.learnSpell(Player.SpellType.FIRE_SWORD);
                        setLog("Learned spell: Fire Sword!", 2.5);
                        return;
                    }
                    case TOME_HEAL -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        player.learnSpell(Player.SpellType.HEAL);
                        setLog("Learned spell: Heal!", 2.5);
                        return;
                    }

                    // potions (consume + uses a turn)
                    case HP_POTION -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        int healed = player.healHp(8);
                        setLog("You drink an HP Potion. +" + healed + " HP.", 2.5);


                        turn.endPlayerTurn();
                        recomputeFov();
                        return;
                    }
                    case MP_POTION -> {
                        if (!player.useItem(chosen)) {
                            setLog("You don't have that.", 2.5);
                            return;
                        }
                        int restored = player.healMp(6);
                        setLog("You drink an MP Potion. +" + restored + " MP.", 2.5);


                        turn.endPlayerTurn();
                        recomputeFov();
                        return;
                    }
                    case TOWN_PORTAL -> {
                        // close inventory back to free-roam
                        state = State.DUNGEON;
                        lastLog = lastLogBeforeInventory;

                        // attempt to use/consume + create portal
                        useTownPortalScroll();

                        // Spend a turn ONLY if we actually consumed it (i.e., portal became active)
                        // (If you prefer: check the consume result directly instead—see note below.)
                        if (townPortalActive) {
                            turn.endPlayerTurn();
                            recomputeFov();
                        }
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
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W))
                dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S))
                dir = +1;

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

            // ✅ Cast from inventory (outside battle) — Heal only
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                Player.SpellType chosen = spells.get(spellIndex);

                if (chosen != Player.SpellType.HEAL) {
                    setLog("That spell can only be cast in battle.", 2.5);
                    return;
                }

                // Already full? Don’t spend MP or a turn.
                if (player.hp >= player.maxHp) {
                    setLog("You're already at full HP.", 2.5);
                    return;
                }

                int cost = spellCost(chosen); // HEAL = 7 in your switch

                if (player.mp < cost) {
                    setLog("Not enough MP!", 2.5);
                    return;
                }

                // Pay MP + heal
                player.mp -= cost;
                int healed = player.healHp(15);
                setLog("Heal restores +" + healed + " HP.  (-" + cost + " MP)", 2.5);

                // Casting outside battle should cost a turn (like potions)
                turn.endPlayerTurn();
                recomputeFov();
                return;
            }
            return;

        }
        // PAGE 2: STATS (read-only)
        if (invPage == 2) {
        }
    }

    private void updateBattle(Input input) {
        if (battle == null) {
            state = State.DUNGEON;
            return;
        }
        // Tick sprite anim timers (runs every frame while in battle)
        battle.tickAnims();
        battle.tickDamage(player);

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

                    battle.playerAtkFrames = 10;

                    // Defender dodge check first
                    if (foeDodgedAttack()) {
                        battle.log = "The " + battle.foe.name + " dodges your attack!";
                        battle.phase = Battle.Phase.ENEMY_DELAY;
                        battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;

                        tickPlayerBattleStatusesOnAction();
                        return;
                    }

                    int dmg = player.rollDamage(rng);

// Fire Sword bonus: add +6..+9 damage ON TOP while active
                    if (battle.fireSwordActive) {
                        int bonus = rng.range(6, 9); // assumes RNG.range(min,max) is inclusive
                        dmg += bonus;
                    }

                    // Guard reduction
                    if (battle.foeGuarded) {
                        dmg = Math.max(1, dmg / 2);
                        battle.foeGuarded = false;
                        battle.log = "The " + battle.foe.name + " blocks! (reduced damage)";
                    } else {
                        battle.log = battle.fireSwordActive
                                ? "You attack (Fire Sword) for " + dmg + "!"
                                : "You attack for " + dmg + "!";
                    }

                    // player acted -> tick player statuses
                    tickPlayerBattleStatusesOnAction();

                    // Hit reaction (visual) happens later
                    battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                    // Damage lands later (HP bar delay)
                    boolean willKill = (battle.foe.hp - dmg) <= 0;
                    battle.queueFoeDamage(
                            Battle.HIT_LAG_FRAMES,
                            dmg,
                            willKill,
                            "The " + battle.foe.name + " was defeated!"
                    );

                    // Enemy acts after delay (unless queued damage kills it; tickDamage flips to WON)
                    battle.phase = Battle.Phase.ENEMY_DELAY;
                    battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
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

        if (battle.phase == Battle.Phase.ENEMY_MESSAGE) {

            // Let the player dismiss early
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {
                battle.timerFrames = 0;
            }

            battle.timerFrames--;
            if (battle.timerFrames <= 0) {
                battle.phase = Battle.Phase.PLAYER_MENU;
                battle.log = "Choose an action.";
            }
            return;
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
                    input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W)) {
                dir = -1;
            }

            if (input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) {
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
                battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
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
                    input.wasTapped(java.awt.event.KeyEvent.VK_LEFT) || input.wasTapped(java.awt.event.KeyEvent.VK_A))
                dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_RIGHT) || input.wasTapped(java.awt.event.KeyEvent.VK_D))
                dir = +1;

            if (dir != 0) {
                battle.spellIndex = (battle.spellIndex + dir) % n;
                if (battle.spellIndex < 0) battle.spellIndex += n;
            }

            // cast
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) || input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                Player.SpellType chosen = spells.get(battle.spellIndex);

                // Spell costs
                int cost = spellCost(chosen);

                if (player.mp < cost) {
                    battle.log = "Not enough MP!";
                    return;
                }

                // Pay MP
                player.mp -= cost;

                // Apply spell
                switch (chosen) {
                    case MAGIC_STAB -> {
                        // Defender dodge
                        if (foeDodgedAttack()) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Magic Stab... but the " + battle.foe.name + " dodges!";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }

// Miss check (spells can miss)
                        int spellAcc = 85;

// Will can ignore slow penalty for this action (only if slowed)
                        int pen = Battle.applyWillVsSlowPenalty(rng, battle.playerAccuracyPenaltyPct, player.will(), battle.playerSlowTurns);

                        if (!Battle.rollSpellHit(
                                rng,
                                spellAcc,
                                player.intelligence(),
                                battle.foe.will(),
                                player.speed(),
                                battle.foe.speed(),
                                pen
                        )) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Magic Stab... MISS!  (-" + cost + " MP)";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }
                        // Magic Stab = weapon damage + extra 3..5 (NO INT scaling)
                        int dmg = player.rollBaseWeaponDamage(rng) + rng.range(3, 5);
                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                        if (battle.foeGuarded) {
                            dmg = Math.max(1, dmg / 2);
                            battle.foeGuarded = false;
                            battle.log = "Magic Stab hits through guard! (" + dmg + ")  (-" + cost + " MP)";
                        } else {
                            battle.log = "Magic Stab hits for " + dmg + "!  (-" + cost + " MP)";
                        }

                        tickPlayerBattleStatusesOnAction();

                        boolean willKill = (battle.foe.hp - dmg) <= 0;
                        battle.queueFoeDamage(
                                Battle.HIT_LAG_FRAMES,
                                dmg,
                                willKill,
                                "The " + battle.foe.name + " was defeated!"
                        );
                    }

                    case SLOW_POKE -> {

                        // Special dodge rule: only possible if foe is 2x faster than player
                        boolean canDodge = battle.foe.speed() >= player.speed() * 2;

                        if (canDodge && foeDodgedAttack()) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Slow Poke... but the " + battle.foe.name + " dodges!  (-" + cost + " MP)";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }

                        // Optional: spells can also miss (keep consistent with Magic Stab / others)
                        int spellAcc = 90; // tune
                        int pen = Battle.applyWillVsSlowPenalty(rng, battle.playerAccuracyPenaltyPct, player.will(), battle.playerSlowTurns);

                        if (!Battle.rollSpellHit(
                                rng,
                                spellAcc,
                                player.intelligence(),
                                battle.foe.will(),
                                player.speed(),
                                battle.foe.speed(),
                                pen
                        )) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Slow Poke... MISS!  (-" + cost + " MP)";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }

                        // Damage: half weapon roll (still scales with ATK)
                        int dmg = Math.max(1, player.rollBaseWeaponDamage(rng) / 2);

                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                        if (battle.foeGuarded) {
                            dmg = Math.max(1, dmg / 2);
                            battle.foeGuarded = false;
                            battle.log = "Slow Poke hits, but the foe blocks! (" + dmg + ")  (-" + cost + " MP)";
                        } else {
                            battle.log = "Slow Poke hits for " + dmg + " and slows the foe!  (-" + cost + " MP)";
                        }

                        // Apply slow effect (guaranteed)
                        int slowTurns = 2; // tune
                        battle.foeSlowTurns = Math.max(battle.foeSlowTurns, slowTurns);
                        battle.foeAccuracyPenaltyPct = SLOW_ACC_PENALTY_PCT;
                        battle.foeDodgePenaltyPct = SLOW_DODGE_PENALTY_PCT;

                        tickPlayerBattleStatusesOnAction();

                        boolean willKill = (battle.foe.hp - dmg) <= 0;
                        battle.queueFoeDamage(
                                Battle.HIT_LAG_FRAMES,
                                dmg,
                                willKill,
                                "The " + battle.foe.name + " was defeated!"
                        );
                    }

                    case ICE_SHARD -> {
                        if (foeDodgedAttack()) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Ice Shard... but the " + battle.foe.name + " dodges!";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }

                        int spellAcc = 80; // tune (ice shard a bit less reliable)
                        if (!Battle.rollHit(rng, spellAcc, battle.playerAccuracyPenaltyPct)) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Ice Shard... MISS!  (-" + cost + " MP)";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }
                        int dmg = rng.range(spellDamageMin(Player.SpellType.ICE_SHARD),
                                spellDamageMax(Player.SpellType.ICE_SHARD));
                        dmg = Math.max(1, dmg);

                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 8);

                        // Slow: increase enemy miss chance for 3 enemy actions
                        int slowBase = 3;
                        int slowBonus = player.intelligence() / 8; // +1 per 8 INT (small!)
                        int slowTurns = slowBase + slowBonus;
                        battle.foeAccuracyPenaltyPct = SLOW_ACC_PENALTY_PCT;
                        battle.foeDodgePenaltyPct = SLOW_DODGE_PENALTY_PCT;

                        battle.log = "Ice Shard hits for " + dmg + " and slows the foe!  (-" + cost + " MP)";
                        tickPlayerBattleStatusesOnAction();

                        boolean resisted = Battle.rollStatusResist(rng, player.intelligence(), battle.foe.will());
                        if (!resisted) {
                            battle.foeSlowTurns = Math.max(battle.foeSlowTurns, slowTurns);
                            battle.foeAccuracyPenaltyPct = SLOW_ACC_PENALTY_PCT;
                            battle.foeDodgePenaltyPct = SLOW_DODGE_PENALTY_PCT;
                            battle.log = "Ice Shard hits for " + dmg + " and slows the foe!  (-" + cost + " MP)";
                        } else {
                            battle.log = "Ice Shard hits for " + dmg + " but the foe resists the slow!  (-" + cost + " MP)";
                        }

                        boolean willKill = (battle.foe.hp - dmg) <= 0;
                        battle.queueFoeDamage(
                                Battle.HIT_LAG_FRAMES,
                                dmg,
                                willKill,
                                "The " + battle.foe.name + " was defeated!"
                        );
                    }

                    case FLASH_FREEZE -> {
                        // Freeze: enemy misses 2 turns, no damage

                        if (foeDodgedAttack()) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Flash Freeze... but the " + battle.foe.name + " dodges!";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }

                        int spellAcc = 75; // tune (strong effect, less reliable)
                        if (!Battle.rollHit(rng, spellAcc, battle.playerAccuracyPenaltyPct)) {
                            battle.playerAtkFrames = 10;
                            battle.log = "Flash Freeze... MISS!  (-" + cost + " MP)";
                            tickPlayerBattleStatusesOnAction();
                            battle.phase = Battle.Phase.ENEMY_DELAY;
                            battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
                            return;
                        }
                        battle.playerAtkFrames = 10;
                        battle.queueEnemyHit(Battle.HIT_LAG_FRAMES, 6);

                        int dmg = rng.range(spellDamageMin(Player.SpellType.FLASH_FREEZE),
                                spellDamageMax(Player.SpellType.FLASH_FREEZE));
                        dmg = Math.max(1, dmg);

                        boolean resisted = Battle.rollStatusResist(rng, player.intelligence(), battle.foe.will());
                        if (resisted) {
                            battle.log = "Flash Freeze hits for " + dmg + ", but the foe resists being frozen!  (-" + cost + " MP)";
                        } else {
                            int freezeBase = 2;
                            int freezeBonus = player.intelligence() / 12; // small scaling
                            int freezeTurns = freezeBase + freezeBonus;

                            battle.foeFrozenTurns = Math.max(battle.foeFrozenTurns, freezeTurns);
                            battle.log = "Flash Freeze hits for " + dmg + "! The foe is frozen solid!  (-" + cost + " MP)";
                        }

// queue damage like other spells
                        boolean willKill = (battle.foe.hp - dmg) <= 0;
                        battle.queueFoeDamage(
                                Battle.HIT_LAG_FRAMES,
                                dmg,
                                willKill,
                                "The " + battle.foe.name + " was defeated!"
                        );

                        tickPlayerBattleStatusesOnAction();
                    }

                    case FIRE_SWORD -> {
                        battle.fireSwordActive = true;
                        battle.log = "Fire Sword! Your blade ignites for the rest of the battle.  (-" + cost + " MP)";
                        tickPlayerBattleStatusesOnAction();
                    }

                    case HEAL -> {
                        int healed = player.healHp(15);
                        battle.log = "Heal restores +" + healed + " HP.  (-" + cost + " MP)";
                        tickPlayerBattleStatusesOnAction();
                    }
                }

// do NOT subtract hp here

                // Enemy turn next
                battle.phase = Battle.Phase.ENEMY_DELAY;
                battle.timerFrames = BATTLE_ENEMY_DELAY_FRAMES;
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
                        battle.foeDodgePenaltyPct = 0;
                    }
                }

                battle.phase = Battle.Phase.ENEMY_MESSAGE;
                battle.timerFrames = BATTLE_ENEMY_MESSAGE_FRAMES;
                return;
            }

            // Normal enemy act
            battle.log = battle.foe.performBattleMove(rng, player, battle);

            // Slow duration counts down on each enemy action
            if (battle.foeSlowTurns > 0) {
                battle.foeSlowTurns--;
                if (battle.foeSlowTurns <= 0) {
                    battle.foeAccuracyPenaltyPct = 0;
                    battle.foeDodgePenaltyPct = 0;
                }
            }

            if (player.hp < 0) player.hp = 0;

            if (player.hp <= 0) {
                battle.phase = Battle.Phase.LOST;
            } else {
                battle.phase = Battle.Phase.ENEMY_MESSAGE;
                battle.timerFrames = BATTLE_ENEMY_MESSAGE_FRAMES;
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

    private GroundItem getGroundItemAt(int x, int y) {
        for (GroundItem gi : groundItems) {
            if (gi.x == x && gi.y == y) return gi;
        }
        return null;
    }

    // FOR ITEM TESTING, DELETE LATER
    private void placeStarterTestChests() {
        // Only on floor 1, only if dungeon/player exist
        if (floor != 1 || dungeon == null || player == null) return;

        // Loot list: all tomes + "first next sword"
        ItemType[] loot = new ItemType[]{
                ItemType.TOME_ICE_SHARD,
                ItemType.TOME_HEAL,
                ItemType.SWORD_BRONZE,
                ItemType.TOME_SLOW_POKE,
                ItemType.TOWN_PORTAL
        };

        // Offsets around the player (try nearby tiles in this order)
        int[][] offsets = new int[][]{
                {1, 0}, {0, 1}, {-1, 0}, {0, -1},
                {2, 0}, {0, 2}, {-2, 0}, {0, -2},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
        };

        int placed = 0;

        for (int i = 0; i < offsets.length && placed < loot.length; i++) {
            int cx = player.x + offsets[i][0];
            int cy = player.y + offsets[i][1];

            // Must be walkable and not already occupied
            if (!dungeon.inBounds(cx, cy)) continue;
            if (!dungeon.isWalkable(cx, cy)) continue;
            if (getEnemyAt(cx, cy) != null) continue;
            if (getChestAt(cx, cy) != null) continue;
            if (cx == player.x && cy == player.y) continue;

            chests.add(new Chest(cx, cy, loot[placed]));
            placed++;
        }

        // Optional: give yourself a hint in the log
        if (placed > 0) {
            setLog("Test chests spawned near start (tomes + Bronze Sword).", 2.5);
        }
    }

    private String itemName(ItemType t) {
        return switch (t) {
            case HP_POTION -> "HP Potion";
            case MP_POTION -> "MP Potion";
            case KEY -> "Key";
            case TOWN_PORTAL -> "Town Portal";
            case SWORD_WORN -> "Worn Sword";
            case SWORD_BRONZE -> "Bronze Sword";
            case SWORD_IRON -> "Iron Sword";
            case SWORD_STEEL -> "Steel Sword";
            case SWORD_KNIGHT -> "Knight Sword";
            case TOME_SLOW_POKE -> "Tome: Slow Poke";
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
        // ESC acts like "No" on confirm screen, or "Back" on stat screen
        boolean esc = input.wasTapped(java.awt.event.KeyEvent.VK_ESCAPE) ||
                input.wasTapped(java.awt.event.KeyEvent.VK_BACK_SPACE);

        // -----------------
        // Stage 0: Confirm
        // -----------------
        if (levelUpStage == 0) {

            // toggle YES/NO with up/down
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S)) {
                levelUpYesNoIndex = 1 - levelUpYesNoIndex; // 0<->1
            }

            // confirm
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                if (levelUpYesNoIndex == 0) {
                    // YES -> go to stat pick
                    levelUpStage = 1;
                    return;
                } else {
                    // NO
                    levelUpDeferred = true;
                    levelUpOffered = false; // ✅ IMPORTANT: don't permanently block future offers

                    if (levelUpFromStairs) {
                        // If declined at stairs, descend anyway
                        descendAfterLevelUpDecision();
                    } else {
                        closeLevelUpModalToDungeon("Okay. You can level up at the stairs.");
                    }
                    return;
                }
            }

            if (esc) {
                // treat ESC as NO
                levelUpDeferred = true;
                levelUpOffered = false; // ✅ IMPORTANT: same as pressing NO

                if (levelUpFromStairs) descendAfterLevelUpDecision();
                else closeLevelUpModalToDungeon("Okay. You can level up at the stairs.");

                return;
            }

            return;
        }

        // -----------------
        // Stage 1: Pick stat
        // -----------------
        if (levelUpStage == 1) {

            // nav stats (HP/MP/ATK)
            int dir = 0;
            if (input.wasTapped(java.awt.event.KeyEvent.VK_UP) || input.wasTapped(java.awt.event.KeyEvent.VK_W))
                dir = -1;
            else if (input.wasTapped(java.awt.event.KeyEvent.VK_DOWN) || input.wasTapped(java.awt.event.KeyEvent.VK_S))
                dir = +1;

            if (dir != 0) {
                levelUpStatIndex = (levelUpStatIndex + dir) % 6;
                if (levelUpStatIndex < 0) levelUpStatIndex += 6;
            }

            // confirm stat
            if (input.wasTapped(java.awt.event.KeyEvent.VK_ENTER) ||
                    input.wasTapped(java.awt.event.KeyEvent.VK_SPACE)) {

                Player.Stat chosen = switch (levelUpStatIndex) {
                    case 0 -> Player.Stat.HP;
                    case 1 -> Player.Stat.MP;
                    case 2 -> Player.Stat.ATK;
                    case 3 -> Player.Stat.SPEED;
                    case 4 -> Player.Stat.WILL;
                    default -> Player.Stat.INT;
                };

                // Apply
                player.levelUp(chosen);

                // Player accepted a level-up -> clear deferral and allow future offers
                levelUpDeferred = false;
                levelUpOffered = false;

                if (levelUpFromStairs) {
                    // Level up THEN descend
                    descendAfterLevelUpDecision();
                } else {
                    closeLevelUpModalToDungeon("Level up!");
                }
                return;
            }

            // back to confirm
            if (esc) {
                levelUpStage = 0;
                return;
            }
        }
    }

    private boolean isOnTownPortalInTown() {
        return zone == Zone.TOWN && townPortalActive && player != null
                && player.x == townPortalTownX && player.y == townPortalTownY;
    }

    private boolean isOnTownPortalInDungeon() {
        return zone == Zone.DUNGEON && townPortalActive && player != null
                && floor == townPortalFloor
                && player.x == townPortalDungeonX && player.y == townPortalDungeonY;
    }

    private void useTownPortalScroll() {
        if (zone != Zone.DUNGEON) {
            setLog("You can only use that in the dungeon.", 2.0);
            return;
        }

        if (player == null || !player.inv.consumeOne(ItemType.TOWN_PORTAL)) {
            setLog("You don't have a Town Portal scroll.", 2.0);
            return;
        }

        townPortalActive = true;
        townPortalFloor = floor;
        townPortalDungeonX = player.x;
        townPortalDungeonY = player.y;

        int[] ts = town != null ? town.getStart() : new int[]{returnX, returnY};
        townPortalTownX = ts[0];
        townPortalTownY = ts[1];

        // ✅ stamp portal tile on current dungeon floor
        if (dungeon != null && dungeon.inBounds(townPortalDungeonX, townPortalDungeonY)) {
            dungeon.setTile(townPortalDungeonX, townPortalDungeonY, Tile.TOWN_PORTAL);
        }

        setLog("A Town Portal opens.", 2.0);
    }

    private void takeTownPortalToTown() {
        if (!isOnTownPortalInDungeon()) {
            setLog("No portal here.", 2.0);
            return;
        }

        saveCurrentDungeonFloorToCache();

        zone = Zone.TOWN;
        player.x = townPortalTownX;
        player.y = townPortalTownY;

        // ✅ stamp portal tile in town
        if (town != null && town.inBounds(townPortalTownX, townPortalTownY)) {
            town.setTile(townPortalTownX, townPortalTownY, Tile.TOWN_PORTAL);
        }

        setLog("You step through the portal.", 2.0);
        recomputeFov();
        turn.reset();
    }

    private void takeTownPortalBackToDungeon() {
        if (!isOnTownPortalInTown()) {
            setLog("No portal here.", 2.0);
            return;
        }

        // ✅ clear town portal tile before leaving town
        if (town != null && town.inBounds(townPortalTownX, townPortalTownY)) {
            town.setTile(townPortalTownX, townPortalTownY, Tile.PATH); // or GRASS; see note below
        }

        zone = Zone.DUNGEON;
        floor = townPortalFloor;

        hasPendingDungeonSpawn = true;
        pendingDungeonSpawnX = townPortalDungeonX;
        pendingDungeonSpawnY = townPortalDungeonY;

        townPortalActive = false; // closes after returning (D2 behavior)

        generateFloor();

        // ✅ clear dungeon portal tile now that it’s closed
        if (dungeon != null && dungeon.inBounds(townPortalDungeonX, townPortalDungeonY)) {
            dungeon.setTile(townPortalDungeonX, townPortalDungeonY, Tile.FLOOR);
        }

        setLog("The portal closes behind you.", 2.0);
        recomputeFov();
        turn.reset();
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

    private void handleTileAndGroundPickups() {
        // --- Ground items (potions, keys, etc.) ---
        GroundItem gi = getGroundItemAt(player.x, player.y);
        if (gi != null) {
            player.inv.add(gi.type, 1);

            if (gi.type == ItemType.KEY) {
                setLog("Picked up a key. (Keys: " + player.keyCount() + ")", 2.5);
            } else {
                setLog("Picked up " + itemName(gi.type) + "!", 2.5);
            }

            groundItems.remove(gi);
        }
    }

    private void tickPlayerBattleStatusesOnAction() {
        if (battle == null) return;

        if (battle.playerSlowTurns > 0) {
            battle.playerSlowTurns--;
            if (battle.playerSlowTurns <= 0) {
                battle.playerAccuracyPenaltyPct = 0;
                battle.playerDodgePenaltyPct = 0;
            }
        }
    }

    private void tickFoeBattleStatusesOnAction() {
        if (battle == null) return;

        if (battle.foeSlowTurns > 0) {
            battle.foeSlowTurns--;
            if (battle.foeSlowTurns <= 0) {
                battle.foeAccuracyPenaltyPct = 0;
                battle.foeDodgePenaltyPct = 0;
            }
        }
    }

    // Returns true if the foe dodged (so the attack does nothing)
    private boolean foeDodgedAttack() {
        if (battle == null) return false;
        return Battle.rollDodge(
                rng,
                FOE_BASE_DODGE_PCT,
                player.speed(),          // attacker speed
                battle.foe.speed(),      // defender speed
                battle.foeDodgePenaltyPct
        );
    }

    private boolean foeDodgedSlowPoke() {
        if (battle == null) return false;

        // Only dodgable if foe speed is at least 2x player's speed
        if (battle.foe.speed() < player.speed() * 2) return false;

        return Battle.rollDodge(
                rng,
                FOE_BASE_DODGE_PCT,
                player.speed(),       // attacker speed
                battle.foe.speed(),   // defender speed
                battle.foeDodgePenaltyPct
        );
    }

    // Returns true if the player dodged
    private boolean playerDodgedAttack() {
        if (battle == null) return false;
        return Battle.rollDodge(
                rng,
                PLAYER_BASE_DODGE_PCT,
                battle.foe.speed(),      // attacker speed
                player.speed(),          // defender speed
                battle.playerDodgePenaltyPct
        );
    }

    // Call once per rendered frame (after drawing)
    public void onFramePresented() {
        fadeAwaitingPresent = false;
    }

    public int spellCost(Player.SpellType s) {
        return switch (s) {
            case MAGIC_STAB -> 3;
            case SLOW_POKE -> 4;
            case ICE_SHARD -> 5;
            case FLASH_FREEZE -> 6;
            case FIRE_SWORD -> 5;
            case HEAL -> 7;
        };
    }

    // Can this spell be cast outside of battle (from Inventory while in the dungeon)?
    public boolean spellUsableOutsideBattle(Player.SpellType s) {
        return s == Player.SpellType.HEAL;
    }

    // Text for UI (inventory preview box, etc.)
    public String spellUsageText(Player.SpellType s) {
        return spellUsableOutsideBattle(s) ? "Cast in & out of battle" : "Cast in battle only";
    }

    public String spellLabel(Player.SpellType s) {
        int cost = spellCost(s);

        // MAGIC_STAB and SLOW_POKE uses weapon damage + 3..5 (NO INT scaling)
        int wepMin = player.getAtkMinTotal();
        int wepMax = player.getAtkMaxTotal();
        String magicStabRange = (wepMin + 3) + "-" + (wepMax + 5);
        String slowPokeRange = Math.max(1, wepMin / 2) + "-" + Math.max(1, wepMax / 2);

        // New INT-scaled spell damage helpers (your new system)
        int sMin = spellDamageMin(s);
        int sMax = spellDamageMax(s);
        String spellRange = (sMin > 0 && sMax > 0) ? (sMin + "-" + sMax) : "";

        return switch (s) {
            case MAGIC_STAB -> "Magic Stab   (-" + cost + " MP) " + magicStabRange + " dmg";
            case SLOW_POKE -> "Slow Poke   (-" + cost + " MP) " + slowPokeRange + " dmg + slow";
            case ICE_SHARD -> "Ice Shard    (-" + cost + " MP) " + spellRange + " dmg + slow";
            case FLASH_FREEZE -> "Flash Freeze (-" + cost + " MP) " + spellRange + " dmg + freeze";
            case FIRE_SWORD -> "Fire Sword   (-" + cost + " MP) +6-9 bonus dmg";
            case HEAL -> "Heal         (-" + cost + " MP) +15 HP";
        };
    }

    public WorldMap activeMap() {
        return switch (zone) {
            case TOWN -> town;
            case BUILDING -> building; // ok because Dungeon likely has the same methods; if not, see note below
            case DUNGEON -> dungeon;
        };
    }

    private Dungeon activeDungeon() {
        return switch (zone) {
            case BUILDING -> building;
            case DUNGEON -> dungeon;
            case TOWN -> null;
        };
    }

    // Getter for renderer
    public Battle battle() {
        return battle;
    }

    public WorldMap map() {
        return activeMap();
    }

    public Zone zone() {
        return zone;
    }

    // Getters for UI/rendering
    public Dungeon dungeon() {
        return dungeon;
    }

    public Player player() {
        return player;
    }

    public List<Enemy> enemies() {
        return enemies;
    }

    public String lastLog() {
        return lastLog;
    }

    public int floor() {
        return floor;
    }

    public long seed() {
        return seed;
    }

    public State state() {
        return state;
    }

    public float fadeAlpha() {
        return fadeAlpha;
    }

    public boolean isFading() {
        return fadePhase != 0;
    }

    public boolean isDeathWipeActive() {
        return deathWipeActive;
    }

    public float deathWipeProgress() {
        return deathWipeProgress;
    }

    public int invPage() {
        return invPage;
    }

    public int invScroll() {
        return invScroll;
    }

    public int spellIndex() {
        return spellIndex;
    }

    public int spellScroll() {
        return spellScroll;
    }

    public int invVisibleRows() {
        return INV_VISIBLE_ROWS;
    }

}
