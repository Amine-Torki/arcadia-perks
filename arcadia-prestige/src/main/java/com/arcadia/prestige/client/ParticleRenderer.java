package com.arcadia.prestige.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Client-side particle renderer for cosmetic effects.
 *
 * Effects:
 *   STATIC  — always active (Orbit, Aura, Wings, Storm, Crown,
 *                            Snow, Void, Dragon, Helix, Meteor)
 *   MOVEMENT — only emit when the player is moving
 *              (Trail, Hearts, Enchant, Flame, Stars, Bubble, Sakura, Rainbow)
 */
@EventBusSubscriber(modid = "arcadia_prestige", value = Dist.CLIENT)
public final class ParticleRenderer {

    private static final int    PARTICLE_MAX_VISIBLE = 10;
    private static final double MAX_DIST_SQ          = 900.0;
    private static final double REDUCED_DIST_SQ      = 100.0;
    private static final double MOVE_THRESHOLD_SQ    = 0.0008;

    private static final Set<String> MOVEMENT_EFFECTS =
            Set.of("trail", "hearts", "enchant", "flame", "stars", "bubble", "sakura", "rainbow",
                   "ghost", "shockwave");

    /** Shared Random — no need for determinism on visual-only particle jitter. */
    private static final Random RAND = new Random();

    // Pre-allocated DustParticleOptions for effects with fixed colours
    private static final DustParticleOptions CROWN_GOLD   = new DustParticleOptions(new Vector3f(1.00f, 0.82f, 0.00f), 1.0f);
    private static final DustParticleOptions CROWN_BRIGHT = new DustParticleOptions(new Vector3f(1.00f, 0.96f, 0.55f), 0.7f);
    private static final DustParticleOptions WINGS_SPARKLE = new DustParticleOptions(new Vector3f(0.85f, 0.75f, 1.0f), 0.5f);

    // Wings gradient: pre-computed for pts=7 (full) and pts=4 (reduced) to avoid per-call allocations
    private static final DustParticleOptions[] WINGS_DUST_FULL = new DustParticleOptions[7];
    private static final DustParticleOptions[] WINGS_DUST_HALF = new DustParticleOptions[4];
    static {
        for (int i = 0; i < 7; i++) {
            float t = (float) i / 6;
            WINGS_DUST_FULL[i] = new DustParticleOptions(new Vector3f(t * 0.85f, t * 0.22f, 1.0f), 0.75f - t * 0.15f);
        }
        for (int i = 0; i < 4; i++) {
            float t = (float) i / 3;
            WINGS_DUST_HALF[i] = new DustParticleOptions(new Vector3f(t * 0.85f, t * 0.22f, 1.0f), 0.75f - t * 0.15f);
        }
    }

    // Wings arc parameters: static to avoid per-call array allocation
    // { spreadScale, backScale, heightOffset, flutterPhase, flutterAmp }
    private static final double[][] WINGS_ARCS = {
        { 1.00, 1.00,  0.00, 0.0, 0.14 },
        { 0.75, 0.80,  0.30, 1.5, 0.18 },
        { 0.58, 0.54, -0.18, 3.0, 0.22 },
    };

    private static int tickCount = 0;

    private static final Map<UUID, List<Vec3>> trailPositions = new HashMap<>();
    private static final Map<UUID, Vec3>       lastPositions  = new HashMap<>();

    private ParticleRenderer() {}

    // =========================================================================
    // Main loop
    // =========================================================================

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ClientLevel)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        tickCount++;
        if (tickCount % 2 != 0) return; // ~10 updates/second

        ClientLevel level      = mc.level;
        Player      localPlayer = mc.player;
        Vec3        localPos    = localPlayer.position();

        Map<UUID, String> allEffects = PlayerEffectCache.getAll();
        if (allEffects.isEmpty()) return;

        // Pre-compute distances once — avoids O(n log n) getPlayerByUUID calls inside comparator
        Map<UUID, Double> distCache = new HashMap<>();
        for (UUID id : allEffects.keySet()) {
            Player p = level.getPlayerByUUID(id);
            distCache.put(id, p != null ? p.distanceToSqr(localPos) : Double.MAX_VALUE);
        }
        List<Map.Entry<UUID, String>> sorted = new ArrayList<>(allEffects.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> distCache.getOrDefault(e.getKey(), Double.MAX_VALUE)));

        boolean firstPerson  = mc.options.getCameraType() == CameraType.FIRST_PERSON;
        boolean hideOwnInFP  = PlayerEffectCache.isHideOwnEffectsFirstPerson();

        int rendered = 0;
        for (Map.Entry<UUID, String> entry : sorted) {
            if (rendered >= PARTICLE_MAX_VISIBLE) break;

            Player target = level.getPlayerByUUID(entry.getKey());
            if (target == null) continue;

            boolean isLocal = target.getUUID().equals(localPlayer.getUUID());
            if (isLocal && hideOwnInFP && firstPerson) continue;

            double distSq = target.distanceToSqr(localPlayer);
            if (distSq > MAX_DIST_SQ) continue;

            UUID uuid       = target.getUUID();
            Vec3 currentPos = target.position();
            Vec3 prevPos    = lastPositions.getOrDefault(uuid, currentPos);
            boolean moving  = currentPos.distanceToSqr(prevPos) > MOVE_THRESHOLD_SQ;
            lastPositions.put(uuid, currentPos);

            float density = distSq > REDUCED_DIST_SQ ? 0.5f : 1.0f;
            renderEffect(level, target, entry.getValue(), tickCount, density, moving);
            rendered++;
        }
    }

    private static void renderEffect(ClientLevel level, Player target, String effectId,
                                     int tick, float density, boolean moving) {
        switch (effectId.toLowerCase(Locale.ROOT)) {
            // ── Static ───────────────────────────────────────────────────────
            case "orbit"  -> renderOrbit(level, target, tick, density);
            case "aura"   -> renderAura(level, target, tick, density);
            case "wings"  -> renderWings(level, target, tick, density);
            case "storm"  -> renderStorm(level, target, tick, density);
            case "platform" -> renderCrown(level, target, tick, density);
            case "comet"    -> renderComet(level, target, tick, density);
            case "pulsar"   -> renderPulsar(level, target, tick, density);
            case "binary"   -> renderBinary(level, target, tick, density);
            case "nova"     -> renderNova(level, target, tick, density);
            case "galaxy"   -> renderGalaxy(level, target, tick, density);
            case "snow"   -> renderSnow(level, target, tick, density);
            case "void"   -> renderVoid(level, target, tick, density);
            case "dragon" -> renderDragon(level, target, tick, density);
            case "helix"  -> renderHelix(level, target, tick, density);
            case "meteor" -> renderMeteor(level, target, tick, density);
            // ── Movement ─────────────────────────────────────────────────────
            case "trail"   -> { if (moving) renderTrail(level, target, tick, density); }
            case "hearts"  -> { if (moving) renderHearts(level, target, tick, density); }
            case "enchant" -> { if (moving) renderEnchant(level, target, tick, density); }
            case "flame"   -> { if (moving) renderFlame(level, target, tick, density); }
            case "stars"   -> { if (moving) renderStars(level, target, tick, density); }
            case "bubble"  -> { if (moving) renderBubble(level, target, tick, density); }
            case "sakura"  -> { if (moving) renderSakura(level, target, tick, density); }
            case "rainbow"   -> { if (moving) renderRainbow(level, target, tick, density); }
            case "ghost"     -> { if (moving) renderGhost(level, target, tick, density); }
            case "shockwave" -> { if (moving) renderShockwave(level, target, tick, density); }
        }
    }

    // =========================================================================
    // STATIC EFFECTS
    // =========================================================================

    // ── Orbit (VIP) ── three-ring orrery with inclined orbit ─────────────────
    private static void renderOrbit(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();

        // Ring 1 — equatorial, 4 particles, fast
        double a1 = tick * 0.15;
        int n1 = density < 1f ? 2 : 4;
        for (int i = 0; i < n1; i++) {
            double a = a1 + i * (2.0 * Math.PI / n1);
            level.addParticle(ParticleTypes.END_ROD,
                    px + Math.cos(a) * 1.2, py + 0.8, pz + Math.sin(a) * 1.2, 0, 0, 0);
        }

        // Ring 2 — elevated, counter-rotating, 3 particles
        double a2 = -tick * 0.10;
        int n2 = density < 1f ? 1 : 3;
        for (int i = 0; i < n2; i++) {
            double a = a2 + i * (2.0 * Math.PI / n2);
            level.addParticle(ParticleTypes.END_ROD,
                    px + Math.cos(a) * 0.9, py + 1.45, pz + Math.sin(a) * 0.9, 0, 0, 0);
        }

        // Ring 3 — inclined orbit (compressed z → tilted plane illusion)
        if (density >= 1f) {
            double a3 = tick * 0.08;
            for (int i = 0; i < 2; i++) {
                double a = a3 + i * Math.PI;
                level.addParticle(ParticleTypes.END_ROD,
                        px + Math.cos(a) * 1.35,
                        py + 1.1 + Math.sin(a) * 0.55,
                        pz + Math.sin(a) * 0.40, 0, 0, 0);
            }
        }

        // Sparkle flash on ring 1
        if (tick % 8 == 0) {
            double sa = tick * 0.15;
            level.addParticle(ParticleTypes.CRIT,
                    px + Math.cos(sa) * 1.2, py + 0.8, pz + Math.sin(sa) * 1.2, 0, 0.07, 0);
        }
    }

    // ── Aura (VIP+) ── soul-fire tornado corkscrew ───────────────────────────
    private static void renderAura(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 0.08, pz = target.getZ();

        // Rising corkscrew column
        int steps = density < 1f ? 5 : 10;
        for (int i = 0; i < steps; i++) {
            double t     = (double) i / steps;
            double angle = tick * 0.22 + t * Math.PI * 3.5;
            double r     = 0.28 + t * 0.20;
            double y     = py + t * 2.3;
            double x     = px + Math.cos(angle) * r;
            double z     = pz + Math.sin(angle) * r;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.016 + t * 0.010, 0);
        }

        // Rotating ground ring of embers
        if (tick % 3 == 0) {
            int n = density < 1f ? 5 : 10;
            double baseA = tick * 0.09;
            for (int i = 0; i < n; i++) {
                double a = baseA + (2.0 * Math.PI * i / n);
                double r = 0.55 + Math.sin(tick * 0.14 + i * 0.7) * 0.15;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        px + Math.cos(a) * r, py, pz + Math.sin(a) * r,
                        Math.cos(a) * 0.016, 0.022, Math.sin(a) * 0.016);
            }
        }

        // Outward burst every 20 ticks
        if (tick % 20 == 0) {
            Random rand = RAND;
            int n = density < 1f ? 6 : 14;
            for (int i = 0; i < n; i++) {
                double a = rand.nextDouble() * Math.PI * 2;
                double r = rand.nextDouble() * 1.1;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        px + Math.cos(a) * r, py + rand.nextDouble() * 1.8, pz + Math.sin(a) * r,
                        Math.cos(a) * 0.055, 0.065, Math.sin(a) * 0.055);
            }
        }
    }

    // ── Wings (VIP+) ── three-arc feathered wings with blue→white gradient ────
    private static void renderWings(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        float  yaw = (float) Math.toRadians(target.getYRot());
        double cY  = Math.cos(yaw), sY = Math.sin(yaw);
        int    pts = density < 1f ? 4 : 7;

        DustParticleOptions[] dustTable = pts == 7 ? WINGS_DUST_FULL : WINGS_DUST_HALF;

        for (int wing = -1; wing <= 1; wing += 2) {
            for (double[] arc : WINGS_ARCS) {
                double spreadS = arc[0] * wing, backS = arc[1], hOff = arc[2];
                float flutter  = 1.0f + (float) Math.sin(tick * arc[4] + arc[3]) * 0.13f;

                for (int i = 0; i < pts; i++) {
                    float  t      = (float) i / (pts - 1);
                    double wingY  = Math.sin(t * Math.PI) * 1.15 * arc[0] + py + 0.9 + hOff;
                    double spread = t * 0.88 * flutter * spreadS;
                    double back   = -(0.18 + t * 0.52) * backS;
                    level.addParticle(dustTable[i],
                            px + spread * cY - back * sY, wingY, pz + spread * sY + back * cY,
                            0, 0, 0);
                }
            }
        }

        // Wingtip sparkle every 5 ticks
        if (tick % 5 == 0) {
            DustParticleOptions sparkle = WINGS_SPARKLE;
            for (int wing = -1; wing <= 1; wing += 2) {
                level.addParticle(sparkle,
                        px + (0.88 * wing) * cY - (-0.70) * sY,
                        py + 1.15,
                        pz + (0.88 * wing) * sY + (-0.70) * cY, 0, 0.02, 0);
            }
        }
    }

    // ── Storm (Founder) ── electric tempest with thunder flash ───────────────
    private static void renderStorm(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        Random rand = RAND;

        // Descending electric helix
        int pts = density < 1f ? 5 : 10;
        for (int i = 0; i < pts; i++) {
            double t = (double) i / pts;
            double y = py + 3.2 - t * 3.0;
            double r = 1.4 - t * 1.1;
            double a = tick * 0.28 + i * (Math.PI / 5.0);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    px + Math.cos(a) * r + (rand.nextDouble() - 0.5) * 0.10,
                    y,
                    pz + Math.sin(a) * r + (rand.nextDouble() - 0.5) * 0.10,
                    0, -0.08, 0);
        }

        // Ground ring of sparks
        if (tick % 4 == 0) {
            int n = density < 1f ? 4 : 8;
            for (int i = 0; i < n; i++) {
                double a = tick * 0.15 + i * (2.0 * Math.PI / n);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        px + Math.cos(a) * 1.2, py + 0.05, pz + Math.sin(a) * 1.2,
                        Math.cos(a + Math.PI) * 0.04, 0.025, Math.sin(a + Math.PI) * 0.04);
            }
        }

        // Ambient sparks
        if (tick % 2 == 0) {
            int n = density < 1f ? 2 : 4;
            for (int i = 0; i < n; i++) {
                double ox = (rand.nextDouble() - 0.5) * 1.8;
                double oz = (rand.nextDouble() - 0.5) * 1.8;
                level.addParticle(ParticleTypes.CRIT,
                        px + ox, py + rand.nextDouble() * 2.8, pz + oz,
                        (rand.nextDouble() - 0.5) * 0.12, 0.04, (rand.nextDouble() - 0.5) * 0.12);
            }
        }

        // Thunder flash every 40 ticks
        if (tick % 40 < 3) {
            int n = density < 1f ? 8 : 20;
            Random flashRand = RAND;
            for (int i = 0; i < n; i++) {
                double a = flashRand.nextDouble() * Math.PI * 2;
                double r = flashRand.nextDouble() * 1.6;
                level.addParticle(ParticleTypes.CRIT,
                        px + Math.cos(a) * r, py + flashRand.nextDouble() * 2.5, pz + Math.sin(a) * r,
                        (flashRand.nextDouble() - 0.5) * 0.5, 0.18,
                        (flashRand.nextDouble() - 0.5) * 0.5);
            }
        }
    }

    // ── Crown (Founder) ── golden platform beneath the player's feet ──────────
    private static void renderCrown(ClientLevel level, Player target, int tick, float density) {
        double px  = target.getX(), pz = target.getZ();
        double py  = target.getY() - 0.08; // just below feet
        double rot = tick * 0.035;

        DustParticleOptions gold   = CROWN_GOLD;
        DustParticleOptions bright = CROWN_BRIGHT;

        // Three concentric rings forming the platform surface
        double[] radii = { 0.28, 0.60, 0.95 };
        int[]    counts = density < 1f ? new int[]{4, 8, 14} : new int[]{6, 12, 20};
        for (int ri = 0; ri < radii.length; ri++) {
            double r  = radii[ri];
            int    n  = counts[ri];
            double ra = rot * (ri % 2 == 0 ? 1.0 : -1.3); // alternate CW/CCW per ring
            for (int i = 0; i < n; i++) {
                double a = ra + (2.0 * Math.PI * i / n);
                level.addParticle(gold, px + Math.cos(a) * r, py, pz + Math.sin(a) * r, 0, 0, 0);
            }
        }

        // Outer glowing rim with bright shimmer
        int rimN = density < 1f ? 10 : 18;
        double rimRot = -rot * 0.8;
        for (int i = 0; i < rimN; i++) {
            double a = rimRot + (2.0 * Math.PI * i / rimN);
            double pulse = 0.95 + Math.sin(tick * 0.12 + i * 0.7) * 0.06;
            level.addParticle(bright, px + Math.cos(a) * pulse, py + 0.02, pz + Math.sin(a) * pulse, 0, 0, 0);
        }

        // Rising gold sparkles from platform edge every 5 ticks
        if (tick % 5 == 0) {
            int sn = density < 1f ? 2 : 4;
            for (int i = 0; i < sn; i++) {
                double a = rot * 3.0 + i * (Math.PI * 2.0 / sn);
                level.addParticle(bright,
                        px + Math.cos(a) * 0.95, py,
                        pz + Math.sin(a) * 0.95, 0, 0.025, 0);
            }
        }

        // Crit sparkle burst at the center every 20 ticks
        if (tick % 20 == 0) {
            int cn = density < 1f ? 4 : 8;
            for (int i = 0; i < cn; i++) {
                double a = Math.PI * 2.0 * i / cn;
                level.addParticle(ParticleTypes.CRIT,
                        px + Math.cos(a) * 0.3, py + 0.05, pz + Math.sin(a) * 0.3,
                        Math.cos(a) * 0.06, 0.08, Math.sin(a) * 0.06);
            }
        }
    }

    // ── Snow (VIP) ── winter vortex: spiral snowfall + ice crystal bursts ─────
    private static void renderSnow(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        Random rand = RAND;

        // Descending spiral
        int spiralN = density < 1f ? 3 : 6;
        for (int i = 0; i < spiralN; i++) {
            double t = (double) i / spiralN;
            double angle = tick * 0.13 + t * Math.PI * 2.5;
            double r = 1.2 - t * 0.45;
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    px + Math.cos(angle) * r, py + 2.4 - t * 2.0, pz + Math.sin(angle) * r,
                    (px - (px + Math.cos(angle) * r)) * 0.022, -0.026,
                    (pz - (pz + Math.sin(angle) * r)) * 0.022);
        }

        // Wide ambient fall
        int ambientN = density < 1f ? 2 : 3;
        for (int i = 0; i < ambientN; i++) {
            double ox = (rand.nextDouble() - 0.5) * 2.8, oz = (rand.nextDouble() - 0.5) * 2.8;
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    px + ox, py + 2.6 + rand.nextDouble() * 0.5, pz + oz,
                    (rand.nextDouble() - 0.5) * 0.01, -0.021, (rand.nextDouble() - 0.5) * 0.01);
        }

        // Ice crystal burst every 24 ticks
        if (tick % 24 == 0) {
            DustParticleOptions ice = new DustParticleOptions(new Vector3f(0.7f, 0.9f, 1.0f), 1.2f);
            int n = density < 1f ? 5 : 10;
            for (int i = 0; i < n; i++) {
                double a = (2.0 * Math.PI * i / n) + tick * 0.2;
                level.addParticle(ice,
                        px + Math.cos(a) * 0.6, py + 0.05, pz + Math.sin(a) * 0.6,
                        Math.cos(a) * 0.065, 0.045, Math.sin(a) * 0.065);
            }
        }
    }

    // ── Void (VIP+) ── triple counter-alternating rings + rising tendril ──────
    private static void renderVoid(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();

        // Three rings at different heights, alternating CW/CCW
        double[][] rings = {
            { 0.07, 0.60,  tick * 0.18 },   // ground, CW
            { 0.55, 0.44, -tick * 0.14 },   // mid, CCW
            { 1.12, 0.30,  tick * 0.22 },   // high, CW
        };

        int maxRings = density < 1f ? 2 : 3;
        for (int ri = 0; ri < maxRings; ri++) {
            double yOff = rings[ri][0], r = rings[ri][1], baseA = rings[ri][2];
            int n = density < 1f ? 6 : 11;
            for (int i = 0; i < n; i++) {
                double a = baseA + (2.0 * Math.PI * i / n);
                double x = px + Math.cos(a) * r, z = pz + Math.sin(a) * r;
                level.addParticle(ParticleTypes.PORTAL, x, py + yOff, z,
                        (px - x) * 0.07, 0.010, (pz - z) * 0.07);
            }
        }

        // Ascending void tendril from center
        if (tick % 2 == 0) {
            int pts = density < 1f ? 3 : 7;
            double phaseOff = (tick * 0.07) % (Math.PI * 2);
            for (int i = 0; i < pts; i++) {
                double t = (double) i / pts;
                double a = phaseOff + t * Math.PI * 1.6;
                double r = Math.sin(t * Math.PI) * 0.22;
                level.addParticle(ParticleTypes.PORTAL,
                        px + Math.cos(a) * r, py + t * 1.9, pz + Math.sin(a) * r,
                        0, 0.028, 0);
            }
        }
    }

    // ── Dragon (VIP+) ── triangle constellation with connecting breath lines ──
    private static void renderDragon(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), pz = target.getZ();
        double baseAngle = tick * 0.06;
        Random rand = RAND;

        for (int i = 0; i < 3; i++) {
            double a    = baseAngle + i * (2.0 * Math.PI / 3.0);
            double r    = 0.95 + Math.sin(tick * 0.05 + i * 2.1) * 0.20;
            double x    = px + Math.cos(a) * r;
            double z    = pz + Math.sin(a) * r;
            double y    = target.getY() + 0.6 + Math.sin(tick * 0.09 + i * 1.3) * 0.30;

            // Orb cloud
            int cloudN = density < 1f ? 2 : 4;
            for (int j = 0; j < cloudN; j++) {
                level.addParticle(ParticleTypes.DRAGON_BREATH,
                        x + (rand.nextDouble() - 0.5) * 0.25, y + rand.nextDouble() * 0.28,
                        z + (rand.nextDouble() - 0.5) * 0.25,
                        0, 0.013 + rand.nextDouble() * 0.011, 0);
            }

            // Connecting lines between triangle vertices
            if (density >= 1f && tick % 3 == 0) {
                int nextI = (i + 1) % 3;
                double na = baseAngle + nextI * (2.0 * Math.PI / 3.0);
                double nx = px + Math.cos(na) * r, nz = pz + Math.sin(na) * r;
                double ny = target.getY() + 0.6;
                for (int s = 1; s <= 3; s++) {
                    double st = s / 4.0;
                    level.addParticle(ParticleTypes.DRAGON_BREATH,
                            x + (nx - x) * st, (y + ny) * 0.5, z + (nz - z) * st,
                            0, 0.005, 0);
                }
            }
        }
    }

    // ── Helix (Founder) ── twin counter-rotating DNA helix ───────────────────
    //   Gold strand (A) rotates forward, cyan strand (B) counter-rotates.
    //   END_ROD sparkles appear at the evenly-spaced "crossing" heights.
    //   A gold ring caps the top; a cyan ring anchors the base.
    private static void renderHelix(ClientLevel level, Player target, int tick, float density) {
        double px        = target.getX(), py = target.getY(), pz = target.getZ();
        double globalRot = tick * 0.040;  // slow rotation of the whole double helix
        double spin      = tick * 0.110;  // per-strand spin speed
        int    steps     = density < 1f ? 9 : 18;
        double height    = 2.4;
        double radius    = 0.44;
        double turns     = 2.2;           // full turns over the height

        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.00f, 0.82f, 0.10f), 1.0f);
        DustParticleOptions cyan = new DustParticleOptions(new Vector3f(0.15f, 0.88f, 1.00f), 0.9f);

        for (int i = 0; i < steps; i++) {
            double t         = (double) i / steps;
            double y         = py + t * height;
            double helixPart = t * turns * 2.0 * Math.PI;

            // Strand A — gold, forward spin
            double aA = globalRot + spin + helixPart;
            level.addParticle(gold, px + Math.cos(aA) * radius, y, pz + Math.sin(aA) * radius, 0, 0, 0);

            // Strand B — cyan, counter-spin, offset by π
            double aB = globalRot - spin + helixPart + Math.PI;
            level.addParticle(cyan, px + Math.cos(aB) * radius, y, pz + Math.sin(aB) * radius, 0, 0, 0);
        }

        // END_ROD sparkles at evenly-spaced heights (visual "rungs" of the ladder)
        if (tick % 3 == 0) {
            int rungs = (int)(turns * 2);
            for (int k = 0; k < rungs; k++) {
                double t = (double) k / rungs + 0.5 / rungs;
                double y = py + t * height;
                double a = globalRot + t * turns * 2.0 * Math.PI;
                level.addParticle(ParticleTypes.END_ROD,
                        px + Math.cos(a) * radius, y, pz + Math.sin(a) * radius,
                        0, 0.006, 0);
            }
        }

        // Gold ring cap at the top
        int capN = density < 1f ? 6 : 12;
        for (int i = 0; i < capN; i++) {
            double a = globalRot * 2.5 + (2.0 * Math.PI * i / capN);
            level.addParticle(gold, px + Math.cos(a) * 0.28, py + height + 0.08, pz + Math.sin(a) * 0.28, 0, 0, 0);
        }

        // Cyan base ring
        int baseN = density < 1f ? 5 : 10;
        for (int i = 0; i < baseN; i++) {
            double a = -globalRot * 2.0 + (2.0 * Math.PI * i / baseN);
            level.addParticle(cyan, px + Math.cos(a) * 0.28, py + 0.08, pz + Math.sin(a) * 0.28, 0, 0, 0);
        }

        // Occasional full sparkle burst from top of helix
        if (tick % 30 == 0) {
            int n = density < 1f ? 4 : 8;
            for (int i = 0; i < n; i++) {
                double a = Math.random() * Math.PI * 2;
                level.addParticle(ParticleTypes.END_ROD,
                        px + Math.cos(a) * 0.3, py + height + 0.2, pz + Math.sin(a) * 0.3,
                        Math.cos(a) * 0.08, 0.10, Math.sin(a) * 0.08);
            }
        }
    }

    // ── Meteor (Founder) ── staggered 3-meteor shower with tails ─────────────
    private static void renderMeteor(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        int playerOff = (target.getId() * 37) % 90;

        for (int m = 0; m < 3; m++) {
            int period = 90;
            int phase  = (tick + playerOff + m * 30) % period;
            if (phase >= 18) continue;

            float t = phase / 17.0f;
            // Each meteor has a slightly different trajectory
            double dx = -0.22 - m * 0.05, dy = -0.07, dz = -0.18 + m * 0.04;
            double x  = px + 4.5 - t * 9.0 + m * 1.2;
            double y  = py + 5.0 - t * 3.5 - m * 0.5;
            double z  = pz + 3.5 - t * 7.0 + m * 0.8;

            int count = density < 1f ? 2 : 4;
            for (int i = 0; i < count; i++) {
                double jx = (Math.random() - 0.5) * 0.14;
                double jy = (Math.random() - 0.5) * 0.14;
                double jz = (Math.random() - 0.5) * 0.14;
                level.addParticle(ParticleTypes.CRIT,    x + jx, y + jy, z + jz, dx, dy, dz);
                level.addParticle(ParticleTypes.END_ROD, x,      y,      z,      dx * 0.7, dy * 0.7, dz * 0.7);
            }
            if (phase % 2 == 0) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, dx * 0.25, 0, dz * 0.25);
            }
        }
    }

    // =========================================================================
    // MOVEMENT EFFECTS
    // =========================================================================

    // ── Trail (VIP) ── long prismatic gradient trail ──────────────────────────
    private static void renderTrail(ClientLevel level, Player target, int tick, float density) {
        UUID uuid = target.getUUID();
        List<Vec3> positions = trailPositions.computeIfAbsent(uuid, k -> new ArrayList<>());
        positions.add(target.position().add(0, 0.45, 0));
        if (positions.size() > 12) positions.remove(0);
        if (positions.size() < 2) return;

        for (int i = 0; i < positions.size() - 1; i++) {
            Vec3  from = positions.get(i), to = positions.get(i + 1);
            float age  = (float) i / (positions.size() - 1);
            // Hue cycles: cyan near the player → purple → orange at the oldest
            DustParticleOptions dust = new DustParticleOptions(
                    hsvToRgb(0.55f + age * 0.45f, 1.0f, 1.0f), 1.1f - age * 0.4f);
            int steps = density < 1f ? 2 : 4;
            for (int s = 0; s < steps; s++) {
                float  lerp   = (float) s / steps;
                double spread = (s - steps * 0.5) * 0.040;
                level.addParticle(dust,
                        from.x + (to.x - from.x) * lerp + spread,
                        from.y + (to.y - from.y) * lerp,
                        from.z + (to.z - from.z) * lerp + spread, 0, 0, 0);
            }
        }
    }

    // ── Hearts (VIP) ── expanding ring of floating hearts ────────────────────
    private static void renderHearts(ClientLevel level, Player target, int tick, float density) {
        if (tick % 4 != 0) return;
        int count = density < 1f ? 3 : 6;
        for (int i = 0; i < count; i++) {
            double a  = (2.0 * Math.PI * i / count) + tick * 0.08;
            double r  = 0.35 + Math.sin(tick * 0.15 + i) * 0.15;
            double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
            level.addParticle(ParticleTypes.HEART,
                    target.getX() + ox, target.getY() + 0.9 + Math.random() * 0.7, target.getZ() + oz,
                    ox * 0.03, 0.04, oz * 0.03);
        }
    }

    // ── Enchant (VIP+) ── enchantment particles spiral inward ─────────────────
    private static void renderEnchant(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        Random rand = RAND;
        int count = density < 1f ? 3 : 6;
        for (int i = 0; i < count; i++) {
            double a      = rand.nextDouble() * Math.PI * 2;
            double startR = 1.2 + rand.nextDouble() * 0.7;
            double x = px + Math.cos(a) * startR;
            double z = pz + Math.sin(a) * startR;
            double y = py + rand.nextDouble() * 1.9;
            level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                    (px - x) * 0.08, 0.025, (pz - z) * 0.08);
        }
    }

    // ── Flame (VIP+) ── fire pillar with lava drips and head crown ────────────
    private static void renderFlame(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        Random rand = RAND;

        // Stacked small flames along body
        int n = density < 1f ? 4 : 8;
        for (int i = 0; i < n; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.36;
            double oz = (rand.nextDouble() - 0.5) * 0.36;
            double oy = 0.1 + ((double) i / n) * 1.65;
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    px + ox, py + oy, pz + oz, ox * 0.015, 0.025, oz * 0.015);
        }

        // Lava drips from chest height every 8 ticks
        if (tick % 8 == 0) {
            int dn = density < 1f ? 1 : 2;
            for (int i = 0; i < dn; i++) {
                level.addParticle(ParticleTypes.DRIPPING_LAVA,
                        px + (rand.nextDouble() - 0.5) * 0.5, py + 1.3, pz + (rand.nextDouble() - 0.5) * 0.5,
                        0, 0, 0);
            }
        }

        // Rotating flame crown at head height every 10 ticks
        if (tick % 10 == 0) {
            int cn = density < 1f ? 3 : 6;
            for (int i = 0; i < cn; i++) {
                double a = (2.0 * Math.PI * i / cn) + tick * 0.12;
                level.addParticle(ParticleTypes.FLAME,
                        px + Math.cos(a) * 0.4, py + 1.78, pz + Math.sin(a) * 0.4, 0, 0.022, 0);
            }
        }
    }

    // ── Stars (Founder) ── rotating pentagram constellation with connecting lines
    private static void renderStars(ClientLevel level, Player target, int tick, float density) {
        double px  = target.getX(), py = target.getY() + 1.0, pz = target.getZ();
        double rot = tick * 0.025;
        double r   = 0.85;

        // Compute 5 vertices
        double[] vx = new double[5], vy = new double[5], vz = new double[5];
        for (int i = 0; i < 5; i++) {
            double a = rot + (2.0 * Math.PI * i / 5) - Math.PI / 2;
            vx[i] = px + Math.cos(a) * r;
            vy[i] = py + Math.sin(a) * 0.35;
            vz[i] = pz + Math.sin(a) * r;
        }

        // END_ROD at each vertex
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.END_ROD, vx[i], vy[i], vz[i], 0, 0.006, 0);
        }

        // Star-pattern edges (skip-one connections)
        if (density >= 1f && tick % 2 == 0) {
            DustParticleOptions lineDust = new DustParticleOptions(new Vector3f(0.7f, 0.7f, 1.0f), 0.5f);
            int[][] edges = {{0,2},{1,3},{2,4},{3,0},{4,1}};
            for (int[] e : edges) {
                for (int s = 1; s <= 2; s++) {
                    double st = s / 3.0;
                    level.addParticle(lineDust,
                            vx[e[0]] + (vx[e[1]] - vx[e[0]]) * st,
                            vy[e[0]] + (vy[e[1]] - vy[e[0]]) * st,
                            vz[e[0]] + (vz[e[1]] - vz[e[0]]) * st, 0, 0, 0);
                }
            }
        }

        // Stardust drifting in the orbital plane
        Random rand = RAND;
        int sn = density < 1f ? 1 : 2;
        for (int i = 0; i < sn; i++) {
            double a = rot + rand.nextDouble() * Math.PI * 2;
            double d = rand.nextDouble() * r;
            level.addParticle(ParticleTypes.END_ROD,
                    px + Math.cos(a) * d, py + (rand.nextDouble() - 0.5) * 0.6, pz + Math.sin(a) * d,
                    (rand.nextDouble() - 0.5) * 0.02, 0.015, (rand.nextDouble() - 0.5) * 0.02);
        }
    }

    // ── Bubble (VIP) ── rising bubbles with pop burst at peak ────────────────
    // Uses DustParticleOptions instead of BUBBLE — vanilla BUBBLE self-removes
    // outside of water, causing the instant-pop in air.
    private static final DustParticleOptions BUBBLE_PARTICLE =
            new DustParticleOptions(new Vector3f(0.72f, 0.90f, 1.00f), 0.55f);
    private static final DustParticleOptions BUBBLE_POP_PARTICLE =
            new DustParticleOptions(new Vector3f(0.55f, 0.80f, 1.00f), 0.35f);

    private static void renderBubble(ClientLevel level, Player target, int tick, float density) {
        if (tick % 3 != 0) return;
        Random rand = RAND;

        int count = density < 1f ? 2 : 4;
        for (int i = 0; i < count; i++) {
            double a  = rand.nextDouble() * Math.PI * 2;
            double r  = 0.2 + rand.nextDouble() * 0.35;
            double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
            level.addParticle(BUBBLE_PARTICLE,
                    target.getX() + ox, target.getY() + 0.1, target.getZ() + oz,
                    ox * 0.008, 0.038 + rand.nextDouble() * 0.03, oz * 0.008);
        }

        // Pop burst at top height
        if (density >= 1f && tick % 6 == 0) {
            double a = rand.nextDouble() * Math.PI * 2;
            double r = rand.nextDouble() * 0.4;
            for (int j = 0; j < 3; j++) {
                double pa = j * (Math.PI * 2.0 / 3);
                level.addParticle(BUBBLE_POP_PARTICLE,
                        target.getX() + Math.cos(a) * r, target.getY() + 1.9, target.getZ() + Math.sin(a) * r,
                        Math.cos(pa) * 0.06, 0.022, Math.sin(pa) * 0.06);
            }
        }
    }

    // ── Sakura (VIP+) ── cherry blossom drift storm with wind direction ────────
    private static void renderSakura(ClientLevel level, Player target, int tick, float density) {
        if (tick % 2 != 0) return;
        Random rand = RAND;
        float  yaw  = (float) Math.toRadians(target.getYRot());
        double windX = Math.cos(yaw) * 0.040, windZ = Math.sin(yaw) * 0.040;

        DustParticleOptions dark  = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.70f), 1.4f);
        DustParticleOptions light = new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.88f), 1.0f);
        DustParticleOptions white = new DustParticleOptions(new Vector3f(1.0f, 0.92f, 0.95f), 0.8f);

        DustParticleOptions[] petals = {dark, light, white};

        int count = density < 1f ? 3 : 6;
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5) * 1.2;
            double oz = (rand.nextDouble() - 0.5) * 1.2;
            double oy = 0.7 + rand.nextDouble() * 1.5;
            level.addParticle(petals[i % 3],
                    target.getX() + ox, target.getY() + oy, target.getZ() + oz,
                    windX + (rand.nextDouble() - 0.5) * 0.028, -0.016,
                    windZ + (rand.nextDouble() - 0.5) * 0.028);
        }

        // Ground-level petals
        if (density >= 1f && tick % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double ox = (rand.nextDouble() - 0.5) * 0.8;
                double oz = (rand.nextDouble() - 0.5) * 0.8;
                level.addParticle(dark, target.getX() + ox, target.getY() + 0.05, target.getZ() + oz,
                        windX * 0.5, 0, windZ * 0.5);
            }
        }
    }

    // ── Rainbow (Founder) ── wide multi-lane hue-cycling arc ──────────────────
    private static void renderRainbow(ClientLevel level, Player target, int tick, float density) {
        UUID uuid = target.getUUID();
        List<Vec3> positions = trailPositions.computeIfAbsent(uuid, k -> new ArrayList<>());
        positions.add(target.position().add(0, 0.30, 0));
        if (positions.size() > 14) positions.remove(0);
        if (positions.size() < 2) return;

        int lanes = density < 1f ? 5 : 8;
        for (int i = 0; i < positions.size(); i++) {
            float age = (float) i / (positions.size() - 1);
            Vec3  pos = positions.get(i);
            for (int lane = 0; lane < lanes; lane++) {
                float hue  = ((tick * 0.035f) + age * 0.9f + lane * (1.0f / lanes)) % 1.0f;
                float size = 1.6f - age * 0.7f;
                double laneOff = (lane - lanes * 0.5) * 0.13;
                double laneY   = Math.sin(lane * Math.PI / Math.max(lanes - 1, 1)) * 0.28 * age;
                level.addParticle(new DustParticleOptions(hsvToRgb(hue, 1.0f, 1.0f), size),
                        pos.x + laneOff, pos.y + laneY, pos.z + laneOff, 0, 0, 0);
            }
        }
    }

    // ── Comet (VIP) ── bright comet on elliptical orbit with fading tail ────────
    private static void renderComet(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 0.9, pz = target.getZ();
        double angle = tick * 0.09;
        double rx = 1.45, rz = 0.85; // elliptical radii

        double headX = px + Math.cos(angle) * rx;
        double headZ = pz + Math.sin(angle) * rz;
        double headY = py + Math.sin(angle * 0.7) * 0.30;

        // Head
        level.addParticle(ParticleTypes.END_ROD, headX, headY, headZ, 0, 0.008, 0);
        if (density >= 1f) {
            level.addParticle(ParticleTypes.CRIT, headX, headY, headZ, 0, 0.015, 0);
        }

        // Mathematical tail — positions behind the head along the same orbit
        int tailLen = density < 1f ? 9 : 17;
        for (int i = 1; i <= tailLen; i++) {
            float t  = (float) i / tailLen;
            double ta = angle - i * 0.068;
            double tx = px + Math.cos(ta) * rx;
            double tz = pz + Math.sin(ta) * rz;
            double ty = py + Math.sin(ta * 0.7) * 0.30;
            // White-yellow at head fading to dim orange at tail end
            float r = 1.0f, g = 1.0f - t * 0.45f, b = 1.0f - t * 0.95f;
            float sz = 1.0f - t * 0.72f;
            level.addParticle(new DustParticleOptions(new Vector3f(r, g, b), sz), tx, ty, tz, 0, 0, 0);
        }

        // Sparkle burst when comet completes a quarter-orbit
        if (tick % 18 < 2) {
            int sn = density < 1f ? 3 : 6;
            for (int i = 0; i < sn; i++) {
                double sa = Math.random() * Math.PI * 2;
                level.addParticle(ParticleTypes.END_ROD,
                        headX + Math.cos(sa) * 0.12, headY, headZ + Math.sin(sa) * 0.12,
                        Math.cos(sa) * 0.07, 0.06, Math.sin(sa) * 0.07);
            }
        }
    }

    // ── Pulsar (VIP+) ── expanding sonar rings pulsing outward ───────────────
    private static void renderPulsar(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 0.5, pz = target.getZ();
        int period = 28;

        // Three wave rings staggered in phase
        for (int wave = 0; wave < 3; wave++) {
            int phase = (tick + wave * (period / 3)) % period;
            float t = (float) phase / period;
            double r = t * 2.6;
            if (r < 0.1) continue;
            // Cyan-to-white, fading out
            float bright = 1.0f - t;
            DustParticleOptions ring = new DustParticleOptions(
                    new Vector3f(0.3f + t * 0.7f, 0.8f + t * 0.2f, 1.0f), bright * 1.1f);
            int n = Math.max(4, (int)((density < 1f ? 10 : 18) * (1.0f - t * 0.5f)));
            for (int i = 0; i < n; i++) {
                double a = 2.0 * Math.PI * i / n;
                level.addParticle(ring,
                        px + Math.cos(a) * r, py, pz + Math.sin(a) * r, 0, 0, 0);
            }
        }

        // Central core — END_ROD sparkles in sync with each new pulse
        if (tick % period < 4) {
            int cn = density < 1f ? 4 : 8;
            for (int i = 0; i < cn; i++) {
                double a = 2.0 * Math.PI * i / cn + tick * 0.3;
                level.addParticle(ParticleTypes.END_ROD,
                        px + Math.cos(a) * 0.18, py, pz + Math.sin(a) * 0.18, 0, 0.05, 0);
            }
        }

        // Vertical spike flash at exact pulse moment
        if (tick % period == 0) {
            int bn = density < 1f ? 5 : 10;
            for (int i = 0; i < bn; i++) {
                level.addParticle(ParticleTypes.END_ROD, px, py + i * 0.28, pz, 0, 0.07, 0);
            }
        }
    }

    // ── Binary (VIP+) ── two stars in mutual orbit with connecting bridge ─────
    private static void renderBinary(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 0.85, pz = target.getZ();
        double angle = tick * 0.065;
        double r = 0.88;

        // Star A (gold) and B (cyan), opposite sides
        double ax = px + Math.cos(angle) * r,           az = pz + Math.sin(angle) * r;
        double bx = px + Math.cos(angle + Math.PI) * r, bz = pz + Math.sin(angle + Math.PI) * r;
        double ay = py + Math.sin(angle * 0.6) * 0.28;
        double by = py + Math.sin(angle * 0.6 + Math.PI) * 0.28;

        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.15f), 1.1f);
        DustParticleOptions cyan = new DustParticleOptions(new Vector3f(0.3f, 0.82f, 1.0f), 1.1f);

        int core = density < 1f ? 2 : 4;
        Random jR = RAND;
        for (int i = 0; i < core; i++) {
            double j = 0.07;
            level.addParticle(gold, ax + (jR.nextDouble()-0.5)*j, ay + (jR.nextDouble()-0.5)*j, az + (jR.nextDouble()-0.5)*j, 0, 0, 0);
            level.addParticle(cyan, bx + (jR.nextDouble()-0.5)*j, by + (jR.nextDouble()-0.5)*j, bz + (jR.nextDouble()-0.5)*j, 0, 0, 0);
        }
        level.addParticle(ParticleTypes.END_ROD, ax, ay, az, 0, 0.006, 0);
        level.addParticle(ParticleTypes.END_ROD, bx, by, bz, 0, 0.006, 0);

        // Connecting gradient bridge
        if (density >= 1f && tick % 2 == 0) {
            for (int i = 1; i < 6; i++) {
                float bt = i / 6.0f;
                float br = 1.0f - bt * 0.7f, bg = 0.85f - bt * 0.03f, bb = 0.15f + bt * 0.85f;
                level.addParticle(new DustParticleOptions(new Vector3f(br, bg, bb), 0.55f),
                        ax + (bx - ax) * bt, ay + (by - ay) * bt, az + (bz - az) * bt, 0, 0, 0);
            }
        }

        // Orbital ghost trails
        if (tick % 3 == 0) {
            int tn = density < 1f ? 4 : 8;
            for (int i = 1; i <= tn; i++) {
                float fade = 1.0f - (float) i / tn;
                double ta = angle - i * 0.16;
                level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.85f * fade, 0.15f * fade), 0.5f * fade),
                        px + Math.cos(ta) * r, py + Math.sin(ta * 0.6) * 0.28, pz + Math.sin(ta) * r, 0, 0, 0);
                double tb = ta + Math.PI;
                level.addParticle(new DustParticleOptions(new Vector3f(0.3f * fade, 0.82f * fade, 1.0f), 0.5f * fade),
                        px + Math.cos(tb) * r, py + Math.sin(tb * 0.6) * 0.28, pz + Math.sin(tb) * r, 0, 0, 0);
            }
        }
    }

    // ── Nova (Founder) ── crown disk above head + ground halo + starburst ─────
    private static void renderNova(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), pz = target.getZ();
        // Crown disk above head — always fully visible, player never blocks it
        double diskY = target.getY() + 2.35;
        double rot   = tick * 0.04;

        DustParticleOptions gold  = new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.0f), 0.9f);
        DustParticleOptions white = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.6f), 0.6f);

        // Outer crown ring
        int outerN = density < 1f ? 10 : 18;
        for (int i = 0; i < outerN; i++) {
            double a = rot + 2.0 * Math.PI * i / outerN;
            double pulse = 1.0 + Math.sin(tick * 0.11 + i * 0.5) * 0.07;
            level.addParticle(gold, px + Math.cos(a) * pulse, diskY, pz + Math.sin(a) * pulse, 0, 0, 0);
        }
        // Inner counter-rotating ring
        int innerN = density < 1f ? 6 : 10;
        for (int i = 0; i < innerN; i++) {
            double a = -rot * 1.4 + 2.0 * Math.PI * i / innerN;
            level.addParticle(white, px + Math.cos(a) * 0.55, diskY + 0.02, pz + Math.sin(a) * 0.55, 0, 0, 0);
        }

        // Ground halo — large ring at feet, visible from above and sides
        if (tick % 3 == 0) {
            int groundN = density < 1f ? 8 : 14;
            double groundRot = -rot * 0.7;
            for (int i = 0; i < groundN; i++) {
                double a = groundRot + 2.0 * Math.PI * i / groundN;
                double r = 1.1 + Math.sin(tick * 0.08 + i * 0.8) * 0.12;
                level.addParticle(new DustParticleOptions(new Vector3f(1.0f, 0.65f, 0.0f), 0.7f),
                        px + Math.cos(a) * r, target.getY() + 0.05, pz + Math.sin(a) * r, 0, 0, 0);
            }
        }

        // Starburst: 8 symmetric arms from crown every 50 ticks
        int period = 50;
        int phase  = tick % period;
        if (phase < 6) {
            float t  = phase / 5.0f;
            int arms = 8;
            int pts  = density < 1f ? 3 : 5;
            for (int arm = 0; arm < arms; arm++) {
                double a = rot + arm * (2.0 * Math.PI / arms);
                for (int j = 0; j < pts; j++) {
                    double dist = (j + 1) * 0.32 * (1.0 + t * 1.2);
                    DustParticleOptions c = new DustParticleOptions(
                            hsvToRgb((float) arm / arms, 1.0f, 1.0f), 1.0f - (float) j / pts);
                    level.addParticle(c,
                            px + Math.cos(a) * dist, diskY + t * 0.4, pz + Math.sin(a) * dist,
                            Math.cos(a) * 0.04, 0.025, Math.sin(a) * 0.04);
                }
            }
            // Central flash above head
            if (density >= 1f && phase == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    level.addParticle(ParticleTypes.END_ROD,
                            px + Math.cos(a) * 0.25, diskY + 0.1, pz + Math.sin(a) * 0.25,
                            Math.cos(a) * 0.1, 0.14, Math.sin(a) * 0.1);
                }
            }
        }
    }

    // ── Galaxy (Founder) ── two-arm logarithmic spiral with halo ─────────────
    private static void renderGalaxy(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 1.0, pz = target.getZ();
        double rot   = tick * 0.018;
        double rMin  = 0.10, rMax = 1.55;
        double turns = 1.35;
        int    steps = density < 1f ? 12 : 22;

        // Two spiral arms 180° apart
        for (int arm = 0; arm < 2; arm++) {
            double armOff = arm * Math.PI;
            for (int i = 0; i < steps; i++) {
                double t     = (double) i / steps;
                double r     = rMin + t * (rMax - rMin);
                double angle = rot + armOff + t * turns * 2.0 * Math.PI;
                double y     = py + Math.sin(t * Math.PI * 1.4) * 0.32;
                // Colour: blue-white at centre → gold at edge
                float  ct    = (float) t;
                Vector3f col = new Vector3f(ct * 0.55f + 0.45f, ct * 0.35f + 0.65f, 1.0f - ct * 0.75f);
                level.addParticle(new DustParticleOptions(col, 0.95f - ct * 0.45f),
                        px + Math.cos(angle) * r, y, pz + Math.sin(angle) * r, 0, 0, 0);
            }
        }

        // Central bright core
        if (tick % 4 == 0) {
            level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0, 0.007, 0);
        }

        // Outer halo ring
        int haloN = density < 1f ? 8 : 16;
        for (int i = 0; i < haloN; i++) {
            double a = rot * 0.5 + 2.0 * Math.PI * i / haloN;
            level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.65f, 1.0f), 0.5f),
                    px + Math.cos(a) * 1.7, py + Math.sin(a * 2.1) * 0.14, pz + Math.sin(a) * 1.7, 0, 0, 0);
        }

        // Scattered stardust sparkle
        if (tick % 7 == 0) {
            double a = Math.random() * Math.PI * 2, rd = Math.random() * 1.55;
            level.addParticle(ParticleTypes.END_ROD,
                    px + Math.cos(a) * rd, py + (Math.random() - 0.5) * 0.55, pz + Math.sin(a) * rd,
                    0, 0.01, 0);
        }
    }

    // ── Ghost (VIP, movement) ── spirit wisps trailing behind player ──────────
    private static void renderGhost(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY(), pz = target.getZ();
        Random rand = RAND;

        // Wisps: soul fire flames drifting up from the player's lower body
        int wn = density < 1f ? 3 : 6;
        for (int i = 0; i < wn; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.50;
            double oz = (rand.nextDouble() - 0.5) * 0.50;
            double oy = 0.1 + rand.nextDouble() * 1.2;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    px + ox, py + oy, pz + oz,
                    ox * 0.010, 0.028 + rand.nextDouble() * 0.018, oz * 0.010);
        }

        // Ash residue on the ground
        if (tick % 3 == 0) {
            int an = density < 1f ? 2 : 4;
            for (int i = 0; i < an; i++) {
                double ox = (rand.nextDouble() - 0.5) * 0.7;
                double oz = (rand.nextDouble() - 0.5) * 0.7;
                level.addParticle(ParticleTypes.ASH,
                        px + ox, py + 0.05, pz + oz,
                        (rand.nextDouble() - 0.5) * 0.02, 0.01, (rand.nextDouble() - 0.5) * 0.02);
            }
        }

        // Occasional wisp that lingers and drifts sideways
        if (density >= 1f && tick % 5 == 0) {
            double ox = (rand.nextDouble() - 0.5) * 0.8;
            double oz = (rand.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.SOUL,
                    px + ox, py + 0.4 + rand.nextDouble() * 0.5, pz + oz,
                    ox * 0.04, 0.035, oz * 0.04);
        }
    }

    // ── Shockwave (VIP+, movement) ── expanding ground rings ─────────────────
    private static void renderShockwave(ClientLevel level, Player target, int tick, float density) {
        double px = target.getX(), py = target.getY() + 0.04, pz = target.getZ();
        int period = 22;
        int playerOff = (target.getId() * 11) % period;

        // Two staggered rings per cycle so there's always something visible
        for (int wave = 0; wave < 2; wave++) {
            int phase = (tick + playerOff + wave * (period / 2)) % period;
            float t = (float) phase / period;
            double r = t * 2.2;
            if (r < 0.08) continue;
            float bright = 1.0f - t;
            // Silver-blue ring
            DustParticleOptions ring = new DustParticleOptions(
                    new Vector3f(0.65f + t * 0.35f, 0.80f + t * 0.20f, 1.0f), bright * 1.0f);
            int n = Math.max(5, (int)((density < 1f ? 10 : 18) * (1.0f - t * 0.4f)));
            for (int i = 0; i < n; i++) {
                double a = 2.0 * Math.PI * i / n;
                level.addParticle(ring,
                        px + Math.cos(a) * r, py, pz + Math.sin(a) * r, 0, 0, 0);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Fast HSV → RGB conversion (s=1, v=1 yields fully saturated colours). */
    private static Vector3f hsvToRgb(float h, float s, float v) {
        int   hi = (int)(h * 6) % 6;
        float f  = h * 6 - (int)(h * 6);
        float p  = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        return switch (hi) {
            case 0 -> new Vector3f(v, t, p);
            case 1 -> new Vector3f(q, v, p);
            case 2 -> new Vector3f(p, v, t);
            case 3 -> new Vector3f(p, q, v);
            case 4 -> new Vector3f(t, p, v);
            default -> new Vector3f(v, p, q);
        };
    }
}
