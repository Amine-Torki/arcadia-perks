package com.arcadia.pets.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PetPacketHandler {

    private PetPacketHandler() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                S2CPetReveal.TYPE,
                S2CPetReveal.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToClient(
                S2CPetPanel.TYPE,
                S2CPetPanel.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToServer(
                C2SPetAction.TYPE,
                C2SPetAction.STREAM_CODEC,
                C2SPetAction::handle
        );
        registrar.playToClient(
                S2CPocketPet.TYPE,
                S2CPocketPet.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToServer(
                C2SRenamePet.TYPE,
                C2SRenamePet.STREAM_CODEC,
                C2SRenamePet::handle
        );
        registrar.playToClient(
                S2CPetHpSync.TYPE,
                S2CPetHpSync.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToClient(
                S2CAftershockCooldown.TYPE,
                S2CAftershockCooldown.STREAM_CODEC,
                (p, ctx) -> p.handle(ctx)
        );
        registrar.playToServer(
                C2SAuraTick.TYPE,
                C2SAuraTick.STREAM_CODEC,
                C2SAuraTick::handle
        );

        // ── Duel packets ──────────────────────────────────────────────────────
        registrar.playToClient(
                S2CDuelInvite.TYPE,
                S2CDuelInvite.STREAM_CODEC,
                S2CDuelInvite::handle
        );
        registrar.playToClient(
                S2CDuelRosterPick.TYPE,
                S2CDuelRosterPick.STREAM_CODEC,
                S2CDuelRosterPick::handle
        );
        registrar.playToServer(
                C2SDuelRosterReady.TYPE,
                C2SDuelRosterReady.STREAM_CODEC,
                C2SDuelRosterReady::handle
        );
        registrar.playToClient(
                S2CDuelState.TYPE,
                S2CDuelState.STREAM_CODEC,
                S2CDuelState::handle
        );
        registrar.playToServer(
                C2SDuelAction.TYPE,
                C2SDuelAction.STREAM_CODEC,
                C2SDuelAction::handle
        );
        registrar.playToServer(
                C2SDuelHint.TYPE,
                C2SDuelHint.STREAM_CODEC,
                C2SDuelHint::handle
        );
        registrar.playToClient(
                S2CDuelHint.TYPE,
                S2CDuelHint.STREAM_CODEC,
                S2CDuelHint::handle
        );
    }
}
