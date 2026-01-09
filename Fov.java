package org.example.world;

public final class Fov {
    private boolean[][] vis;

    public void compute(WorldMap m, int ox, int oy, int radius) {
        int w = m.w(), h = m.h();
        vis = new boolean[w][h];

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int y = oy - radius; y <= oy + radius; y++) {
                if (!m.inBounds(x, y)) continue;
                int dx = x - ox, dy = y - oy;
                if (dx*dx + dy*dy > radius*radius) continue;

                if (hasLineOfSight(m, ox, oy, x, y)) {
                    vis[x][y] = true;
                }
            }
        }

        if (m.inBounds(ox, oy)) vis[ox][oy] = true;
    }

    public boolean visible(int x, int y) {
        if (vis == null) return false;
        if (x < 0 || y < 0 || x >= vis.length || y >= vis[0].length) return false;
        return vis[x][y];
    }

    private boolean hasLineOfSight(WorldMap m, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int x = x0, y = y0;

        while (true) {
            if (x == x1 && y == y1) return true;

            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx)  { err += dx; y += sy; }

            if (m.blocksSight(x, y)) {
                return (x == x1 && y == y1);
            }
        }
    }
}