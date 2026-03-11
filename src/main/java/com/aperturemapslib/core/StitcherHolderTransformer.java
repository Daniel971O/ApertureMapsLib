package com.aperturemapslib.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StitcherHolderTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.client.renderer.texture.Stitcher$Holder";
    private static final String TARGET_CLASS_OBF = "cdn$a";
    private static final String SCALE_METHOD_DESC = "(I)V";
    private static final String ROTATE_METHOD_DESC = "()V";

    private static final String CONFIG_DIR_NAME = "config";
    private static final String CONFIG_FILE_NAME = "aperturemapslib.cfg";
    private static final Object CONFIG_LOCK = new Object();
    private static volatile boolean CONFIG_READY = false;
    private static final boolean CTM_REQUIRED = loadDebugOption("dependency.ctm.required", true);
    private static final boolean DEBUG_CORE_VERBOSE = loadDebugOption("debug.core.verbose", false);
    private static final boolean DEBUG_STITCH = loadDebugOption("debug.stitch", false);
    private static final boolean DEBUG_PATTERN_SAMPLES = loadDebugOption("debug.pattern_samples", false);
    private static final boolean DEBUG_ATLAS_DUMP = loadDebugOption("debug.atlas_dump", false);
    private static final boolean DEBUG_GUARD_ONLY = loadDebugOption("debug.guard_only", false);

    private static final Set<String> DEBUG_HITS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> DEBUG_OBSERVED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> DEBUG_NULL = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> DEBUG_SQUARE = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> DEBUG_SQUARE_FIELDS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static final ConcurrentHashMap<Object, String> HOLDER_ICON_CACHE = new ConcurrentHashMap<Object, String>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        boolean target = TARGET_CLASS.equals(name)
                || TARGET_CLASS.equals(transformedName)
                || TARGET_CLASS_OBF.equals(name)
                || TARGET_CLASS_OBF.equals(transformedName);

        if (!target) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean patchedScale = false;
        boolean patchedRotate = false;
        boolean patchedCtor = false;

        for (MethodNode method : classNode.methods) {
            if ("<init>".equals(method.name) && isHolderCtor(method.desc)) {
                insertSquareCtorHook(method);
                patchedCtor = true;
                continue;
            }

            if ("<init>".equals(method.name)) {
                continue;
            }

            if (SCALE_METHOD_DESC.equals(method.desc)) {
                insertManagedGuardEarlyReturn(method, method.name + method.desc);
                patchedScale = true;
                continue;
            }

            if (ROTATE_METHOD_DESC.equals(method.desc)) {
                insertManagedGuardEarlyReturn(method, method.name + method.desc);
                patchedRotate = true;
            }
        }

        if (!patchedScale && !patchedRotate && !patchedCtor) {
            System.out.println("[ApertureMapsLib] StitcherHolderTransformer loaded, but no methods were patched");
            return basicClass;
        }

        System.out.println("[ApertureMapsLib] Patched Stitcher$Holder"
                + " (scale=" + patchedScale
                + ", rotate=" + patchedRotate
                + ", ctorSquare=" + patchedCtor + ")");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isHolderCtor(String desc) {
        return "(Lcdq;I)V".equals(desc)
                || "(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;I)V".equals(desc);
    }

    private static void insertSquareCtorHook(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.RETURN) {
                continue;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/aperturemapslib/core/StitcherHolderTransformer",
                    "applySquareReservation",
                    "(Ljava/lang/Object;)V",
                    false
            ));
            method.instructions.insertBefore(insn, hook);
        }
    }

    private static void insertManagedGuardEarlyReturn(MethodNode method, String methodId) {
        InsnList guard = new InsnList();
        LabelNode continueLabel = new LabelNode();

        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/aperturemapslib/core/StitcherHolderTransformer",
                "applySquareReservation",
                "(Ljava/lang/Object;)V",
                false
        ));

        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new LdcInsnNode(methodId));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/aperturemapslib/core/StitcherHolderTransformer",
                "debugObserveHolder",
                "(Ljava/lang/Object;Ljava/lang/String;)V",
                false
        ));

        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/aperturemapslib/core/StitcherHolderTransformer",
                "isManagedHolder",
                "(Ljava/lang/Object;)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));

        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new LdcInsnNode(methodId));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/aperturemapslib/core/StitcherHolderTransformer",
                "debugGuardHit",
                "(Ljava/lang/Object;Ljava/lang/String;)V",
                false
        ));

        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueLabel);

        method.instructions.insert(guard);
    }

    public static void applySquareReservation(Object holder) {
        String iconName = getHolderIconName(holder);
        if (!isSquareTargetSpriteName(iconName)) {
            return;
        }

        int width = readIntField(holder, "b", "width", "field_94204_c", "field_188121_c");
        int height = readIntField(holder, "c", "height", "field_94201_d", "field_94203_d", "field_188122_d");
        if (width <= 0 || height <= 0) {
            return;
        }

        int square = Math.max(width, height);
        writeIntField(holder, square, "b", "width", "field_94204_c", "field_188121_c");
        writeIntField(holder, square, "c", "height", "field_94201_d", "field_94203_d", "field_188122_d");
        writeBooleanField(holder, false, "e", "rotated", "field_94201_f", "field_188125_g");

        if (DEBUG_CORE_VERBOSE && DEBUG_SQUARE.add(iconName)) {
            System.out.println("[ApertureMapsLib] Square reserve applied: " + iconName
                    + " -> " + square + "x" + square + " (source=" + width + "x" + height + ")");
        }
    }


    private static void applySquareReservationKnownIcon(Object holder, String iconName) {
        if (!isSquareTargetSpriteName(iconName)) {
            return;
        }

        int width = readIntField(holder, "b", "width", "field_94204_c", "field_188121_c");
        int height = readIntField(holder, "c", "height", "field_94201_d", "field_94203_d", "field_188122_d");
        if (width <= 0 || height <= 0) {
            String failKey = "bad-size|" + iconName;
            if (DEBUG_CORE_VERBOSE && DEBUG_SQUARE.add(failKey)) {
                System.out.println("[ApertureMapsLib] Square reserve skipped (bad size): " + iconName
                        + " width=" + width + ", height=" + height);
            }
            logHolderIntFields(holder, iconName);
            return;
        }

        int square = Math.max(width, height);
        writeIntField(holder, square, "b", "width", "field_94204_c", "field_188121_c");
        writeIntField(holder, square, "c", "height", "field_94201_d", "field_94203_d", "field_188122_d");
        writeBooleanField(holder, false, "e", "rotated", "field_94201_f", "field_188125_g");

        if (DEBUG_CORE_VERBOSE && DEBUG_SQUARE.add(iconName)) {
            System.out.println("[ApertureMapsLib] Square reserve applied: " + iconName
                    + " -> " + square + "x" + square + " (source=" + width + "x" + height + ")");
        }
    }
    public static boolean isManagedHolder(Object holder) {
        String iconName = getHolderIconName(holder);
        return isTargetSpriteName(iconName);
    }

    public static void debugObserveHolder(Object holder, String methodId) {
        String iconName = getHolderIconName(holder);
        if (iconName == null) {
            if (DEBUG_CORE_VERBOSE && DEBUG_NULL.add(methodId)) {
                System.out.println("[ApertureMapsLib] Stitcher holder observe NULL icon: method="
                        + methodId + ", holderClass=" + (holder != null ? holder.getClass().getName() : "null"));
            }
            return;
        }
        if (!isManagedSprite(iconName)) {
            return;
        }

        applySquareReservationKnownIcon(holder, iconName);

        String key = methodId + "|" + iconName;
        if (DEBUG_CORE_VERBOSE && DEBUG_OBSERVED.add(key)) {
            System.out.println("[ApertureMapsLib] Stitcher holder observe: method="
                    + methodId + ", sprite=" + iconName);
        }
    }

    public static void debugGuardHit(Object holder, String methodId) {
        String iconName = getHolderIconName(holder);
        if (!isTargetSpriteName(iconName)) {
            return;
        }

        applySquareReservationKnownIcon(holder, iconName);

        String key = methodId + "|" + iconName;
        if (DEBUG_CORE_VERBOSE && DEBUG_HITS.add(key)) {
            System.out.println("[ApertureMapsLib] Stitcher holder guard hit: method="
                    + methodId + ", sprite=" + iconName);
        }
    }
    private static boolean isTargetSpriteName(String iconName) {
        return iconName != null && ApertureSpriteRules.isGuardEnabled(iconName);
    }

    private static boolean isSquareTargetSpriteName(String iconName) {
        if (DEBUG_GUARD_ONLY || iconName == null) {
            return false;
        }
        return ApertureSpriteRules.isSquareReserveEnabled(iconName);
    }

    private static boolean isManagedSprite(String iconName) {
        return iconName != null && ApertureSpriteRules.isManaged(iconName);
    }

    public static boolean isCtmRequired() {
        return CTM_REQUIRED;
    }

    public static boolean isDebugCoreVerbose() {
        return DEBUG_CORE_VERBOSE;
    }

    public static boolean isDebugStitchEnabled() {
        return DEBUG_STITCH;
    }

    public static boolean isDebugPatternSamplesEnabled() {
        return DEBUG_PATTERN_SAMPLES;
    }

    public static boolean isDebugAtlasDumpEnabled() {
        return DEBUG_ATLAS_DUMP;
    }
    private static File getPrimaryConfigFile() {
        return new File(CONFIG_DIR_NAME, CONFIG_FILE_NAME);
    }

    private static boolean loadDebugOption(String key, boolean fallback) {
        ensureConfigInitialized();
        File configFile = getPrimaryConfigFile();
        if (!configFile.exists()) {
            return fallback;
        }

        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = normalizeConfigLine(rawLine);
                if (line.isEmpty()) {
                    continue;
                }

                int equals = line.indexOf('=');
                if (equals < 0) {
                    continue;
                }

                String cfgKey = line.substring(0, equals).trim();
                if (!key.equalsIgnoreCase(cfgKey)) {
                    continue;
                }

                String value = line.substring(equals + 1).trim();
                return parseBoolean(value, fallback);
            }
        } catch (IOException ignored) {
            // use fallback
        }

        return fallback;
    }

    private static void ensureConfigInitialized() {
        File configFile = getPrimaryConfigFile();
        synchronized (CONFIG_LOCK) {
            if (CONFIG_READY) {
                return;
            }
            ensureConfigTemplate(configFile);
            ensureConfigKey(configFile, "dependency.ctm.required", "true",
                    "# CTM dependency (true=required, false=optional)",
                    "# Зависимость CTM (true=обязательна, false=опциональна)");
            CONFIG_READY = true;
        }
    }

    private static void ensureConfigKey(File configFile, String key, String value, String commentEn, String commentRu) {
        if (configFile == null || !configFile.exists()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = normalizeConfigLine(rawLine);
                if (line.isEmpty()) {
                    continue;
                }

                int equals = line.indexOf('=');
                if (equals < 0) {
                    continue;
                }

                String cfgKey = line.substring(0, equals).trim();
                if (key.equalsIgnoreCase(cfgKey)) {
                    return;
                }
            }

            List<String> out = new ArrayList<String>(lines);
            if (!out.isEmpty() && !out.get(out.size() - 1).trim().isEmpty()) {
                out.add("");
            }
            if (commentEn != null && !commentEn.isEmpty()) {
                out.add(commentEn);
            }
            if (commentRu != null && !commentRu.isEmpty()) {
                out.add(commentRu);
            }
            out.add(key + "=" + value);
            Files.write(configFile.toPath(), out, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // no-op
        }
    }

    private static void ensureConfigTemplate(File configFile) {
        if (configFile.exists()) {
            return;
        }
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                System.out.println("[ApertureMapsLib] Could not create config directory: " + parent.getAbsolutePath());
                return;
            }

                        List<String> lines = Arrays.asList(
                    "# ApertureMapsLib debug config\\# Дебаг конфиг ApertureMapsLib",
                    "# Core behavior is fixed in code: guard + square reservation are always enabled\\# Основная логика защита + square всегда включена в коде",
                    "# debug.guard_only=true Unstable guard system (disables square reserve)\\# debug.guard_only=true Нестабильная система гвард (отключает square)",
                    "",
                    "# Debug settings\\# Дебаг настройки",
                    "debug.atlas_dump=true",
                    "debug.stitch=false",
                    "debug.pattern_samples=false",
                    "debug.core.verbose=false",
                    "debug.guard_only=false",
                    "dependency.ctm.required=true"
            );

            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            System.out.println("[ApertureMapsLib] Created config template: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("[ApertureMapsLib] Failed to create config template: " + e.getMessage());
        }
    }
private static String normalizeConfigLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }

        String line = rawLine;
        int hash = line.indexOf('#');
        if (hash >= 0) {
            line = line.substring(0, hash);
        }

        int slashComment = line.indexOf("//");
        if (slashComment >= 0) {
            line = line.substring(0, slashComment);
        }

        return line.trim();
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private static String getHolderIconName(Object holder) {
        if (holder == null) {
            return null;
        }

        String cached = HOLDER_ICON_CACHE.get(holder);
        if (cached != null) {
            return cached;
        }

        Object sprite = readField(holder, "a", "theTexture", "field_98156_a", "field_188120_b");
        if (sprite == null) {
            sprite = invokeNoArg(holder, "func_110112_b", "getAtlasSprite", "a", "b");
        }

        String iconName = getIconName(sprite);
        if (iconName != null) {
            HOLDER_ICON_CACHE.put(holder, iconName);
            return iconName;
        }

        for (Field field : getAllFields(holder.getClass())) {
            try {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object candidate = field.get(holder);
                iconName = getIconName(candidate);
                if (iconName != null) {
                    HOLDER_ICON_CACHE.put(holder, iconName);
                    return iconName;
                }
            } catch (Throwable ignored) {
                // continue
            }
        }

        for (Method method : holder.getClass().getDeclaredMethods()) {
            try {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                Object candidate = method.invoke(holder);
                iconName = getIconName(candidate);
                if (iconName != null) {
                    HOLDER_ICON_CACHE.put(holder, iconName);
                    return iconName;
                }
            } catch (Throwable ignored) {
                // continue
            }
        }

        return null;
    }
    private static String getIconName(Object sprite) {
        if (sprite == null) {
            return null;
        }

        Object icon = invokeNoArg(sprite, "func_94215_i", "getIconName", "i");
        if (icon instanceof String) {
            String s = (String) icon;
            if (!s.isEmpty()) {
                return s;
            }
        }

        Object iconField = readField(sprite, "iconName", "field_110984_i", "h", "i", "j");
        if (iconField instanceof String) {
            String s = (String) iconField;
            if (!s.isEmpty()) {
                return s;
            }
        }

        for (Field field : getAllFields(sprite.getClass())) {
            try {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(sprite);
                if (value instanceof String) {
                    String text = (String) value;
                    if (text.contains(":")) {
                        return text;
                    }
                }
            } catch (Throwable ignored) {
                // continue
            }
        }

        for (Method method : sprite.getClass().getDeclaredMethods()) {
            try {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() != String.class) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(sprite);
                if (value instanceof String) {
                    String text = (String) value;
                    if (text.contains(":")) {
                        return text;
                    }
                }
            } catch (Throwable ignored) {
                // continue
            }
        }

        return null;
    }

    private static Field[] getAllFields(Class<?> type) {
        if (type == null) {
            return new Field[0];
        }

        Field[] own = type.getDeclaredFields();
        Class<?> parent = type.getSuperclass();
        if (parent == null) {
            return own;
        }

        Field[] parentFields = getAllFields(parent);
        Field[] all = new Field[own.length + parentFields.length];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(parentFields, 0, all, own.length, parentFields.length);
        return all;
    }


    private static void logHolderIntFields(Object holder, String iconName) {
        if (holder == null) {
            return;
        }

        String key = "fields|" + iconName;
        if (!DEBUG_CORE_VERBOSE || !DEBUG_SQUARE_FIELDS.add(key)) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[ApertureMapsLib] Holder int fields for ").append(iconName).append(": ");

        boolean first = true;
        for (Field field : getAllFields(holder.getClass())) {
            try {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                    continue;
                }
                field.setAccessible(true);
                int value = field.getInt(holder);
                if (!first) {
                    sb.append(", ");
                }
                sb.append(field.getName()).append("=").append(value);
                first = false;
            } catch (Throwable ignored) {
                // continue
            }
        }

        if (first) {
            sb.append("<none>");
        }

        System.out.println(sb.toString());
    }
    private static int readIntField(Object target, String... names) {
        Object value = readField(target, names);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return -1;
    }

    private static void writeIntField(Object target, int value, String... names) {
        if (target == null) {
            return;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    if (f.getType() != int.class) {
                        continue;
                    }
                    f.setAccessible(true);
                    f.setInt(target, value);
                    return;
                } catch (Throwable ignored) {
                    // try next
                }
            }
            current = current.getSuperclass();
        }
    }

    private static void writeBooleanField(Object target, boolean value, String... names) {
        if (target == null) {
            return;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    if (f.getType() != boolean.class) {
                        continue;
                    }
                    f.setAccessible(true);
                    f.setBoolean(target, value);
                    return;
                } catch (Throwable ignored) {
                    // try next
                }
            }
            current = current.getSuperclass();
        }
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) {
            return null;
        }

        Class<?> clazz = target.getClass();
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
                // try declared or next name
            }
            try {
                Method m = clazz.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
                // try next name
            }
        }
        return null;
    }

    private static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Throwable ignored) {
                    // try next name
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }
}








