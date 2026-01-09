package org.example.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Dungeon implements WorldMap {
    private final int w, h;
    private final Tile[][] tiles;

    private final List<Rect> rooms = new ArrayList<>();


    // Fog-of-war
    private final boolean[][] visibleNow;
    private final boolean[][] seenEver;
    // Fog-of-war enabled
    private boolean fogEnabled = true;

    private int startX, startY;
    private int stairsX, stairsY;

    private int keyX = -1, keyY = -1;
    private int lockedDoorX = -1, lockedDoorY = -1;

    public Dungeon(int w, int h) {
        this.w = w; this.h = h;
        tiles = new Tile[w][h];
        visibleNow = new boolean[w][h];
        seenEver = new boolean[w][h];

        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                tiles[x][y] = Tile.WALL;
    }

    public int w() { return w; }
    public int h() { return h; }

    public Tile tile(int x, int y) {
        if (!inBounds(x, y)) return Tile.WALL;
        return tiles[x][y];
    }

    public void setTile(int x, int y, Tile t) {
        if (inBounds(x, y)) tiles[x][y] = t;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && tiles[x][y].walkable;
    }

    public boolean blocksSight(int x, int y) {
        if (!inBounds(x, y)) return true;
        return tiles[x][y] == Tile.WALL;
    }

    public void setKey(int x, int y) {
        keyX = x; keyY = y;
        setTile(x, y, Tile.KEY);
    }

    public boolean hasKeyPlaced() {
        return keyX >= 0 && keyY >= 0;
    }

    public int[] getKeyPos() {
        return new int[]{keyX, keyY};
    }

    public boolean isKeyAt(int x, int y) {
        return x == keyX && y == keyY;
    }

    public void clearKeyTile() {
        if (inBounds(keyX, keyY) && tile(keyX, keyY) == Tile.KEY) {
            setTile(keyX, keyY, Tile.FLOOR);
        }
        keyX = -1; keyY = -1;
    }

    public void setLockedDoor(int x, int y) {
        lockedDoorX = x; lockedDoorY = y;
        setTile(x, y, Tile.LOCKED_DOOR);
    }

    public int[] getLockedDoorPos() {
        return new int[]{lockedDoorX, lockedDoorY};
    }

    public boolean isLockedDoorAt(int x, int y) {
        return x == lockedDoorX && y == lockedDoorY;
    }

    // ---- Stairs room bounds (so we can avoid putting the key chest inside it) ----
    private int stairsRoomX1 = -1, stairsRoomY1 = -1, stairsRoomX2 = -1, stairsRoomY2 = -1;

    public void setStairsRoomBounds(int x, int y, int w, int h) {
        this.stairsRoomX1 = x;
        this.stairsRoomY1 = y;
        this.stairsRoomX2 = x + w - 1;
        this.stairsRoomY2 = y + h - 1;
    }

    public boolean hasStairsRoomBounds() {
        return stairsRoomX1 >= 0;
    }

    public boolean isInsideStairsRoom(int x, int y) {
        if (!hasStairsRoomBounds()) return false;
        return x >= stairsRoomX1 && x <= stairsRoomX2 && y >= stairsRoomY1 && y <= stairsRoomY2;
    }

    public void addRoom(Rect r) {
        rooms.add(r);
    }

    public List<Rect> rooms() {
        return Collections.unmodifiableList(rooms);
    }

    /**
     * Returns a random FLOOR tile that is guaranteed to be inside a room.
     * If excludeStairsRoom=true, it will never pick a tile inside the stairs room bounds.
     */
    public int[] findRandomRoomFloor(org.example.game.util.RNG rng, boolean excludeStairsRoom) {
        if (rooms.isEmpty()) return findRandomFloor(rng); // fallback safety

        for (int tries = 0; tries < 5000; tries++) {
            Rect r = rooms.get(rng.nextInt(rooms.size()));

            int x = rng.range(r.x, r.x2());
            int y = rng.range(r.y, r.y2());

            if (tile(x, y) != Tile.FLOOR) continue;
            if (excludeStairsRoom && isInsideStairsRoom(x, y)) continue;

            return new int[]{x, y};
        }

        // fallback if something weird happens
        return findRandomFloor(rng);
    }

    public void setStart(int x, int y) { startX = x; startY = y; }
    public int[] getStart() { return new int[]{startX, startY}; }

    public void setStairsDown(int x, int y) { stairsX = x; stairsY = y; setTile(x, y, Tile.STAIRS_DOWN); }
    public boolean isStairsDown(int x, int y) { return x == stairsX && y == stairsY; }

    // Visibility
    public boolean isVisibleNow(int x, int y) {
        if (!fogEnabled) return inBounds(x, y);
        return inBounds(x,y) && visibleNow[x][y];
    }

    public boolean wasSeenEver(int x, int y) {
        if (!fogEnabled) return inBounds(x, y);
        return inBounds(x,y) && seenEver[x][y];
    }

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;

        if (!enabled) {
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++) {
                    visibleNow[x][y] = true;
                    seenEver[x][y] = true;
                }
        }
    }

    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void clearVisibilityNow() {
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                visibleNow[x][y] = false;
    }

    public void markVisible(int x, int y) {
        if (!inBounds(x, y)) return;
        visibleNow[x][y] = true;
        seenEver[x][y] = true;
    }

    public void applyVisibilityFromFov(Fov f) {
        if (!fogEnabled) return;
        clearVisibilityNow();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (f.visible(x, y)) markVisible(x, y);
    }
    public int[] findRandomFloor(org.example.game.util.RNG rng) {
        while (true) {
            int x = rng.range(1, w - 2);
            int y = rng.range(1, h - 2);
            if (tile(x, y) == Tile.FLOOR) return new int[]{x, y};
        }
    }
}