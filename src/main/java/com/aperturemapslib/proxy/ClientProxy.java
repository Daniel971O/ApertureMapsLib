package com.aperturemapslib.proxy;

import com.aperturemapslib.client.MissingCtmHandler;
import com.aperturemapslib.core.ApertureSpriteRules;
import com.aperturemapslib.core.StitcherHolderTransformer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("ApertureMapsLib");

    private static boolean atlasDumped;

    @Override
    public void onMissingCtmRequired() {
        MissingCtmHandler.register();
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        boolean stitchDebug = StitcherHolderTransformer.isDebugStitchEnabled();
        boolean atlasDumpDebug = StitcherHolderTransformer.isDebugAtlasDumpEnabled();

        if (!stitchDebug && !atlasDumpDebug) {
            return;
        }

        TextureMap map = event.getMap();

        if (stitchDebug) {
            TextureAtlasSprite missing = map.getMissingSprite();
            List<String> managedSprites = collectManagedSprites(map);

            if (managedSprites.isEmpty()) {
                LOGGER.info("[ApertureMapsLib] Stitch debug: no aperture-managed sprites registered in this atlas");
            }

            for (String spriteName : managedSprites) {
                TextureAtlasSprite sprite = map.getAtlasSprite(spriteName);
                TextureAtlasSprite direct = map.getTextureExtry(spriteName);
                boolean isMissing = sprite == null
                        || sprite == missing
                        || "missingno".equals(sprite.getIconName());
                boolean rotated = getSpriteRotated(sprite);

                if (sprite == null) {
                    LOGGER.warn("[ApertureMapsLib] Stitch debug {} -> null sprite", spriteName);
                    continue;
                }

                LOGGER.info(
                        "[ApertureMapsLib] Stitch debug {} -> iconName={}, missing={}, directPresent={}, rotated={}, size={}x{}, frames={}, hasAnim={}, origin=({}, {}), u=[{}, {}], v=[{}, {}]",
                        spriteName,
                        sprite.getIconName(),
                        isMissing,
                        direct != null,
                        rotated,
                        sprite.getIconWidth(),
                        sprite.getIconHeight(),
                        sprite.getFrameCount(),
                        sprite.hasAnimationMetadata(),
                        sprite.getOriginX(),
                        sprite.getOriginY(),
                        sprite.getMinU(),
                        sprite.getMaxU(),
                        sprite.getMinV(),
                        sprite.getMaxV()
                );

                logFrameDataDiagnostics(spriteName, sprite);
            }

            dumpRegisteredManagedSprites(managedSprites);
        }

        if (atlasDumpDebug) {
            dumpAtlasSnapshot(map);
        }
    }

    private static void logFrameDataDiagnostics(String spriteName, TextureAtlasSprite sprite) {
        try {
            List<?> frames = tryGetFrameDataList(sprite);
            if (frames == null || frames.isEmpty()) {
                LOGGER.info("[ApertureMapsLib] Frame debug {} -> no frame data list", spriteName);
                return;
            }

            Object first = frames.get(0);
            if (!(first instanceof int[][])) {
                LOGGER.info("[ApertureMapsLib] Frame debug {} -> first frame is not int[][] ({})",
                        spriteName, first != null ? first.getClass().getName() : "null");
                return;
            }

            int[][] frame0 = (int[][]) first;
            int levels = frame0.length;
            int len0 = (levels > 0 && frame0[0] != null) ? frame0[0].length : -1;
            int width = sprite.getIconWidth();
            int height = sprite.getIconHeight();
            int expected = width * height;
            int inferredHeight = (width > 0 && len0 >= 0) ? (len0 / width) : -1;

            LOGGER.info(
                    "[ApertureMapsLib] Frame debug {} -> frameList={}, mipLevels={}, level0Len={}, expectedLen={}, inferredHeight={}, spriteSize={}x{}",
                    spriteName,
                    frames.size(),
                    levels,
                    len0,
                    expected,
                    inferredHeight,
                    width,
                    height
            );
        } catch (Throwable t) {
            LOGGER.warn("[ApertureMapsLib] Frame debug failed for {}", spriteName, t);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> tryGetFrameDataList(TextureAtlasSprite sprite) {
        String[] names = new String[]{"framesTextureData", "field_110976_a", "a"};
        for (String name : names) {
            try {
                Field f = TextureAtlasSprite.class.getDeclaredField(name);
                f.setAccessible(true);
                Object value = f.get(sprite);
                if (value instanceof List) {
                    return (List<?>) value;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static void dumpAtlasSnapshot(TextureMap map) {
        if (atlasDumped) {
            return;
        }
        atlasDumped = true;

        try {
            int atlasWidth = readAtlasDimension(map, true);
            int atlasHeight = readAtlasDimension(map, false);

            if (atlasWidth <= 0 || atlasHeight <= 0) {
                int[] inferred = inferAtlasDimensions(map);
                atlasWidth = inferred[0];
                atlasHeight = inferred[1];
            }

            if (atlasWidth <= 0 || atlasHeight <= 0) {
                LOGGER.warn("[ApertureMapsLib] Atlas dump skipped: atlas size not resolved");
                return;
            }

            File outDir = new File(Minecraft.getMinecraft().gameDir, "aperturemapslib_debug");
            if (!outDir.exists() && !outDir.mkdirs()) {
                LOGGER.warn("[ApertureMapsLib] Atlas dump skipped: cannot create {}", outDir.getAbsolutePath());
                return;
            }

            File atlasPng = new File(outDir, "atlas_blocks.png");
            File atlasRects = new File(outDir, "atlas_blocks_rects.txt");

            dumpAtlasPng(map, atlasWidth, atlasHeight, atlasPng);
            dumpAtlasRectList(map, atlasRects);

            LOGGER.info("[ApertureMapsLib] Atlas debug exported: {} and {}",
                    atlasPng.getAbsolutePath(), atlasRects.getAbsolutePath());
        } catch (Throwable t) {
            LOGGER.warn("[ApertureMapsLib] Atlas dump failed", t);
        }
    }

    private static void dumpAtlasPng(TextureMap map, int atlasWidth, int atlasHeight, File outFile) throws Exception {
        IntBuffer buffer = BufferUtils.createIntBuffer(atlasWidth * atlasHeight);
        GlStateManager.bindTexture(map.getGlTextureId());
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(
                GL11.GL_TEXTURE_2D,
                0,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                buffer
        );

        int[] pixels = new int[atlasWidth * atlasHeight];
        buffer.get(pixels);

        BufferedImage image = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < atlasHeight; y++) {
            int srcRow = atlasHeight - 1 - y;
            for (int x = 0; x < atlasWidth; x++) {
                image.setRGB(x, y, pixels[srcRow * atlasWidth + x]);
            }
        }

        ImageIO.write(image, "png", outFile);
    }

    private static void dumpAtlasRectList(TextureMap map, File outFile) throws Exception {
        Map<?, ?> sprites = tryGetSpriteMap(map, "mapUploadedSprites");
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_94252_e");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "mapRegisteredSprites");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_110574_e");
        }

        if (sprites == null) {
            return;
        }

        List<String> lines = new ArrayList<String>();
        lines.add("name\toriginX\toriginY\twidth\theight\tminU\tmaxU\tminV\tmaxV");

        for (Map.Entry<?, ?> entry : sprites.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (!(value instanceof TextureAtlasSprite)) {
                continue;
            }
            TextureAtlasSprite sprite = (TextureAtlasSprite) value;
            lines.add(name
                    + "\t" + sprite.getOriginX()
                    + "\t" + sprite.getOriginY()
                    + "\t" + sprite.getIconWidth()
                    + "\t" + sprite.getIconHeight()
                    + "\t" + sprite.getMinU()
                    + "\t" + sprite.getMaxU()
                    + "\t" + sprite.getMinV()
                    + "\t" + sprite.getMaxV());
        }

        Collections.sort(lines.subList(1, lines.size()));
        Files.write(outFile.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static int readAtlasDimension(TextureMap map, boolean width) {
        String[] fields = width
                ? new String[]{"atlasWidth", "field_147636_j", "field_110575_b"}
                : new String[]{"atlasHeight", "field_147637_k", "field_110576_c"};

        for (String fieldName : fields) {
            try {
                Field f = TextureMap.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object value = f.get(map);
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (Throwable ignored) {
                // try next name
            }
        }

        return 0;
    }

    private static int[] inferAtlasDimensions(TextureMap map) {
        Map<?, ?> sprites = tryGetSpriteMap(map, "mapUploadedSprites");
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_94252_e");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "mapRegisteredSprites");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_110574_e");
        }

        int maxX = 0;
        int maxY = 0;

        if (sprites != null) {
            for (Object value : sprites.values()) {
                if (!(value instanceof TextureAtlasSprite)) {
                    continue;
                }
                TextureAtlasSprite sprite = (TextureAtlasSprite) value;
                maxX = Math.max(maxX, sprite.getOriginX() + sprite.getIconWidth());
                maxY = Math.max(maxY, sprite.getOriginY() + sprite.getIconHeight());
            }
        }

        if (maxX <= 0 || maxY <= 0) {
            return new int[]{0, 0};
        }

        return new int[]{nextPow2(maxX), nextPow2(maxY)};
    }

    private static int nextPow2(int value) {
        int x = 1;
        while (x < value) {
            x <<= 1;
        }
        return x;
    }

    private static boolean getSpriteRotated(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return false;
        }
        try {
            Field f = TextureAtlasSprite.class.getDeclaredField("rotated");
            f.setAccessible(true);
            return f.getBoolean(sprite);
        } catch (Throwable t) {
            try {
                Field f = TextureAtlasSprite.class.getDeclaredField("field_110973_g");
                f.setAccessible(true);
                return f.getBoolean(sprite);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static List<String> collectManagedSprites(TextureMap map) {
        Map<?, ?> sprites = tryGetSpriteMap(map, "mapRegisteredSprites");
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_110574_e");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "mapUploadedSprites");
        }
        if (sprites == null) {
            sprites = tryGetSpriteMap(map, "field_94252_e");
        }
        if (sprites == null) {
            return Collections.emptyList();
        }

        List<String> managed = new ArrayList<String>();
        for (Object key : sprites.keySet()) {
            String name = String.valueOf(key);
            if (ApertureSpriteRules.isManaged(name)) {
                managed.add(name);
            }
        }
        Collections.sort(managed);
        return managed;
    }

    private static void dumpRegisteredManagedSprites(List<String> managedSprites) {
        for (String name : managedSprites) {
            LOGGER.info("[ApertureMapsLib] Registered managed sprite key: {}", name);
        }
        LOGGER.info("[ApertureMapsLib] Registered managed sprite count: {}", managedSprites.size());
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> tryGetSpriteMap(TextureMap map, String fieldName) {
        try {
            Field f = TextureMap.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object value = f.get(map);
            if (value instanceof Map) {
                return (Map<?, ?>) value;
            }
        } catch (Throwable ignored) {
            // no-op
        }
        return null;
    }
}


