package org.example.entity;

public enum PhysicalMove {
    // Universal moves (learned via level/tomes)
    SLASH,      // Reliable, standard damage
    SMASH,      // High damage, lower accuracy, scales with speed
    LUNGE,      // Crit on hit, lose turn on miss
    PARRY,      // Defensive move, blocks next attack
    SWEEP       // Lower damage but can't be dodged
}