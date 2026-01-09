package org.example.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Town implements WorldMap {
    private final int w, h;
    private final Tile[][] tiles;

    private final boolean[][] visibleNow;
    private final boolean[][] seenEver;
    private boolean fogEnabled = false;

    private int startX, startY;

    private int cryptDoorX = -1, cryptDoorY = -1;


    private final List<BuildingLot> buildings = new ArrayList<>();

    public Town(int w, int h) {
        this.w = w; this.h = h;
        tiles = new Tile[w][h];
        visibleNow = new boolean[w][h];
        seenEver = new boolean[w][h];

        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                tiles[x][y] = Tile.GRASS;
    }

    @Override public int w() { return w; }
    @Override public int h() { return h; }

    @Override
    public Tile tile(int x, int y) {
        if (!inBounds(x, y)) return Tile.WALL;
        return tiles[x][y];
    }

    public void setTile(int x, int y, Tile t) {
        if (inBounds(x, y)) tiles[x][y] = t;
    }

    @Override
    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    @Override
    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && tiles[x][y].walkable;
    }

    @Override
    public boolean blocksSight(int x, int y) {
        if (!inBounds(x, y)) return true;
        return tiles[x][y] == Tile.WALL;
    }

    public void setStart(int x, int y) { startX = x; startY = y; }
    public int[] getStart() { return new int[]{startX, startY}; }

    public void addBuilding(BuildingLot b) {
        buildings.add(b);
        if (b.type == BuildingType.CRYPT) {
            cryptDoorX = b.doorX;
            cryptDoorY = b.doorY;
        }
    }

    public BuildingLot buildingAtDoor(int x, int y) {
        for (BuildingLot b : buildings) {
            if (b.doorX == x && b.doorY == y) return b;
        }
        return null;
    }

    public List<BuildingLot> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public boolean isCryptDoor(int x, int y) {
        return x == cryptDoorX && y == cryptDoorY;
    }

    public int[] getCryptDoorPos() {
        return new int[]{cryptDoorX, cryptDoorY};
    }

    public int[] outsideOfDoor(BuildingLot b) {
        return switch (b.doorSide) {
            case NORTH -> new int[]{ b.doorX, b.doorY - 1 };
            case SOUTH -> new int[]{ b.doorX, b.doorY + 1 };
            case WEST  -> new int[]{ b.doorX - 1, b.doorY };
            case EAST  -> new int[]{ b.doorX + 1, b.doorY };
        };
    }

    // Visibility (same pattern as Dungeon)
    public boolean isVisibleNow(int x, int y) {
        if (!fogEnabled) return inBounds(x, y);
        return inBounds(x,y) && visibleNow[x][y];
    }

    public boolean wasSeenEver(int x, int y) {
        if (!fogEnabled) return inBounds(x, y);
        return inBounds(x,y) && seenEver[x][y];
    }

    public void applyVisibilityFromFov(Fov f) {
        if (!fogEnabled) return;
        clearVisibilityNow();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (f.visible(x, y)) markVisible(x, y);
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
}