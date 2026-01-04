package org.example.entity;

public abstract class Actor {
    public int x, y;

    public int hp, maxHp;
    public int mp, maxMp;

    public int atkMin, atkMax;
    public String name;

    // Full constructor (HP + MP)
    protected Actor(String name, int x, int y, int maxHp, int maxMp, int atkMin, int atkMax) {
        this.name = name;
        this.x = x; this.y = y;
        this.maxHp = maxHp;
        this.hp = maxHp;

        this.maxMp = Math.max(0, maxMp);
        this.mp = this.maxMp;

        this.atkMin = atkMin;
        this.atkMax = atkMax;
    }

    // Backwards-compatible constructor (HP only; MP = 0)
    protected Actor(String name, int x, int y, int maxHp, int atkMin, int atkMax) {
        this(name, x, y, maxHp, 0, atkMin, atkMax);
    }
}