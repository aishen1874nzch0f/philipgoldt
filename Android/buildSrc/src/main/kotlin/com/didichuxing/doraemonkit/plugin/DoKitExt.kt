package com.didichuxing.doraemonkit.plugin

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/5/19-18:00
 * 描    述：dokit 对象扩展
 * 修订历史：
 * ================================================
 */

fun InsnList.methodExitInsnNode(): InsnNode? {
    return this.iterator()?.asSequence()?.filterIsInstance(InsnNode::class.java)?.find {
        it.opcode == RETURN ||
                it.opcode == IRETURN ||
                it.opcode == FRETURN ||
                it.opcode == ARETURN ||
                it.opcode == LRETURN ||
                it.opcode == DRETURN ||
                it.opcode == ATHROW
    }
}