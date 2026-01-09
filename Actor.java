package org.example.entity;

public abstract class Actor {
    public int x, y;

    public int hp, maxHp;
    public int mp, maxMp;

    public int atkMin, atkMax;
    public String name;

    // Core stats (used by your Battle math)
    protected int speed;
    protected int intelligence;
    protected int will;

    // Full constructor (HP + MP + core stats)
    protected Actor(String name, int x, int y,
                    int maxHp, int maxMp,
                    int atkMin, int atkMax,
                    int speed, int intelligence, int will) {

        this.name = name;
        this.x = x;
        this.y = y;

        this.maxHp = Math.max(1, maxHp);
        this.hp = this.maxHp;

        this.maxMp = Math.max(0, maxMp);
        this.mp = this.maxMp;

        // keep ATK sane
        this.atkMin = atkMin;
        this.atkMax = Math.max(atkMin, atkMax);

        // core stats
        this.speed = Math.max(0, speed);
        this.intelligence = Math.max(0, intelligence);
        this.will = Math.max(0, will);
    }

    // Backwards-compatible: HP only, with core stats (MP = 0)
    protected Actor(String name, int x, int y,
                    int maxHp,
                    int atkMin, int atkMax,
                    int speed, int intelligence, int will) {
        this(name, x, y, maxHp, 0, atkMin, atkMax, speed, intelligence, will);
    }

    // Backwards-compatible: HP + MP, no core stats (defaults)
    protected Actor(String name, int x, int y,
                    int maxHp, int maxMp,
                    int atkMin, int atkMax) {
        this(name, x, y, maxHp, maxMp, atkMin, atkMax, 10, 10, 10);
    }

    // Backwards-compatible: HP only, no core stats (MP=0, defaults)
    protected Actor(String name, int x, int y,
                    int maxHp,
                    int atkMin, int atkMax) {
        this(name, x, y, maxHp, 0, atkMin, atkMax, 10, 10, 10);
    }

    // Getters used all over your battle logic
    public int speed() { return speed; }
    public int intelligence() { return intelligence; }
    public int will() { return will; }
}