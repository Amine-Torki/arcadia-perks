package com.arcadia.lib.event;

import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/**
 * Fired on the NeoForge event bus when a pet duel concludes.
 *
 * <p>Fired by arcadia-pets after reward distribution. arcadia-prestige listens
 * to update ELO ratings and grant bonus XP without any hard cross-module dependency.</p>
 *
 * @param winnerUuid       UUID of the winning player
 * @param loserUuid        UUID of the losing player
 * @param winnerMobType    mob type string of the winner's first (lead) pet (e.g. "minecraft:cat")
 * @param loserMobType     mob type string of the loser's first (lead) pet
 * @param winnerPetIds     petIds for all pets in the winner's duel roster (for XP grants)
 * @param loserPetIds      petIds for all pets in the loser's duel roster
 */
public final class DuelResultEvent extends Event {

    private final UUID         winnerUuid;
    private final UUID         loserUuid;
    private final String       winnerMobType;
    private final String       loserMobType;
    private final List<UUID>   winnerPetIds;
    private final List<UUID>   loserPetIds;

    public DuelResultEvent(UUID winnerUuid, UUID loserUuid,
                           String winnerMobType, String loserMobType,
                           List<UUID> winnerPetIds, List<UUID> loserPetIds) {
        this.winnerUuid    = winnerUuid;
        this.loserUuid     = loserUuid;
        this.winnerMobType = winnerMobType;
        this.loserMobType  = loserMobType;
        this.winnerPetIds  = winnerPetIds;
        this.loserPetIds   = loserPetIds;
    }

    public UUID         getWinnerUuid()    { return winnerUuid; }
    public UUID         getLoserUuid()     { return loserUuid; }
    public String       getWinnerMobType() { return winnerMobType; }
    public String       getLoserMobType()  { return loserMobType; }
    public List<UUID>   getWinnerPetIds()  { return winnerPetIds; }
    public List<UUID>   getLoserPetIds()   { return loserPetIds; }
}
