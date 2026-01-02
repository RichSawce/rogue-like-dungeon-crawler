package org.example.world;

public final class Rect {
    public final int x, y, w, h;

    public Rect(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public int x2() { return x + w - 1; }
    public int y2() { return y + h - 1; }

    public int cx() { return x + w / 2; }
    public int cy() { return y + h / 2; }

    public boolean intersects(Rect other) {
        return this.x <= other.x2() && this.x2() >= other.x
                && this.y <= other.y2() && this.y2() >= other.y;
    }
}