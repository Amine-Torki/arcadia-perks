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
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_daily_quests (
                uuid          VARCHAR(36)  NOT NULL,
                date_key      VARCHAR(16)  NOT NULL,
                quest_index   TINYINT      NOT NULL,
                quest_type    VARCHAR(32)  NOT NULL,
                difficulty    VARCHAR(16)  NOT NULL,
                context       VARCHAR(128) NOT NULL DEFAULT '',
                target_amount INT          NOT NULL,
                reward_coins  INT          NOT NULL DEFAULT 0,
                reward_essence INT         NOT NULL DEFAULT 0,
                progress      INT          NOT NULL DEFAULT 0,
                claimed       TINYINT      NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, date_key, quest_index),
                INDEX idx_quest_date (uuid, date_key)
            )
            """
        );
    }
}
