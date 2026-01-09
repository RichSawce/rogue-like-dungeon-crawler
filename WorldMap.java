package org.example.world;


public interface WorldMap {
    int w();
    int h();

    boolean inBounds(int x, int y);
    boolean isWalkable(int x, int y);
    boolean blocksSight(int x, int y);
    boolean isVisibleNow(int x, int y);
    boolean wasSeenEver(int x, int y);

    Tile tile(int x, int y);
}