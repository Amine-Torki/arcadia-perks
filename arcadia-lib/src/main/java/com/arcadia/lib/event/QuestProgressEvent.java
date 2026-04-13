package com.arcadia.lib.event;

import net.neoforged.bus.api.Event;

import java.util.UUID;

/**
 * Fired on the NeoForge event bus whenever a player performs an action
 * that can count towards a daily quest.
 *
 * <p>Any module can fire this; arcadia-prestige registers the listener that
 * processes it. Keeping the event in arcadia-lib avoids any hard dependency
 * between the firing module and the prestige module.</p>
 *
 * @param playerUuid UUID of the acting player
 * @param typeId     Name of the matching {@code QuestType} constant (e.g. "PET_SUMMON")
 * @param context    Optional context value (mob type, block type …); empty string if unused
 * @param amount     How many units of progress this action contributes
 */
public final class QuestProgressEvent extends Event {

    private final UUID   playerUuid;
    private final String typeId;
    private final String context;
    private final int    amount;

    public QuestProgressEvent(UUID playerUuid, String typeId, String context, int amount) {
        this.playerUuid = playerUuid;
        this.typeId     = typeId;
        this.context    = context;
        this.amount     = amount;
    }

    public UUID   getPlayerUuid() { return playerUuid; }
    public String getTypeId()     { return typeId; }
    public String getContext()    { return context; }
    public int    getAmount()     { return amount; }
}
