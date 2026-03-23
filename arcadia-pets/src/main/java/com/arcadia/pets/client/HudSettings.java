package com.arcadia.pets.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;

/**
 * Client-side HUD layout settings — toggles and position for the pet widget.
 * The pet widget is a single draggable group containing: portrait, HP bar, aftershock bar.
 * Position stored as fractions (0.0–1.0) of screen dimensions for scale-independence.
 * Persisted to {@code config/arcadia_hud.json}.
 */
public final class HudSettings {

    /** Show the HP bar strip inside the widget. */
    public static boolean showHpBar      = true;
    /** Show the aftershock cooldown bar inside the widget (only visible in ATK mode). */
    public static boolean showAftershock = true;
    /** Master toggle — if false, entire widget is hidden. */
    public static boolean showPetPortrait  = true;
    /** If true, the pet bag roulette skips the spinning animation (accessibility / photosensitivity). */
    public static boolean reducedMotion    = false;

    /** Widget X position as fraction of screen width. NaN = use default (top-left). */
    public static float   petPortraitXFrac = Float.NaN;
    /** Widget Y position as fraction of screen height. NaN = use default (top-left). */
    public static float   petPortraitYFrac = Float.NaN;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded = false;

    private HudSettings() {}

    public static void ensureLoaded() {
        if (!loaded) { load(); loaded = true; }
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o == null) return;
            showHpBar        = bool (o, "showHpBar",        true);
            showAftershock   = bool (o, "showAftershock",   true);
            showPetPortrait  = bool (o, "showPetPortrait",  true);
            reducedMotion    = bool (o, "reducedMotion",    false);
            petPortraitXFrac = frac (o, "petPortraitXFrac", Float.NaN);
            petPortraitYFrac = frac (o, "petPortraitYFrac", Float.NaN);
        } catch (Exception ignored) {}
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("showHpBar",       showHpBar);
        o.addProperty("showAftershock",  showAftershock);
        o.addProperty("showPetPortrait", showPetPortrait);
        o.addProperty("reducedMotion",   reducedMotion);
        addFrac(o, "petPortraitXFrac", petPortraitXFrac);
        addFrac(o, "petPortraitYFrac", petPortraitYFrac);
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(o));
        } catch (Exception ignored) {}
    }

    /** Resets widget position to default top-left (NaN = auto-layout). */
    public static void resetPositions() {
        petPortraitXFrac = Float.NaN;
        petPortraitYFrac = Float.NaN;
    }

    /** Default: top-left corner (x=4). */
    public static int resolvePortraitX(int sw) {
        return Float.isNaN(petPortraitXFrac) ? 4 : (int)(petPortraitXFrac * sw);
    }
    /** Default: top-left corner (y=4). */
    public static int resolvePortraitY(int sh) {
        return Float.isNaN(petPortraitYFrac) ? 4 : (int)(petPortraitYFrac * sh);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }
    private static float frac(JsonObject o, String k, float def) {
        if (!o.has(k) || o.get(k).isJsonNull()) return def;
        float v = o.get(k).getAsFloat();
        return Float.isNaN(v) ? def : v;
    }
    private static void addFrac(JsonObject o, String k, float v) {
        if (Float.isNaN(v)) o.add(k, JsonNull.INSTANCE);
        else o.addProperty(k, v);
    }
    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/arcadia_hud.json");
    }
}
