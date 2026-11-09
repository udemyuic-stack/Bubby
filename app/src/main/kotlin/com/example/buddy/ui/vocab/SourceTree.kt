package com.example.buddy.ui.vocab

class SourceNode(val id: Long, val parent: Long?, val kind: String, val title: String, val skill: String?, val words: Int)

class SourceBranch(val node: SourceNode, val children: List<SourceBranch>)

object SourceTree {
    fun build(rows: List<SourceNode>): List<SourceBranch> {
        val byId = rows.associateBy { it.id }
        val kids = HashMap<Long, MutableList<SourceNode>>()
        val roots = ArrayList<SourceNode>()
        for (r in rows) {
            val p = r.parent
            if (p == null || p !in byId || reachesSelf(byId, r)) roots.add(r) else kids.getOrPut(p) { ArrayList() }.add(r)
        }
        fun branch(n: SourceNode, seen: Set<Long>): SourceBranch =
            SourceBranch(n, (kids[n.id] ?: emptyList<SourceNode>()).sortedBy { it.id }.filter { it.id !in seen }.map { branch(it, seen + it.id) })
        return roots.sortedBy { it.id }.map { branch(it, setOf(it.id)) }
    }

    fun path(rows: List<SourceNode>, id: Long): String {
        val byId = rows.associateBy { it.id }
        val parts = ArrayList<String>()
        val seen = HashSet<Long>()
        var cur = byId[id]
        while (cur != null && seen.add(cur.id)) {
            parts.add(cur.title)
            cur = cur.parent?.let { byId[it] }
        }
        return parts.reversed().joinToString(" › ")
    }

    private fun reachesSelf(byId: Map<Long, SourceNode>, n: SourceNode): Boolean {
        val seen = HashSet<Long>()
        var cur = n.parent?.let { byId[it] }
        while (cur != null) {
            if (cur.id == n.id) return true
            if (!seen.add(cur.id)) return false
            cur = cur.parent?.let { byId[it] }
        }
        return false
    }
}
