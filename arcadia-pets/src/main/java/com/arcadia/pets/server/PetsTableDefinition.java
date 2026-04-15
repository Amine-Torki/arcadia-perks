package com.arcadia.pets.server;

import com.arcadia.lib.data.TableDefinition;
import java.util.List;

public final class PetsTableDefinition implements TableDefinition {
    @Override public String moduleId() { return "arcadia-pets"; }

    @Override
    public List<String> createTableStatements() {
        return List.of(
            """
            CREATE TABLE IF NOT EXISTS arcadia_pet_collections (
                owner_uuid  VARCHAR(36)  NOT NULL,
                slot_index  INT          NOT NULL,
                item_nbt    MEDIUMTEXT   NOT NULL,
                PRIMARY KEY (owner_uuid, slot_index)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_pet_history (
                id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
                owner_uuid  VARCHAR(36)  NOT NULL,
                pet_id      VARCHAR(36)  NOT NULL,
                pet_nbt     MEDIUMTEXT   NOT NULL,
                created_at  BIGINT       NOT NULL,
                INDEX idx_pet_hist_owner (owner_uuid),
                INDEX idx_pet_hist_pet   (pet_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_duel_elo (
                uuid              VARCHAR(36)  PRIMARY KEY,
                rating            INT          NOT NULL DEFAULT 1000,
                wins              INT          NOT NULL DEFAULT 0,
                losses            INT          NOT NULL DEFAULT 0,
                favorite_mob_type VARCHAR(128) NOT NULL DEFAULT '',
                INDEX idx_elo_rating (rating)
            )
            """
        );
    }
}
