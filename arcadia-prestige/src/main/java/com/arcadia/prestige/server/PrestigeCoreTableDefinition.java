package com.arcadia.prestige.server;

import com.arcadia.lib.data.TableDefinition;
import java.util.List;

public final class PrestigeCoreTableDefinition implements TableDefinition {
    @Override public String moduleId() { return "arcadia-prestige"; }

    @Override
    public List<String> createTableStatements() {
        return List.of(
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                grade VARCHAR(16),
                particle_id VARCHAR(64) DEFAULT '',
                last_claim BIGINT DEFAULT 0,
                streak INT DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_pet_registry (
                pet_id VARCHAR(36) PRIMARY KEY,
                owner_uuid VARCHAR(36),
                mob_type VARCHAR(64),
                rarity TINYINT,
                total_stars TINYINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_owner_uuid (owner_uuid)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_daily_milestone_claims (
                uuid VARCHAR(36) NOT NULL,
                cycle INT NOT NULL,
                claims INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, cycle)
            )
            """
        );
    }
}
