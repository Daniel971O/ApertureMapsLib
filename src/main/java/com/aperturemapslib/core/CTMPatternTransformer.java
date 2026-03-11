package com.aperturemapslib.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CTMPatternTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "team.chisel.ctm.client.texture.render.TextureMap$MapType$2";
    private static final String TARGET_METHOD = "transformQuad";
    private static final String TARGET_DESC = "(Lteam/chisel/ctm/client/texture/render/TextureMap;Lnet/minecraft/client/renderer/block/model/BakedQuad;Lteam/chisel/ctm/api/texture/ITextureContext;I)Ljava/util/List;";
    private static final String DEBUG_CLASS_PREFIX = "team.chisel.ctm.client.texture.render.TextureMap$MapType$";

    private static boolean debugSeenMapTypeClass;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String normName = normalizeClassName(name);
        String normTransformed = normalizeClassName(transformedName);

        if (StitcherHolderTransformer.isDebugCoreVerbose()
                && !debugSeenMapTypeClass
                && (normName.startsWith(DEBUG_CLASS_PREFIX) || normTransformed.startsWith(DEBUG_CLASS_PREFIX))) {
            debugSeenMapTypeClass = true;
            System.out.println("[ApertureMapsLib] CTMPatternTransformer saw class load: name="
                    + normName + ", transformedName=" + normTransformed);
        }

        if (!isTargetClass(normName, normTransformed)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean patched = false;

        for (MethodNode method : classNode.methods) {
            if (!isTargetMethod(method)) {
                continue;
            }

            TypeInsnNode newSubmap = findSubmapConstructorSite(method);
            if (newSubmap == null) {
                continue;
            }

            InsnList clamp = new InsnList();
            LabelNode skipClamp = new LabelNode();

            // Apply only to .mcmeta-managed textures.
            clamp.add(new VarInsnNode(Opcodes.ALOAD, 1));
            clamp.add(new FieldInsnNode(
                    Opcodes.GETFIELD,
                    "team/chisel/ctm/client/texture/render/TextureMap",
                    "sprites",
                    "[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
            ));
            clamp.add(new InsnNode(Opcodes.ICONST_0));
            clamp.add(new InsnNode(Opcodes.AALOAD));
            clamp.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/renderer/texture/TextureAtlasSprite",
                    "func_94215_i",
                    "()Ljava/lang/String;",
                    false
            ));
            clamp.add(new InsnNode(Opcodes.DUP));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 6));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 7));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 8));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 9));
            clamp.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/aperturemapslib/core/CTMPatternDebug",
                    "logPatternSample",
                    "(Ljava/lang/String;FFFF)V",
                    false
            ));
            clamp.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/aperturemapslib/core/ApertureSpriteRules",
                    "isManaged",
                    "(Ljava/lang/String;)Z",
                    false
            ));
            clamp.add(new JumpInsnNode(Opcodes.IFEQ, skipClamp));

            // f6 = max(0.001f, f6 - 0.001f)
            clamp.add(new LdcInsnNode(0.001f));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 6));
            clamp.add(new LdcInsnNode(0.001f));
            clamp.add(new InsnNode(Opcodes.FSUB));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false));
            clamp.add(new VarInsnNode(Opcodes.FSTORE, 6));

            // f7 = max(0.001f, f7 - 0.001f)
            clamp.add(new LdcInsnNode(0.001f));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 7));
            clamp.add(new LdcInsnNode(0.001f));
            clamp.add(new InsnNode(Opcodes.FSUB));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false));
            clamp.add(new VarInsnNode(Opcodes.FSTORE, 7));

            // f8 = max(0f, min(f8 + 0.0005f, 15.999f - f6))
            clamp.add(new InsnNode(Opcodes.FCONST_0));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 8));
            clamp.add(new LdcInsnNode(0.0005f));
            clamp.add(new InsnNode(Opcodes.FADD));
            clamp.add(new LdcInsnNode(15.999f));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 6));
            clamp.add(new InsnNode(Opcodes.FSUB));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false));
            clamp.add(new VarInsnNode(Opcodes.FSTORE, 8));

            // f9 = max(0f, min(f9 + 0.0005f, 15.999f - f7))
            clamp.add(new InsnNode(Opcodes.FCONST_0));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 9));
            clamp.add(new LdcInsnNode(0.0005f));
            clamp.add(new InsnNode(Opcodes.FADD));
            clamp.add(new LdcInsnNode(15.999f));
            clamp.add(new VarInsnNode(Opcodes.FLOAD, 7));
            clamp.add(new InsnNode(Opcodes.FSUB));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false));
            clamp.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false));
            clamp.add(new VarInsnNode(Opcodes.FSTORE, 9));

            // For our non-square patterns, avoid CTM subdivide(4) path that can corrupt per-subquad UVs.
            clamp.add(new InsnNode(Opcodes.ICONST_1));
            clamp.add(new VarInsnNode(Opcodes.ISTORE, 4));

            clamp.add(skipClamp);

            method.instructions.insertBefore(newSubmap, clamp);
            patched = true;
        }

        if (!patched) {
            System.out.println("[ApertureMapsLib] CTM pattern clamp transformer loaded, but no Submap constructor site was patched");
            return basicClass;
        }

        System.out.println("[ApertureMapsLib] Patched CTM pattern UV clamp for .mcmeta-managed sprites in " + classNode.name.replace('/', '.'));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static TypeInsnNode findSubmapConstructorSite(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                if ("team/chisel/ctm/client/util/Submap".equals(typeInsn.desc)) {
                    return typeInsn;
                }
            }
        }
        return null;
    }

    private static boolean isTargetMethod(MethodNode method) {
        if (!TARGET_METHOD.equals(method.name)) {
            return false;
        }

        if (TARGET_DESC.equals(method.desc)) {
            return true;
        }

        return method.desc != null
                && method.desc.contains("Lteam/chisel/ctm/client/texture/render/TextureMap;")
                && method.desc.endsWith(")Ljava/util/List;");
    }

    private static boolean isTargetClass(String normName, String normTransformed) {
        return TARGET_CLASS.equals(normName) || TARGET_CLASS.equals(normTransformed);
    }

    private static String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        return className.replace('/', '.');
    }
}