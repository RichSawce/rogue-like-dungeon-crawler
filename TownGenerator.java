package org.example.world;

import org.example.game.util.RNG;


import java.util.*;

public final class TownGenerator {
    private final RNG rng;

    public TownGenerator(RNG rng) {
        this.rng = rng;
    }

    // Tune these later
    public Town generate(int w, int h) {
        Town t = new Town(w, h);

        // Optional: boundary wall so the town feels enclosed
        ringWalls(t);

        // Define your “fixed pieces”
        List<Prefab> required = List.of(
                new Prefab(BuildingType.INN,         9, 7, DoorSide.SOUTH),
                new Prefab(BuildingType.WEAPON_SHOP, 7, 6, DoorSide.SOUTH),
                new Prefab(BuildingType.MAGIC_SHOP,  7, 6, DoorSide.SOUTH),
                new Prefab(BuildingType.QUEST_HOUSE_1,6, 5, DoorSide.SOUTH),
                new Prefab(BuildingType.QUEST_HOUSE_2,6, 5, DoorSide.SOUTH),
                new Prefab(BuildingType.CRYPT,       9, 7, DoorSide.SOUTH)
        );

        // Place them: shops/inn more central, crypt pushed toward an edge/corner
        Map<BuildingType, BuildingLot> placed = placeBuildings(t, required);

        // Roads: connect everything to the Inn (hub)
        BuildingLot inn = placed.get(BuildingType.INN);
        if (inn != null) {
            for (BuildingLot b : placed.values()) {
                if (b == inn) continue;
                paintPath(t, inn.doorX, inn.doorY, b.doorX, b.doorY);
            }
        }

        // Mark roads leading off doors a bit (porches)
        for (BuildingLot b : placed.values()) {
            paintPorch(t, b.doorX, b.doorY);
        }

        // Start position: 2 tiles in front of inn door if possible, else the inn door
        int sx = inn != null ? inn.doorX : w / 2;
        int sy = inn != null ? inn.doorY + 2 : h / 2;
        if (!t.isWalkable(sx, sy)) { sx = inn.doorX; sy = inn.doorY; }
        t.setStart(sx, sy);

        return t;
    }

    // -------------------------
    // Prefab definitions
    // -------------------------

    private static final class Prefab {
        final BuildingType type;
        final int w, h;
        final DoorSide doorSide;

        Prefab(BuildingType type, int w, int h, DoorSide doorSide) {
            this.type = type;
            this.w = w; this.h = h;
            this.doorSide = doorSide;
        }
    }

    // -------------------------
    // Building placement
    // -------------------------
    private Map<BuildingType, BuildingLot> placeBuildings(Town t, List<Prefab> prefabs) {
        int w = t.w(), h = t.h();

        Map<BuildingType, BuildingLot> out = new EnumMap<>(BuildingType.class);
        List<Rect> taken = new ArrayList<>();

        // Placement priorities:
        // - Inn near center
        // - Shops near center-ish
        // - Quest houses more spread
        // - Crypt near edge
        List<Prefab> ordered = new ArrayList<>(prefabs);
        ordered.sort(Comparator.comparingInt(p -> priority(p.type)));

        for (Prefab p : ordered) {
            BuildingLot lot = tryPlace(t, taken, p);
            if (lot == null) {
                // fallback: loosen constraints
                lot = tryPlaceBrute(t, taken, p);
            }

            if (lot != null) {
                stampBuilding(t, lot, p.type == BuildingType.CRYPT);
                t.addBuilding(lot);
                out.put(p.type, lot);
                taken.add(expand(lot.bounds(), 2)); // buffer so buildings aren’t kissing
            }
        }

        return out;
    }

    private int priority(BuildingType t) {
        return switch (t) {
            case INN -> 0;
            case WEAPON_SHOP, MAGIC_SHOP -> 1;
            case QUEST_HOUSE_1, QUEST_HOUSE_2 -> 2;
            case CRYPT -> 3;
            case HOUSE -> 4;
        };
    }

    private BuildingLot tryPlace(Town t, List<Rect> taken, Prefab p) {
        int w = t.w(), h = t.h();

        for (int tries = 0; tries < 1200; tries++) {
            int rx, ry;

            if (p.type == BuildingType.INN || p.type == BuildingType.WEAPON_SHOP || p.type == BuildingType.MAGIC_SHOP) {
                // central-ish cluster
                rx = rng.range(w/2 - 10, w/2 + 10);
                ry = rng.range(h/2 - 8,  h/2 + 8);
            } else if (p.type == BuildingType.CRYPT) {
                // edge bias (graveyard corner feel)
                rx = rng.range(3, w - p.w - 4);
                ry = rng.range(3, h - p.h - 4);
                // push toward one side
                if (rng.chance(0.5)) rx = rng.chance(0.5) ? 3 : (w - p.w - 4);
                else ry = rng.chance(0.5) ? 3 : (h - p.h - 4);
            } else {
                // general
                rx = rng.range(3, w - p.w - 4);
                ry = rng.range(3, h - p.h - 4);
            }

            Rect r = new Rect(rx, ry, p.w, p.h);

            if (!fitsInsideTown(t, r)) continue;
            if (intersectsAny(r, taken)) continue;

            int[] door = computeDoor(r, p.doorSide);
            if (!t.inBounds(door[0], door[1])) continue;

            // Ensure door opens to walkable outdoor space (not into a wall/boundary)
            int[] outside = oneStepOutsideDoor(r, door[0], door[1], p.doorSide);
            if (outside == null || !t.inBounds(outside[0], outside[1])) continue;
            if (t.tile(outside[0], outside[1]) == Tile.WALL) continue;

            return new BuildingLot(p.type, rx, ry, p.w, p.h, door[0], door[1], p.doorSide);
        }

        return null;
    }

    private BuildingLot tryPlaceBrute(Town t, List<Rect> taken, Prefab p) {
        int w = t.w(), h = t.h();

        for (int tries = 0; tries < 6000; tries++) {
            int rx = rng.range(2, w - p.w - 3);
            int ry = rng.range(2, h - p.h - 3);

            Rect r = new Rect(rx, ry, p.w, p.h);
            if (!fitsInsideTown(t, r)) continue;
            if (intersectsAny(r, taken)) continue;

            int[] door = computeDoor(r, p.doorSide);
            int[] outside = oneStepOutsideDoor(r, door[0], door[1], p.doorSide);
            if (outside == null || !t.inBounds(outside[0], outside[1])) continue;
            if (t.tile(outside[0], outside[1]) == Tile.WALL) continue;

            return new BuildingLot(p.type, rx, ry, p.w, p.h, door[0], door[1], p.doorSide);
        }

        return null;
    }

    private boolean fitsInsideTown(Town t, Rect r) {
        // keep 1 tile margin from boundary walls
        return r.x >= 2 && r.y >= 2 && r.x2() <= t.w() - 3 && r.y2() <= t.h() - 3;
    }

    private boolean intersectsAny(Rect r, List<Rect> taken) {
        for (Rect o : taken) if (r.intersects(o)) return true;
        return false;
    }

    private Rect expand(Rect r, int pad) {
        return new Rect(r.x - pad, r.y - pad, r.w + pad*2, r.h + pad*2);
    }

    private int[] computeDoor(Rect r, DoorSide side) {
        return switch (side) {
            case NORTH -> new int[]{ r.cx(), r.y };
            case SOUTH -> new int[]{ r.cx(), r.y2() };
            case WEST  -> new int[]{ r.x, r.cy() };
            case EAST  -> new int[]{ r.x2(), r.cy() };
        };
    }

    private int[] oneStepOutsideDoor(Rect r, int doorX, int doorY, DoorSide side) {
        return switch (side) {
            case NORTH -> new int[]{ doorX, doorY - 1 };
            case SOUTH -> new int[]{ doorX, doorY + 1 };
            case WEST  -> new int[]{ doorX - 1, doorY };
            case EAST  -> new int[]{ doorX + 1, doorY };
        };
    }

    private void stampBuilding(Town t, BuildingLot b, boolean isCrypt) {
        // Make the footprint solid wall, then put a door tile
        for (int x = b.x; x < b.x + b.w; x++) {
            for (int y = b.y; y < b.y + b.h; y++) {
                t.setTile(x, y, Tile.WALL);
            }
        }
        t.setTile(b.doorX, b.doorY, isCrypt ? Tile.CRYPT_DOOR : Tile.DOOR);
    }

    // -------------------------
    // Roads: BFS path paint
    // -------------------------
    private void paintPath(Town t, int sx, int sy, int tx, int ty) {
        List<int[]> path = bfsPath(t, sx, sy, tx, ty);
        if (path == null) return;

        for (int[] p : path) {
            int x = p[0], y = p[1];
            Tile cur = t.tile(x, y);

            // Don’t overwrite doors or walls
            if (cur == Tile.WALL || cur == Tile.DOOR || cur == Tile.CRYPT_DOOR) continue;

            // Paint a path
            t.setTile(x, y, Tile.PATH);
        }
    }

    private void paintPorch(Town t, int doorX, int doorY) {
        // Light touch: paint a small plus shape of PATH around doors (only on grass)
        int[][] dirs = {{0,0},{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int x = doorX + d[0], y = doorY + d[1];
            if (!t.inBounds(x, y)) continue;
            if (t.tile(x, y) == Tile.GRASS) t.setTile(x, y, Tile.PATH);
        }
    }

    private List<int[]> bfsPath(Town t, int sx, int sy, int tx, int ty) {
        // BFS over walkable non-wall tiles.
        // Doors are walkable, so it works naturally.
        int w = t.w(), h = t.h();

        int[][] prevX = new int[w][h];
        int[][] prevY = new int[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(prevX[x], Integer.MIN_VALUE);
        for (int x = 0; x < w; x++) Arrays.fill(prevY[x], Integer.MIN_VALUE);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy});
        prevX[sx][sy] = sx;
        prevY[sx][sy] = sy;

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int x = cur[0], y = cur[1];

            if (x == tx && y == ty) break;

            for (int[] d : dirs) {
                int nx = x + d[0], ny = y + d[1];
                if (!t.inBounds(nx, ny)) continue;

                if (prevX[nx][ny] != Integer.MIN_VALUE) continue; // visited
                if (t.tile(nx, ny) == Tile.WALL) continue;

                prevX[nx][ny] = x;
                prevY[nx][ny] = y;
                q.addLast(new int[]{nx, ny});
            }
        }

        if (prevX[tx][ty] == Integer.MIN_VALUE) return null;

        // Reconstruct
        List<int[]> path = new ArrayList<>();
        int cx = tx, cy = ty;
        while (!(cx == sx && cy == sy)) {
            path.add(new int[]{cx, cy});
            int px = prevX[cx][cy];
            int py = prevY[cx][cy];
            cx = px; cy = py;
        }
        path.add(new int[]{sx, sy});
        Collections.reverse(path);
        return path;
    }

    // -------------------------
    // Town boundary ring walls
    // -------------------------
    private void ringWalls(Town t) {
        int w = t.w(), h = t.h();

        for (int x = 0; x < w; x++) {
            t.setTile(x, 0, Tile.WALL);
            t.setTile(x, h - 1, Tile.WALL);
        }
        for (int y = 0; y < h; y++) {
            t.setTile(0, y, Tile.WALL);
            t.setTile(w - 1, y, Tile.WALL);
        }
    }
}