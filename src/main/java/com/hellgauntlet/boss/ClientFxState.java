package com.hellgauntlet.boss;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-side state filled by the network handlers: corrupted entity ids and running boss attack effects. */
public final class ClientFxState {
    public static final int ATTACK_LIFE_TICKS = 18;
    private static final int CORRUPTION_MARGIN = 175; // 160 ticks + latency margin; refreshed by every packet

    public record Attack(int type, Vec3 origin, Vec3 dir, long start, long seed) {}

    private static final Map<Integer, Long> CORRUPTED = new HashMap<>();
    public static final List<Attack> ATTACKS = new ArrayList<>();

    private ClientFxState() {}

    public static void setCorrupted(int entityId, boolean active, long now) {
        if (active) {
            CORRUPTED.put(entityId, now + CORRUPTION_MARGIN);
        } else {
            CORRUPTED.remove(entityId);
        }
    }

    public static boolean isCorrupted(int entityId, long now) {
        Long until = CORRUPTED.get(entityId);
        if (until == null) {
            return false;
        }
        if (now > until) {
            CORRUPTED.remove(entityId);
            return false;
        }
        return true;
    }

    public static void addAttack(CorruptionAttackFxPayload p, long now) {
        Vec3 origin = new Vec3(p.ox(), p.oy(), p.oz());
        ATTACKS.add(new Attack(p.attackType(), origin, new Vec3(p.dx(), p.dy(), p.dz()), now,
                Double.doubleToLongBits(p.ox() * 31.0 + p.oz()) ^ now));
    }
}
