package org.example.game;

public final class GameConfig {
    private GameConfig() {}

    // Viewport size in tiles (what the window shows)
    public static final int MAP_W = 60;
    public static final int MAP_H = 38;

    // Dungeon/world size in tiles (must be >= viewport; bigger enables camera scrolling)
    public static final int DUNGEON_W = MAP_W * 2;  // 120
    public static final int DUNGEON_H = MAP_H * 2;  // 76

    // Dungeon gen
    public static final int ROOM_MIN = 5;
    public static final int ROOM_MAX = 11;
    public static final int MAX_ROOMS = 12;

    // Rendering
    public static final int TILE_SIZE = 8;    // logical pixels (tile art is 8x8)
    public static final int SCALE = 5;        // window pixel scaling
    public static final int UI_H_TILES = 4;   // bottom UI height in tiles

    // Gameplay
    public static final int PLAYER_FOV_RADIUS = 10;
    public static final int START_ENEMIES_MIN = 6;
    public static final int START_ENEMIES_MAX = 10;

    public static final int VIEW_W_TILES = MAP_W;
    public static final int VIEW_H_TILES = MAP_H; // map-only (no UI)

    public static final int WINDOW_W_PX = VIEW_W_TILES * TILE_SIZE * SCALE;
    public static final int WINDOW_H_PX = (VIEW_H_TILES + UI_H_TILES) * TILE_SIZE * SCALE;
}