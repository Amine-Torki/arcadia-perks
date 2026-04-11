package com.arcadia.lib.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ArcadiaGson {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ArcadiaGson() {}

    public static Gson get() {
        return GSON;
    }
}
