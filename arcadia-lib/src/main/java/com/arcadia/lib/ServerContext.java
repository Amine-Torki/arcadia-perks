package com.arcadia.lib;

/**
 * Shared runtime context set by the prestige module and read by dependent modules (arcadia-ah, etc.).
 * Populated during config load; falls back to JVM property or "server1" if not set.
 */
public final class ServerContext {

    /** Unique ID for this server instance; used in AH listings to tag origin server. */
    public static volatile String SERVER_ID =
            System.getProperty("arcadia.server_id", "server1");

    private ServerContext() {}
}
