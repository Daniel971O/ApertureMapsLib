package com.aperturemapslib.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class ApertureSpriteRules {

    private static final String RULE_SECTION = "aperturemapslib";
    private static final Rule DISABLED_RULE = new Rule(false, false, false);
    private static final ConcurrentHashMap<String, Rule> CACHE = new ConcurrentHashMap<String, Rule>();

    private ApertureSpriteRules() {
    }

    public static boolean isManaged(String iconName) {
        return resolveRule(iconName).enabled;
    }

    public static boolean isSquareReserveEnabled(String iconName) {
        Rule rule = resolveRule(iconName);
        return rule.enabled && rule.squareReserve;
    }

    public static boolean isGuardEnabled(String iconName) {
        Rule rule = resolveRule(iconName);
        return rule.enabled && rule.guard;
    }

    private static Rule resolveRule(String iconName) {
        if (iconName == null || iconName.isEmpty()) {
            return DISABLED_RULE;
        }
        Rule cached = CACHE.get(iconName);
        if (cached != null) {
            return cached;
        }
        Rule loaded = loadRule(iconName);
        Rule existing = CACHE.putIfAbsent(iconName, loaded);
        return existing != null ? existing : loaded;
    }

    private static Rule loadRule(String iconName) {
        String[] parts = splitIconName(iconName);
        if (parts == null) {
            return DISABLED_RULE;
        }

        String modid = parts[0];
        String path = parts[1];
        String mcmetaPath = "assets/" + modid + "/textures/" + path + ".png.mcmeta";

        InputStream stream = ApertureSpriteRules.class.getClassLoader().getResourceAsStream(mcmetaPath);
        if (stream == null) {
            return DISABLED_RULE;
        }

        try {
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return DISABLED_RULE;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (!root.has(RULE_SECTION)) {
                return DISABLED_RULE;
            }

            JsonElement section = root.get(RULE_SECTION);
            if (section == null || section.isJsonNull()) {
                return DISABLED_RULE;
            }

            if (section.isJsonPrimitive()) {
                boolean enabled = getAsBoolean(section, false);
                if (!enabled) {
                    return DISABLED_RULE;
                }
                return new Rule(true, true, true);
            }

            if (!section.isJsonObject()) {
                return DISABLED_RULE;
            }

            JsonObject obj = section.getAsJsonObject();
            boolean enabled = getAsBoolean(obj.get("enabled"), true);
            if (!enabled) {
                return DISABLED_RULE;
            }

            boolean squareReserve = true;
            if (obj.has("square_reserve")) {
                squareReserve = getAsBoolean(obj.get("square_reserve"), true);
            } else if (obj.has("square")) {
                squareReserve = getAsBoolean(obj.get("square"), true);
            }

            boolean guard = getAsBoolean(obj.get("guard"), true);
            return new Rule(true, squareReserve, guard);
        } catch (Throwable ignored) {
            return DISABLED_RULE;
        } finally {
            try {
                stream.close();
            } catch (Throwable ignored) {
                // no-op
            }
        }
    }

    private static boolean getAsBoolean(JsonElement element, boolean fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isBoolean()) {
                    return element.getAsBoolean();
                }
                if (element.getAsJsonPrimitive().isString()) {
                    String v = element.getAsString().trim().toLowerCase();
                    if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
                        return true;
                    }
                    if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
                        return false;
                    }
                }
                if (element.getAsJsonPrimitive().isNumber()) {
                    return element.getAsInt() != 0;
                }
            }
        } catch (Throwable ignored) {
            return fallback;
        }
        return fallback;
    }

    private static String[] splitIconName(String iconName) {
        int colon = iconName.indexOf(':');
        if (colon <= 0 || colon == iconName.length() - 1) {
            return null;
        }

        String modid = iconName.substring(0, colon);
        String path = iconName.substring(colon + 1);
        if (modid.isEmpty() || path.isEmpty()) {
            return null;
        }

        return new String[]{modid, path};
    }

    private static final class Rule {
        private final boolean enabled;
        private final boolean squareReserve;
        private final boolean guard;

        private Rule(boolean enabled, boolean squareReserve, boolean guard) {
            this.enabled = enabled;
            this.squareReserve = squareReserve;
            this.guard = guard;
        }
    }
}