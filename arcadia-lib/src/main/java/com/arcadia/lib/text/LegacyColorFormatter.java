package com.arcadia.lib.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class LegacyColorFormatter {
    private LegacyColorFormatter() {}

    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        MutableComponent root = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current != '&') {
                buffer.append(current);
                continue;
            }

            if (i + 1 >= message.length()) {
                break;
            }

            char code = message.charAt(i + 1);
            ChatFormatting formatting = ChatFormatting.getByCode(code);
            if (formatting == null) {
                buffer.append(code);
                i++;
                continue;
            }

            appendBuffer(root, buffer, currentStyle);
            currentStyle = formatting == ChatFormatting.RESET ? Style.EMPTY : currentStyle.applyFormat(formatting);
            i++;
        }

        appendBuffer(root, buffer, currentStyle);
        return root;
    }

    public static String stripInvalidColorCodes(String message) {
        if (message == null || message.isEmpty()) return "";

        StringBuilder sanitized = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current != '&') {
                sanitized.append(current);
                continue;
            }

            if (i + 1 >= message.length()) {
                break;
            }

            char code = message.charAt(i + 1);
            if (ChatFormatting.getByCode(code) != null) {
                sanitized.append('&').append(code);
            } else {
                sanitized.append(code);
            }
            i++;
        }
        return sanitized.toString();
    }

    private static void appendBuffer(MutableComponent root, StringBuilder buffer, Style style) {
        if (buffer.isEmpty()) return;
        root.append(Component.literal(buffer.toString()).withStyle(style));
        buffer.setLength(0);
    }
}
