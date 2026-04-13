package com.arcadia.pets.duel;

import com.arcadia.pets.item.PetData;
import com.arcadia.pets.item.PetItem;
import com.arcadia.pets.item.PetRarity;
import com.arcadia.pets.server.PetCollectionSavedData;
import com.arcadia.pets.skill.PetSkill;
import com.arcadia.pets.skill.PetSkills;
import com.arcadia.pets.skill.SkillInstance;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side manager for all active and pending duels.
 *
 * <p>All active duels are held purely in memory — no database persistence
 * is required for the MVP. A crash clears all duels gracefully.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Player A challenges B → {@link #challenge(ServerPlayer, ServerPlayer)}</li>
 *   <li>Player B accepts → {@link #accept(ServerPlayer, DuelSession)}</li>
 *   <li>Both players confirm rosters → {@link #confirmRoster(ServerPlayer, UUID, List)}</li>
 *   <li>Combat runs via {@link #handleAction}</li>
 *   <li>Duel ends → {@link #endDuel(DuelSession, UUID)}</li>
 * </ol>
 */
public final class DuelManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Active duel sessions indexed by duel UUID. */
    private static final Map<UUID, DuelSession> ACTIVE_DUELS = new ConcurrentHashMap<>();

    /** Maps each player UUID to the duel they are currently in. */
    private static final Map<UUID, UUID> PLAYER_TO_DUEL = new ConcurrentHashMap<>();

    /** Pending challenges: challengee UUID → challenger UUID. */
    private static final Map<UUID, UUID> PENDING_CHALLENGES = new ConcurrentHashMap<>();

    /** Challenge expiry timestamps: challengee UUID → epoch ms when challenge expires. */
    private static final Map<UUID, Long> CHALLENGE_EXPIRY = new ConcurrentHashMap<>();

    private static final long CHALLENGE_TTL_MS = 60_000L; // 60 seconds to respond

    /** Fixed UUID representing the bot opponent — no real player can have this UUID. */
    public static final UUID BOT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000B07");

    /** How long the bot "thinks" before acting (ms). Feels natural, not instant. */
    private static final long BOT_THINK_MS = 1_500L;

    // ── Reward scaling ─────────────────────────────────────────────────────────
    /** Maps average rarity ordinal (0–5) to a 1–5 Star Essence reward. */
    private static final float ESSENCE_AVG_SCALE = 0.8f;
    /** Base Numismatics coin reward for winning a duel. */
    private static final int   COIN_BASE         = 10;
    /** Additional coins per average rarity level of the defeated team. */
    private static final int   COIN_PER_RARITY   = 8;

    private DuelManager() {}

    // =========================================================================
    // Challenge flow
    // =========================================================================

    /**
     * Registers a pending challenge from challenger to target.
     *
     * @return false if target already has a pending incoming challenge or is already in a duel
     */
    public static boolean challenge(ServerPlayer challenger, ServerPlayer target) {
        if (isInDuel(challenger.getUUID()) || isInDuel(target.getUUID())) return false;
        if (PENDING_CHALLENGES.containsKey(target.getUUID()))             return false;
        PENDING_CHALLENGES.put(target.getUUID(), challenger.getUUID());
        CHALLENGE_EXPIRY.put(target.getUUID(), System.currentTimeMillis() + CHALLENGE_TTL_MS);
        return true;
    }

    /** True if a non-expired pending challenge targeting this player exists. */
    public static boolean hasPendingChallenge(UUID targetUuid) {
        Long expiry = CHALLENGE_EXPIRY.get(targetUuid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            PENDING_CHALLENGES.remove(targetUuid);
            CHALLENGE_EXPIRY.remove(targetUuid);
            return false;
        }
        return PENDING_CHALLENGES.containsKey(targetUuid);
    }

    /** Returns the challenger UUID for a pending challenge, or null if none. */
    public static UUID getChallengerFor(UUID targetUuid) {
        if (!hasPendingChallenge(targetUuid)) return null;
        return PENDING_CHALLENGES.get(targetUuid);
    }

    /** Clears a pending challenge (accepted or declined). */
    public static void clearChallenge(UUID targetUuid) {
        PENDING_CHALLENGES.remove(targetUuid);
        CHALLENGE_EXPIRY.remove(targetUuid);
    }

    /**
     * Creates a new {@link DuelSession} for the two players (ROSTER_PICK phase).
     * Should be called after the target accepts.
     */
    public static DuelSession accept(ServerPlayer challenger, ServerPlayer target) {
        clearChallenge(target.getUUID());
        DuelSession session = new DuelSession(challenger.getUUID(), target.getUUID());
        ACTIVE_DUELS.put(session.duelId, session);
        PLAYER_TO_DUEL.put(challenger.getUUID(), session.duelId);
        PLAYER_TO_DUEL.put(target.getUUID(), session.duelId);
        return session;
    }

    // =========================================================================
    // Roster selection
    // =========================================================================

    /**
     * Confirms a player's roster selection (up to 3 pet IDs from their collection).
     * When both players have confirmed, starts combat and returns the session.
     * Returns null if validation fails.
     *
     * @param petIds ordered list of up to 3 pet UUIDs from the player's collection
     */
    public static DuelSession confirmRoster(ServerPlayer player, UUID duelId, List<UUID> petIds) {
        DuelSession session = ACTIVE_DUELS.get(duelId);
        if (session == null || session.phase != DuelPhase.ROSTER_PICK) return null;
        if (petIds.size() < 1 || petIds.size() > 3)                    return null;

        // Load pets from the player's collection
        PetCollectionSavedData col = PetCollectionSavedData.getOrCreate(
                player.getServer());
        PetData[] roster = new PetData[3];
        int filled = 0;
        for (UUID petId : petIds) {
            if (filled >= 3) break;
            ItemStack stack = col.findStack(player.getUUID(), petId);
            if (stack.isEmpty()) continue;
            PetData pd = PetData.fromStack(stack);
            if (pd == null) continue;
            roster[filled++] = pd;
        }
        if (filled == 0) return null;
        // Pad empty slots with copies of the first pet if fewer than 3 selected
        if (filled < 3) {
            LOGGER.debug("[DuelManager] {} confirmed {} pet(s); padding remaining {} slot(s) with first pet",
                    player.getUUID(), filled, 3 - filled);
        }
        for (int i = filled; i < 3; i++) roster[i] = roster[0];

        boolean isP1 = player.getUUID().equals(session.p1);
        if (isP1) {
            System.arraycopy(roster, 0, session.p1Roster, 0, 3);
            session.p1Confirmed = true;
        } else {
            System.arraycopy(roster, 0, session.p2Roster, 0, 3);
            session.p2Confirmed = true;
        }

        if (session.p1Confirmed && session.p2Confirmed) {
            session.startCombat();
            LOGGER.info("[DuelManager] Duel {} started between {} and {}",
                    session.duelId, session.p1, session.p2);
        }
        return session;
    }

    // =========================================================================
    // Combat action
    // =========================================================================

    /**
     * Result type for {@link #handleAction}.
     */
    public enum ActionResult {
        OK,
        NOT_IN_DUEL,
        NOT_YOUR_TURN,
        INSUFFICIENT_SP,
        SKILL_ON_COOLDOWN,
        SKILL_NOT_FOUND,
        INVALID_TARGET,
        DUEL_OVER
    }

    /**
     * Resolves a player's action for their current turn.
     *
     * @param player     acting player
     * @param actionType 0=ATTACK, 1=SKILL, 2=DEFEND, 3=FORFEIT, 4=PASS
     * @param skillId    skill identifier (used when actionType=1)
     * @param targetIdx  0–2 target pet index in the target side's roster
     */
    public static ActionResult handleAction(ServerPlayer player, int actionType,
                                            String skillId, int targetIdx) {
        UUID duelId = PLAYER_TO_DUEL.get(player.getUUID());
        if (duelId == null)                              return ActionResult.NOT_IN_DUEL;
        DuelSession session = ACTIVE_DUELS.get(duelId);
        if (session == null || session.phase == DuelPhase.FINISHED) return ActionResult.DUEL_OVER;

        TurnSlot slot = session.currentSlot();
        if (slot == null || !slot.ownerUuid().equals(player.getUUID()))
            return ActionResult.NOT_YOUR_TURN;

        int actorIdx = slot.petIndex();
        UUID opponent = session.opponentOf(player.getUUID());

        return switch (actionType) {
            case 0 -> handleAttack(session, player.getUUID(), actorIdx, opponent, targetIdx);
            case 1 -> handleSkill(session, player.getUUID(), actorIdx, skillId, opponent, targetIdx);
            case 2 -> handleDefend(session, player.getUUID(), actorIdx);
            case 3 -> handleForfeit(session, player.getUUID());
            case 4 -> handlePass(session);
            default -> ActionResult.OK;
        };
    }

    // ── Action implementations ────────────────────────────────────────────────

    private static ActionResult handleAttack(DuelSession session,
                                              UUID actor, int actorPet,
                                              UUID opponent, int targetPet) {
        // Basic attack is FREE (costs no SP) but always ends the turn
        if (!session.isAlive(opponent, targetPet)) return ActionResult.INVALID_TARGET;

        int atk   = session.getAtk(actor, actorPet);
        int dealt = session.resolveDamage(actor, actorPet, opponent, targetPet, atk);
        session.addLog(session.petName(actor, actorPet) + " attacks "
                + session.petName(opponent, targetPet) + "! " + dealt + " damage.");

        if (session.checkWinCondition()) {
            endDuel(session, session.winner);
            return ActionResult.OK;
        }
        session.endTurn(); // attack always ends the turn
        return ActionResult.OK;
    }

    private static ActionResult handleSkill(DuelSession session,
                                             UUID actor, int actorPet,
                                             String skillId,
                                             UUID suggestedTargetOwner, int targetPet) {
        DuelSkillDef def = DuelSkillAdapter.get(skillId);
        if (def == null) return ActionResult.SKILL_NOT_FOUND;

        // Verify the acting pet actually has this skill at level > 0
        PetData petData = session.rosterFor(actor)[actorPet];
        if (petData == null) return ActionResult.SKILL_NOT_FOUND;
        int skillLevel  = 0;
        for (SkillInstance si : petData.skills()) {
            if (si.skill().getId().equals(skillId)) { skillLevel = si.level(); break; }
        }
        if (skillLevel <= 0) return ActionResult.SKILL_NOT_FOUND;

        if (session.currentSP < def.spCost) return ActionResult.INSUFFICIENT_SP;
        if (session.getSkillCooldown(actor, actorPet, skillId) > 0) return ActionResult.SKILL_ON_COOLDOWN;

        // Resolve canonical target based on DuelTargetType
        UUID targetOwner;
        int  resolvedTargetPet;
        switch (def.targetType) {
            case SELF -> { targetOwner = actor; resolvedTargetPet = actorPet; }
            case ALLY_SINGLE -> {
                targetOwner      = actor;
                resolvedTargetPet = (targetPet >= 0 && targetPet < 3
                        && session.isAlive(actor, targetPet)) ? targetPet : actorPet;
            }
            case ENEMY_SINGLE -> {
                UUID opp = session.opponentOf(actor);
                targetOwner = opp;
                if (targetPet >= 0 && targetPet < 3 && session.isAlive(opp, targetPet)) {
                    resolvedTargetPet = targetPet;
                } else {
                    int first = session.firstAlivePet(opp);
                    if (first < 0) return ActionResult.INVALID_TARGET;
                    resolvedTargetPet = first;
                }
            }
            case ALL_ENEMIES, ALL_ALLIES, RANDOM_ENEMY -> {
                // Effect function handles iteration internally; pass dummy values
                targetOwner       = session.opponentOf(actor);
                resolvedTargetPet = 0;
            }
            default -> { targetOwner = actor; resolvedTargetPet = actorPet; }
        }

        String logLine = def.effectFn.apply(session, actor, actorPet,
                targetOwner, resolvedTargetPet, skillLevel);
        session.addLog(logLine);
        session.currentSP -= def.spCost;
        session.setSkillCooldown(actor, actorPet, skillId, def.getCooldown(skillLevel));

        if (session.checkWinCondition()) {
            endDuel(session, session.winner);
            return ActionResult.OK;
        }
        if (session.currentSP <= 0) session.endTurn();
        return ActionResult.OK;
    }

    private static ActionResult handleDefend(DuelSession session, UUID actor, int actorPet) {
        // Defend is FREE (no SP cost) but always ends the turn
        float fortify = 0.10f + (float)(session.getAtk(actor, actorPet)) * 0.005f;
        fortify = Math.min(0.30f, fortify);
        session.applyEffect(actor, actorPet,
                new ActiveEffect(DuelStatusType.FORTIFY, 1, fortify));
        session.addLog(session.petName(actor, actorPet) + " braces for impact! -"
                + Math.round(fortify * 100) + "% incoming damage this turn.");
        session.endTurn();
        return ActionResult.OK;
    }

    private static ActionResult handleForfeit(DuelSession session, UUID forfeiter) {
        UUID winner = session.opponentOf(forfeiter);
        session.addLog(session.petName(forfeiter, 0) + "'s team forfeits!");
        endDuel(session, winner);
        return ActionResult.OK;
    }

    private static ActionResult handlePass(DuelSession session) {
        session.addLog("Turn passed. SP carried over.");
        // SP is NOT zeroed — it naturally saves itself in endTurn() via petSP map
        session.endTurn();
        return ActionResult.OK;
    }

    // =========================================================================
    // End / cleanup
    // =========================================================================

    /**
     * Finalises a duel, sets winner, removes session from active maps.
     * Reward distribution must be triggered separately by the caller after
     * sending the final S2CDuelState packet.
     */
    public static void endDuel(DuelSession session, UUID winner) {
        session.winner = winner;
        session.phase  = DuelPhase.FINISHED;
        ACTIVE_DUELS.remove(session.duelId);
        PLAYER_TO_DUEL.remove(session.p1);
        PLAYER_TO_DUEL.remove(session.p2);
        LOGGER.info("[DuelManager] Duel {} ended. Winner: {}", session.duelId, winner);
    }

    // =========================================================================
    // Bot duel
    // =========================================================================

    /**
     * Starts a practice duel against a bot opponent for the given player.
     * The player's roster is taken from their collection (first 3 pets).
     * Bot ELO is never updated; rewards are halved.
     *
     * @return the created session, or null if the player has no pets
     */
    public static DuelSession startBotDuel(ServerPlayer player, BotDifficulty difficulty) {
        if (isInDuel(player.getUUID())) return null;

        // Load player roster (first 3 pets from collection)
        PetCollectionSavedData col = PetCollectionSavedData.getOrCreate(player.getServer());
        List<ItemStack> stacks = col.getCollection(player.getUUID());
        PetData[] playerRoster = new PetData[3];
        int filled = 0;
        for (ItemStack stack : stacks) {
            if (filled >= 3) break;
            PetData pd = PetData.fromStack(stack);
            if (pd != null) playerRoster[filled++] = pd;
        }
        if (filled == 0) return null;
        while (filled < 3) { playerRoster[filled] = playerRoster[0]; filled++; }

        // Create session: player is p1, bot is p2
        DuelSession session = new DuelSession(player.getUUID(), BOT_UUID);
        System.arraycopy(playerRoster, 0, session.p1Roster, 0, 3);
        PetData[] botRoster = BotRoster.generate(difficulty);
        System.arraycopy(botRoster, 0, session.p2Roster, 0, 3);
        ACTIVE_DUELS.put(session.duelId, session);
        PLAYER_TO_DUEL.put(player.getUUID(), session.duelId);
        // BOT_UUID intentionally NOT added to PLAYER_TO_DUEL (no real player)

        session.p1Confirmed = true;
        session.p2Confirmed = true;
        session.startCombat();

        return session;
    }

    /**
     * Executes the bot's turn: picks a random available skill or falls back to a basic attack.
     * Called by {@link com.arcadia.pets.duel.DuelTickHandler} when {@code session.botActAt} has passed.
     */
    public static void executeBotTurn(DuelSession session) {
        TurnSlot slot = session.currentSlot();
        if (slot == null || !slot.ownerUuid().equals(BOT_UUID)) return;

        int actorPet = slot.petIndex();
        UUID player  = session.opponentOf(BOT_UUID); // the human player
        int targetPet = session.firstAlivePet(player);
        if (targetPet < 0) return; // no valid target — shouldn't happen

        // Collect usable skills (not on cooldown, enough SP)
        List<String> usable = new ArrayList<>();
        PetData pd = session.rosterFor(BOT_UUID)[actorPet];
        if (pd != null) {
            for (SkillInstance si : pd.skills()) {
                if (si.level() <= 0) continue; // locked
                int cd = session.getSkillCooldown(BOT_UUID, actorPet, si.skill().getId());
                if (cd > 0) continue;
                DuelSkillDef def = DuelSkillAdapter.get(si.skill().getId());
                if (def == null) continue;
                if (session.currentSP < def.spCost) continue;
                usable.add(si.skill().getId());
            }
        }

        if (!usable.isEmpty()) {
            // Pick a random usable skill
            String skillId = usable.get(new Random().nextInt(usable.size()));
            handleSkillForBot(session, actorPet, skillId, player, targetPet);
        } else {
            // Fallback: basic attack
            handleAttack(session, BOT_UUID, actorPet, player, targetPet);
        }

        session.botActAt = 0L;
    }

    private static void handleSkillForBot(DuelSession session, int actorPet,
                                           String skillId, UUID opponent, int targetPet) {
        DuelSkillDef def = DuelSkillAdapter.get(skillId);
        if (def == null) { handleAttack(session, BOT_UUID, actorPet, opponent, targetPet); return; }

        // For SELF/ALL skills the target doesn't matter — reuse actorPet as target
        int resolvedTarget = switch (def.targetType) {
            case SELF, ALL_ALLIES -> actorPet;
            default -> targetPet;
        };
        handleSkill(session, BOT_UUID, actorPet, skillId, opponent, resolvedTarget);
    }

    /** Called when a player disconnects — opponent wins automatically. */
    public static void onPlayerLogout(UUID playerUuid) {
        clearChallenge(playerUuid);
        UUID duelId = PLAYER_TO_DUEL.get(playerUuid);
        if (duelId == null) return;
        DuelSession session = ACTIVE_DUELS.get(duelId);
        if (session == null) return;
        session.addLog("Opponent disconnected. Victory by default!");
        endDuel(session, session.opponentOf(playerUuid));
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /** Returns an unmodifiable snapshot of all active duel sessions (for tick checks etc.). */
    public static java.util.Collection<DuelSession> getActiveSessions() {
        return java.util.Collections.unmodifiableCollection(ACTIVE_DUELS.values());
    }

    public static boolean isInDuel(UUID playerUuid) {
        return PLAYER_TO_DUEL.containsKey(playerUuid);
    }

    /** Returns the active session for a player, or null. */
    public static DuelSession getSessionFor(UUID playerUuid) {
        UUID duelId = PLAYER_TO_DUEL.get(playerUuid);
        if (duelId == null) return null;
        return ACTIVE_DUELS.get(duelId);
    }

    public static DuelSession getSession(UUID duelId) {
        return ACTIVE_DUELS.get(duelId);
    }

    /**
     * Computes the Star Essence reward quantity for the winning player.
     * Scales with the average rarity of the losing team's roster.
     */
    public static int essenceRewardFor(DuelSession session, UUID loser) {
        PetData[] roster = session.rosterFor(loser);
        int total = 0;
        for (PetData pd : roster) {
            if (pd != null) total += pd.rarity().ordinal(); // 0=COMMON … 5=MYTHIC
        }
        // Average ordinal → map to 1–5 essence
        float avg = total / 3.0f;
        return Math.max(1, Math.min(5, 1 + (int)(avg * ESSENCE_AVG_SCALE)));
    }

    /**
     * Computes the Numismatics coin reward for the winning player.
     * lv: 10 base + 5 per average rarity level of the losing team.
     */
    public static int coinsRewardFor(DuelSession session, UUID loser) {
        PetData[] roster = session.rosterFor(loser);
        int total = 0;
        for (PetData pd : roster) {
            if (pd != null) total += pd.rarity().ordinal();
        }
        float avg = total / 3.0f;
        return COIN_BASE + (int)(avg * COIN_PER_RARITY);
    }
}
