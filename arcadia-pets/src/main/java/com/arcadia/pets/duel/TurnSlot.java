package com.arcadia.pets.duel;

import java.util.UUID;

/**
 * One entry in the turn-order queue: identifies which player's pet acts next.
 *
 * @param ownerUuid  UUID of the player who owns this pet
 * @param petIndex   0–2 index in that player's roster
 * @param agility    cached AGI stat used to sort the turn order
 */
public record TurnSlot(UUID ownerUuid, int petIndex, int agility) {}
