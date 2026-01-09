package org.example.world;

public final class BuildingLot {
    public final BuildingType type;
    public final int x, y;
    public final int w, h;
    public final int doorX, doorY;
    public final DoorSide doorSide;

    // NEW: cached interior generated once per game run
    private Dungeon interior;

    public Dungeon getInterior() { return interior; }
    public void setInterior(Dungeon d) { this.interior = d; }
    public boolean hasInterior() { return interior != null; }


    public BuildingLot(BuildingType type, int x, int y, int w, int h,
                       int doorX, int doorY, DoorSide doorSide) {
        this.type = type;
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.doorX = doorX; this.doorY = doorY;
        this.doorSide = doorSide;
    }

    public Rect bounds() {
        return new Rect(x, y, w, h);
    }


}