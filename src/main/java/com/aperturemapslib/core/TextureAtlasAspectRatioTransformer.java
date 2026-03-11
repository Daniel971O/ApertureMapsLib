package com.aperturemapslib.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class TextureAtlasAspectRatioTransformer implements IClassTransformer {

    private static final String TARGET_CLASS = "net.minecraft.client.renderer.texture.TextureAtlasSprite";
    private static final String TARGET_CLASS_OBF = "cdq";
    private static final String TARGET_MESSAGE = "broken aspect ratio and not an animation";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        boolean target = TARGET_CLASS.equals(transformedName)
                || TARGET_CLASS.equals(name)
                || TARGET_CLASS_OBF.equals(name)
                || TARGET_CLASS_OBF.equals(transformedName);

        if (!target) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        String iconGetter = findIconNameGetter(classNode);
        if (iconGetter == null) {
            System.out.println("[ApertureMapsLib] TextureAtlasSprite icon getter not found, keeping previous global patch behavior");
            return applyGlobalThrowRemoval(classNode, basicClass);
        }

        boolean patchedAspect = false;

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode)) {
                    continue;
                }

                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (!TARGET_MESSAGE.equals(ldc.cst)) {
                    continue;
                }

                AbstractInsnNode prev1 = insn.getPrevious();
                AbstractInsnNode prev2 = prev1 != null ? prev1.getPrevious() : null;
                AbstractInsnNode next1 = insn.getNext();
                AbstractInsnNode next2 = next1 != null ? next1.getNext() : null;

                if (!(prev2 instanceof TypeInsnNode)
                        || prev2.getOpcode() != Opcodes.NEW
                        || prev1 == null || prev1.getOpcode() != Opcodes.DUP
                        || !(next1 instanceof MethodInsnNode)
                        || next1.getOpcode() != Opcodes.INVOKESPECIAL
                        || next2 == null || next2.getOpcode() != Opcodes.ATHROW) {
                    continue;
                }

                LabelNode continueLabel = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        classNode.name,
                        iconGetter,
                        "()Ljava/lang/String;",
                        false
                ));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/aperturemapslib/core/ApertureSpriteRules",
                        "isManaged",
                        "(Ljava/lang/String;)Z",
                        false
                ));
                guard.add(new JumpInsnNode(Opcodes.IFNE, continueLabel));

                method.instructions.insertBefore(prev2, guard);

                AbstractInsnNode afterThrow = next2.getNext();
                if (afterThrow != null) {
                    method.instructions.insertBefore(afterThrow, continueLabel);
                } else {
                    method.instructions.add(continueLabel);
                }

                patchedAspect = true;
            }
        }

        if (!patchedAspect) {
            System.out.println("[ApertureMapsLib] TextureAtlasSprite transformer loaded, but no aspect-ratio throw was matched");
            return basicClass;
        }

        System.out.println("[ApertureMapsLib] Patched TextureAtlasSprite aspect check for .mcmeta-managed sprites");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static String findIconNameGetter(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (!"()Ljava/lang/String;".equals(method.desc)) {
                continue;
            }

            AbstractInsnNode[] ops = method.instructions.toArray();
            int i = 0;
            while (i < ops.length && ops[i].getOpcode() == -1) {
                i++;
            }
            if (i >= ops.length || ops[i].getOpcode() != Opcodes.ALOAD) {
                continue;
            }
            if (!(ops[i + 1] instanceof FieldInsnNode) || ops[i + 1].getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            FieldInsnNode fieldInsn = (FieldInsnNode) ops[i + 1];
            if (!"Ljava/lang/String;".equals(fieldInsn.desc)) {
                continue;
            }
            if (ops[i + 2].getOpcode() != Opcodes.ARETURN) {
                continue;
            }
            return method.name;
        }

        for (MethodNode method : classNode.methods) {
            if ("()Ljava/lang/String;".equals(method.desc)
                    && ("func_94215_i".equals(method.name) || "getIconName".equals(method.name))) {
                return method.name;
            }
        }

        return null;
    }

    private static byte[] applyGlobalThrowRemoval(ClassNode classNode, byte[] fallbackClass) {
        boolean patched = false;

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();

                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (TARGET_MESSAGE.equals(ldc.cst)) {
                        AbstractInsnNode prev1 = insn.getPrevious();
                        AbstractInsnNode prev2 = prev1 != null ? prev1.getPrevious() : null;
                        AbstractInsnNode next1 = insn.getNext();
                        AbstractInsnNode next2 = next1 != null ? next1.getNext() : null;

                        if (prev2 instanceof TypeInsnNode
                                && prev2.getOpcode() == Opcodes.NEW
                                && prev1 != null && prev1.getOpcode() == Opcodes.DUP
                                && next1 instanceof MethodInsnNode
                                && next1.getOpcode() == Opcodes.INVOKESPECIAL
                                && next2 != null && next2.getOpcode() == Opcodes.ATHROW) {

                            method.instructions.remove(prev2);
                            method.instructions.remove(prev1);
                            method.instructions.remove(insn);
                            method.instructions.remove(next1);
                            method.instructions.remove(next2);

                            patched = true;
                            next = next2.getNext();
                        }
                    }
                }

                insn = next;
            }
        }

        if (!patched) {
            return fallbackClass;
        }

        System.out.println("[ApertureMapsLib] Fallback: patched TextureAtlasSprite to allow non-square textures globally");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}