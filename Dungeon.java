package org.example.world;

public final class Dungeon {
    private final int w, h;
    private final Tile[][] tiles;

    // Fog-of-war
    private final boolean[][] visibleNow;
    private final boolean[][] seenEver;

    private int startX, startY;
    private int stairsX, stairsY;

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

    public void setStart(int x, int y) { startX = x; startY = y; }
    public int[] getStart() { return new int[]{startX, startY}; }

    public void setStairsDown(int x, int y) { stairsX = x; stairsY = y; setTile(x, y, Tile.STAIRS_DOWN); }
    public boolean isStairsDown(int x, int y) { return x == stairsX && y == stairsY; }

    // Visibility
    public boolean isVisibleNow(int x, int y) { return inBounds(x,y) && visibleNow[x][y]; }
    public boolean wasSeenEver(int x, int y) { return inBounds(x,y) && seenEver[x][y]; }

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