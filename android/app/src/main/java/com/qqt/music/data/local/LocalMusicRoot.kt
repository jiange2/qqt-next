package com.qqt.music.data.local

import android.net.Uri
import android.provider.DocumentsContract

/** 已授权的音乐文件夹：SAF 树 URI + 展示用目录名（ADR 0019 以用户选中的树为扫描范围） */
data class LocalMusicRoot(val treeUri: Uri, val name: String)

/**
 * 找出与 [candidate] 重叠的已授权根目录：新目录被已有目录包含、包含已有目录、或就是同一棵树
 * 都算重叠（spec：重叠的授权被明确拒绝，否则同一批文件会被两棵子树重复扫进来）。
 *
 * 判定只能落在 provider 给的树根 documentId 上：同一 authority 且一方是另一方的目录路径前缀，
 * 边界落在 `/` 或 `:` 上——`primary:Music` 命中 `primary:Music/Rock`，不会命中 `primary:Music2`。
 * documentId 不透明（第三方 provider）时判不出包含关系，只按树 URI 去重；
 * 即便漏判，同一首歌也会被内容指纹（ADR 0021）合成一条，不会重复展示。
 */
fun List<LocalMusicRoot>.findOverlap(candidate: Uri): LocalMusicRoot? =
    firstOrNull { it.treeUri == candidate || isSameOrNestedTree(it.treeUri, candidate) }

private fun isSameOrNestedTree(existing: Uri, candidate: Uri): Boolean {
    if (existing.authority != candidate.authority) return false
    val existingId = treeDocumentId(existing) ?: return false
    val candidateId = treeDocumentId(candidate) ?: return false
    return containsDirectory(existingId, candidateId) || containsDirectory(candidateId, existingId)
}

/** [parent] 是 [child] 本身或它的某一级祖先目录 */
private fun containsDirectory(parent: String, child: String): Boolean {
    if (child == parent) return true
    if (child.length <= parent.length || !child.startsWith(parent)) return false
    if (parent.endsWith('/') || parent.endsWith(':')) return true
    val boundary = child[parent.length]
    return boundary == '/' || boundary == ':'
}

/** 非 documents provider 的 URI 上取树根 documentId 会抛异常：判不了重叠，交给 URI 去重 */
private fun treeDocumentId(treeUri: Uri): String? =
    runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
