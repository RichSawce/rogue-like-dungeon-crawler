package org.example.entity;


public abstract class Actor {
    public int x, y;
    public int hp, maxHp;
    public int atkMin, atkMax;
    public String name;

    protected Actor(String name, int x, int y, int maxHp, int atkMin, int atkMax) {
        this.name = name;
        this.x = x; this.y = y;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.atkMin = atkMin;
        this.atkMax = atkMax;
    }
}