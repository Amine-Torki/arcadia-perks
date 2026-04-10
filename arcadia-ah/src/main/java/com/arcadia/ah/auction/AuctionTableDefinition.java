package com.arcadia.ah.auction;

import com.arcadia.lib.data.TableDefinition;
import java.util.List;

public final class AuctionTableDefinition implements TableDefinition {
    @Override public String moduleId() { return "arcadia-ah"; }

    @Override
    public List<String> createTableStatements() {
        return List.of(
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_listings (
                listing_id   VARCHAR(36)  PRIMARY KEY,
                server_id    VARCHAR(32)  NOT NULL,
                seller_uuid  VARCHAR(36)  NOT NULL,
                seller_name  VARCHAR(32)  NOT NULL,
                item_nbt     MEDIUMTEXT   NOT NULL,
                item_name    VARCHAR(128) NOT NULL DEFAULT '',
                item_type    VARCHAR(128) NOT NULL DEFAULT '',
                category     VARCHAR(32)  NOT NULL DEFAULT 'misc',
                price        BIGINT       NOT NULL DEFAULT 0,
                listed_at    BIGINT       NOT NULL,
                expires_at   BIGINT       NOT NULL,
                INDEX idx_seller (seller_uuid),
                INDEX idx_expires (expires_at)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_mailbox (
                entry_id      VARCHAR(36) PRIMARY KEY,
                recipient_uuid VARCHAR(36) NOT NULL,
                type          VARCHAR(8)  NOT NULL,
                item_nbt      MEDIUMTEXT,
                coins         BIGINT      NOT NULL DEFAULT 0,
                reason        VARCHAR(256),
                created_at    BIGINT      NOT NULL,
                INDEX idx_recipient (recipient_uuid)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS arcadia_prestige_auction_sales_log (
                sale_id      VARCHAR(36) PRIMARY KEY,
                seller_uuid  VARCHAR(36) NOT NULL,
                seller_name  VARCHAR(32) NOT NULL,
                buyer_uuid   VARCHAR(36) NOT NULL,
                amount       BIGINT      NOT NULL,
                sold_at      BIGINT      NOT NULL,
                INDEX idx_sales_seller (seller_uuid),
                INDEX idx_sales_buyer  (buyer_uuid)
            )
            """
        );
    }
}
