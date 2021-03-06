package squeek.applecore.asm.module;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import org.objectweb.asm.tree.*;
import squeek.applecore.asm.IClassTransformerModule;
import squeek.asmhelper.ASMHelper;
import squeek.asmhelper.ObfHelper;

public class ModuleFoodEatingSpeed implements IClassTransformerModule
{
	@Override
	public String[] getClassesToTransform()
	{
		return new String[]{
		"net.minecraft.entity.player.EntityPlayer",
		"net.minecraft.client.renderer.ItemRenderer"
		};
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass)
	{
		if (ASMHelper.isCauldron())
			return basicClass;

		boolean isObfuscated = !name.equals(transformedName);
		ClassNode classNode = ASMHelper.readClassFromBytes(basicClass);

		if (transformedName.equals("net.minecraft.entity.player.EntityPlayer"))
		{
			addItemInUseMaxDurationField(classNode);

			MethodNode methodNode = ASMHelper.findMethodNodeOfClass(classNode, isObfuscated ? "a" : "setItemInUse", isObfuscated ? "(Ladd;I)V" : "(Lnet/minecraft/item/ItemStack;I)V");
			if (methodNode != null)
			{
				patchSetItemInUse(classNode, methodNode, isObfuscated);
			}
			else
				throw new RuntimeException(classNode.name + ": setItemInUse method not found");

			// this is client-only, so don't throw an error if it's not found
			methodNode = ASMHelper.findMethodNodeOfClass(classNode, isObfuscated ? "bz" : "getItemInUseDuration", "()I");
			if (methodNode != null)
			{
				patchGetItemInUseDuration(classNode, methodNode, isObfuscated);
			}
		}
		else if (transformedName.equals("net.minecraft.client.renderer.ItemRenderer"))
		{
			MethodNode methodNode = ASMHelper.findMethodNodeOfClass(classNode, isObfuscated ? "a" : "renderItemInFirstPerson", "(F)V");
			if (methodNode != null)
			{
				patchRenderItemInFirstPerson(classNode, methodNode, isObfuscated);
			}
			else
				throw new RuntimeException(classNode.name + ": setItemInUse method not found");
		}

		return ASMHelper.writeClassToBytes(classNode);
	}

	private void patchRenderItemInFirstPerson(ClassNode classNode, MethodNode method, boolean isObfuscated)
	{
		AbstractInsnNode targetNode = ASMHelper.findFirstInstructionWithOpcode(method, INVOKEVIRTUAL);
		while (targetNode != null && !(((MethodInsnNode) targetNode).name.equals(isObfuscated ? "n" : "getMaxItemUseDuration")
				&& ((MethodInsnNode) targetNode).desc.equals("()I")
				&& ((MethodInsnNode) targetNode).owner.equals(ObfHelper.getInternalClassName("net.minecraft.item.ItemStack"))))
		{
			targetNode = ASMHelper.findNextInstructionWithOpcode(targetNode, INVOKEVIRTUAL);
		}
		if (targetNode == null)
			throw new RuntimeException("ItemRenderer.renderItemInFirstPerson: INVOKEVIRTUAL getMaxItemUseDuration instruction not found");

		MethodInsnNode getItemInUseCountNode = (MethodInsnNode) ASMHelper.findPreviousInstructionWithOpcode(targetNode, INVOKEVIRTUAL);
		if (getItemInUseCountNode == null)
			throw new RuntimeException("ItemRenderer.renderItemInFirstPerson: INVOKEVIRTUAL getItemInUseCount instruction not found");

		int entityclientplayermpIndex = ((VarInsnNode) getItemInUseCountNode.getPrevious()).var;
		String entityclientplayermpInternalName = getItemInUseCountNode.owner;

		// f7 = 1.0F - f6 / (float) entityclientplayermp.itemInUseMaxDuration;
		((VarInsnNode) targetNode.getPrevious()).var = entityclientplayermpIndex;
		FieldInsnNode getFieldNode = new FieldInsnNode(GETFIELD, entityclientplayermpInternalName, "itemInUseMaxDuration", "I");

		method.instructions.insert(targetNode, getFieldNode);

		ASMHelper.removeFromInsnListUntil(method.instructions, targetNode, getFieldNode);
	}

	private void patchGetItemInUseDuration(ClassNode classNode, MethodNode method, boolean isObfuscated)
	{
		InsnList needle = new InsnList();
		needle.add(new VarInsnNode(ALOAD, 0));
		needle.add(new FieldInsnNode(GETFIELD, ObfHelper.getInternalClassName("net.minecraft.entity.player.EntityPlayer"), isObfuscated ? "f" : "itemInUse", ObfHelper.getDescriptor("net.minecraft.item.ItemStack")));
		needle.add(new MethodInsnNode(INVOKEVIRTUAL, ObfHelper.getInternalClassName("net.minecraft.item.ItemStack"), isObfuscated ? "n" : "getMaxItemUseDuration", "()I"));

		InsnList replacement = new InsnList();
		replacement.add(new VarInsnNode(ALOAD, 0));
		replacement.add(new VarInsnNode(ALOAD, 0));
		replacement.add(new FieldInsnNode(GETFIELD, classNode.name.replace('.', '/'), "itemInUseMaxDuration", "I"));
		replacement.add(new MethodInsnNode(INVOKESTATIC, "squeek/applecore/asm/Hooks", "getItemInUseMaxDuration", "(Lnet/minecraft/entity/player/EntityPlayer;I)I"));

		int numReplacementsMade = ASMHelper.findAndReplaceAll(method.instructions, needle, replacement);
		if (numReplacementsMade == 0)
			throw new RuntimeException("EntityPlayer.getItemInUseDuration: no replacements made");
	}

	private void patchSetItemInUse(ClassNode classNode, MethodNode method, boolean isObfuscated)
	{
		AbstractInsnNode targetNode = ASMHelper.findFirstInstructionWithOpcode(method, PUTFIELD);
		while (targetNode != null && !((FieldInsnNode) targetNode).name.equals(isObfuscated ? "g" : "itemInUseCount"))
		{
			targetNode = ASMHelper.findNextInstructionWithOpcode(targetNode, PUTFIELD);
		}

		if (targetNode == null)
			throw new RuntimeException("EntityPlayer.setItemInUse: PUTFIELD itemInUseCount instruction not found");

		InsnList toInject = new InsnList();

		// this.itemInUseMaxDuration = p_71008_2_;
		toInject.add(new VarInsnNode(ALOAD, 0));
		toInject.add(new VarInsnNode(ILOAD, 2));
		toInject.add(new FieldInsnNode(PUTFIELD, classNode.name.replace(".", "/"), "itemInUseMaxDuration", "I"));

		method.instructions.insert(targetNode, toInject);
	}

	private void addItemInUseMaxDurationField(ClassNode classNode)
	{
		classNode.fields.add(new FieldNode(ACC_PUBLIC, "itemInUseMaxDuration", "I", null, null));
	}
}
