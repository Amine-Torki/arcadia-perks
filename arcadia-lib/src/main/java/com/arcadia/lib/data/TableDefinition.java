package com.arcadia.lib.data;

import java.util.List;

/**
 * Interface for mods to declare their database tables.
 * Register via {@link DatabaseManager#registerTables(TableDefinition)} during
 * {@code FMLCommonSetupEvent}. Tables are created automatically when the
 * database pool initializes.
 */
public interface TableDefinition {

    /** Module identifier for logging (e.g. "arcadia-pets"). */
    String moduleId();

    /** SQL CREATE TABLE IF NOT EXISTS statements. */
    List<String> createTableStatements();
}
