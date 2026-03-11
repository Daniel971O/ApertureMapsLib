package com.aperturemapslib.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CTMSubmapTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "team.chisel.ctm.client.util.Submap";
    private static final String TARGET_DESC = "(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;F)F";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_CLASS.equals(transformedName)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean patchedU = false;
        boolean patchedV = false;

        for (MethodNode method : classNode.methods) {
            if (!TARGET_DESC.equals(method.desc)) {
                continue;
            }

            if ("getInterpolatedU".equals(method.name)) {
                rewriteU(method);
                patchedU = true;
                continue;
            }

            if ("getInterpolatedV".equals(method.name)) {
                rewriteV(method);
                patchedV = true;
            }
        }

        if (!patchedU && !patchedV) {
            return basicClass;
        }

        System.out.println("[ApertureMapsLib] Patched CTM Submap interpolation with UV clamp (U=" + patchedU + ", V=" + patchedV + ")");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static void rewriteU(MethodNode method) {
        // return sprite.getInterpolatedU(getXOffset() + clamp(u, 0, getWidth()-1e-4)/getWidth())
        InsnList insn = new InsnList();

        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getXOffset",
                "()F",
                false
        ));

        insn.add(new InsnNode(Opcodes.FCONST_0));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getWidth",
                "()F",
                false
        ));
        insn.add(new LdcInsnNode(1.0E-4f));
        insn.add(new InsnNode(Opcodes.FSUB));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "min",
                "(FF)F",
                false
        ));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(FF)F",
                false
        ));

        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getWidth",
                "()F",
                false
        ));
        insn.add(new InsnNode(Opcodes.FDIV));
        insn.add(new InsnNode(Opcodes.FADD));
        insn.add(new InsnNode(Opcodes.F2D));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/texture/TextureAtlasSprite",
                "func_94214_a",
                "(D)F",
                false
        ));
        insn.add(new InsnNode(Opcodes.FRETURN));

        method.instructions.clear();
        method.instructions.add(insn);
    }

    private static void rewriteV(MethodNode method) {
        // return sprite.getInterpolatedV(getYOffset() + clamp(v, 0, getHeight()-1e-4)/getHeight())
        InsnList insn = new InsnList();

        insn.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getYOffset",
                "()F",
                false
        ));

        insn.add(new InsnNode(Opcodes.FCONST_0));
        insn.add(new VarInsnNode(Opcodes.FLOAD, 2));
        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getHeight",
                "()F",
                false
        ));
        insn.add(new LdcInsnNode(1.0E-4f));
        insn.add(new InsnNode(Opcodes.FSUB));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "min",
                "(FF)F",
                false
        ));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Math",
                "max",
                "(FF)F",
                false
        ));

        insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "team/chisel/ctm/client/util/Submap",
                "getHeight",
                "()F",
                false
        ));
        insn.add(new InsnNode(Opcodes.FDIV));
        insn.add(new InsnNode(Opcodes.FADD));
        insn.add(new InsnNode(Opcodes.F2D));
        insn.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/texture/TextureAtlasSprite",
                "func_94207_b",
                "(D)F",
                false
        ));
        insn.add(new InsnNode(Opcodes.FRETURN));

        method.instructions.clear();
        method.instructions.add(insn);
    }
}
