package com.arcadia.pets.client;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.network.S2CDuelState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache of the last received {@link S2CDuelState}.
 * Thread-safe reads from the render thread; writes happen on the main game thread
 * (Minecraft's single-thread executor via {@code ctx.enqueueWork}).
 */
public final class DuelClientState {

    private static volatile S2CDuelState current = null;
    /** Decoded pet data for both rosters (indexed [side 0=p1 / 1=p2][pet 0–2]). */
    private static volatile PetData[][] rosters = null;

    private DuelClientState() {}

    /** Updates the cached state from a fresh packet. */
    public static void update(S2CDuelState state) {
        current = state;
        PetData[][] r = new PetData[2][3];
        for (int i = 0; i < 3; i++) {
            r[0][i] = state.p1RosterTags()[i] != null
                    ? PetData.fromTag(state.p1RosterTags()[i]) : null;
            r[1][i] = state.p2RosterTags()[i] != null
                    ? PetData.fromTag(state.p2RosterTags()[i]) : null;
        }
        rosters = r;
    }

    /** Returns the latest duel state, or null if not in a duel. */
    public static S2CDuelState get() { return current; }

    /** Returns decoded roster data [side][petIdx], or null if not yet received. */
    public static PetData[][] getRosters() { return rosters; }

    /** True if the local player is currently in an active duel. */
    public static boolean isInDuel() { return current != null; }

    /** Clears state when the player disconnects or the duel screen is fully closed. */
    public static void clear() {
        current = null;
        rosters = null;
    }

    /** Returns true if it is the local player's turn to act. */
    public static boolean isMyTurn(UUID localPlayerUuid) {
        S2CDuelState s = current;
        return s != null && localPlayerUuid.equals(s.actorUuid());
    }

    /** Returns HP for a given side (0=p1, 1=p2) and pet index. */
    public static int hp(int side, int petIdx) {
        S2CDuelState s = current;
        if (s == null) return 0;
        return side == 0 ? s.p1Hp()[petIdx] : s.p2Hp()[petIdx];
    }

    public static int maxHp(int side, int petIdx) {
        S2CDuelState s = current;
        if (s == null) return 1;
        return side == 0 ? s.p1MaxHp()[petIdx] : s.p2MaxHp()[petIdx];
    }

    /** Skill cooldown in turns, or 0 if ready. */
    public static int skillCooldown(int side, int petIdx, String skillId) {
        S2CDuelState s = current;
        if (s == null) return 0;
        String key = (side == 0 ? "1" : "2") + "_" + petIdx + "_" + skillId;
        return s.skillCooldowns().getOrDefault(key, 0);
    }

    /** Active effect labels for a given pet slot. */
    public static List<String> effectLabels(int side, int petIdx) {
        S2CDuelState s = current;
        if (s == null) return Collections.emptyList();
        String key = (side == 0 ? "1" : "2") + "_" + petIdx;
        return s.effectLabels().getOrDefault(key, Collections.emptyList());
    }
}
