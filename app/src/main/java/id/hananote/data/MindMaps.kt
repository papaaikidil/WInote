package id.hananote.data

/**
 * Aturan mind map: hubungan induk-anak, menambah dan menghapus ide, dan menyusun rapi.
 * Ukuran tiap ide diberikan dari luar ([SizeOf]) supaya aturan ini tidak bergantung pada cara menggambar.
 */
object MindMaps {

    /** Lebar dan tinggi kotak sebuah ide pada kedalaman tertentu (0 = ide utama). */
    fun interface SizeOf {
        fun size(node: MindNode, depth: Int): FloatArray
    }

    /** Warna cabang: tiap cabang utama mendapat warnanya sendiri, anak-anaknya mengikuti. */
    val BranchColors: List<Int> = listOf(
        0xFFE5484D.toInt(), // merah
        0xFF2F80ED.toInt(), // biru
        0xFF27AE60.toInt(), // hijau
        0xFFF2994A.toInt(), // jingga
        0xFF9B51E0.toInt(), // ungu
        0xFF00A3A3.toInt(), // toska
        0xFFD6336C.toInt(), // mawar
        0xFF8A6D00.toInt(), // kunyit tua
    )

    private const val SIBLING_GAP = 16f
    private const val LEVEL_GAP_FIRST = 56f
    private const val LEVEL_GAP = 40f

    /** Mind map baru: ide utama dengan tiga cabang kosong siap diisi. */
    fun starter(rootColor: Int, sizeOf: SizeOf): List<MindNode> {
        var nodes = listOf(MindNode(0, -1, 0f, 0f, "Ide utama", rootColor))
        repeat(3) { nodes = addChild(nodes, 0, sizeOf).first }
        return arrange(nodes, sizeOf)
    }

    fun root(nodes: List<MindNode>): MindNode? = nodes.firstOrNull { it.parent < 0 }

    fun children(nodes: List<MindNode>, id: Int): List<MindNode> = nodes.filter { it.parent == id }.sortedBy { it.id }

    fun depths(nodes: List<MindNode>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        val byParent = nodes.groupBy { it.parent }
        fun walk(id: Int, d: Int) {
            out[id] = d
            for (c in byParent[id].orEmpty()) if (c.id !in out) walk(c.id, d + 1)
        }
        for (r in byParent[-1].orEmpty()) walk(r.id, 0)
        return out
    }

    /** Ide ini beserta seluruh keturunannya. */
    fun subtree(nodes: List<MindNode>, id: Int): Set<Int> {
        val out = LinkedHashSet<Int>()
        val byParent = nodes.groupBy { it.parent }
        fun walk(i: Int) {
            if (!out.add(i)) return
            for (c in byParent[i].orEmpty()) walk(c.id)
        }
        walk(id)
        return out
    }

    /** Sisi cabang terhadap ide utama: 1 kanan, -1 kiri. */
    fun side(nodes: List<MindNode>, id: Int): Int {
        val r = root(nodes) ?: return 1
        val n = nodes.firstOrNull { it.id == id } ?: return 1
        if (n.id == r.id) return 1
        return if (n.x >= r.x) 1 else -1
    }

    private fun nextId(nodes: List<MindNode>) = (nodes.maxOfOrNull { it.id } ?: -1) + 1

    /**
     * Tambah ide anak di bawah [parentId]. Cabang utama bergantian kanan-kiri dan mendapat warna baru;
     * ide yang lebih dalam mengikuti arah dan warna induknya. Hasil: daftar baru dan nomor ide baru.
     */
    fun addChild(nodes: List<MindNode>, parentId: Int, sizeOf: SizeOf): Pair<List<MindNode>, Int> {
        val parent = nodes.firstOrNull { it.id == parentId } ?: return nodes to -1
        val depths = depths(nodes)
        val pd = depths[parentId] ?: 0
        val siblings = children(nodes, parentId)
        val id = nextId(nodes)
        val color: Int
        val dir: Int
        if (pd == 0) {
            val used = siblings.map { it.color }.toSet()
            color = BranchColors.firstOrNull { it !in used } ?: BranchColors[siblings.size % BranchColors.size]
            val right = siblings.count { it.x >= parent.x }
            val left = siblings.size - right
            dir = if (right <= left) 1 else -1
        } else {
            color = parent.color
            dir = side(nodes, parentId)
        }
        val ps = sizeOf.size(parent, pd)
        val probe = MindNode(id, parentId, 0f, 0f, "", color)
        val cs = sizeOf.size(probe, pd + 1)
        val gap = if (pd == 0) LEVEL_GAP_FIRST else LEVEL_GAP
        val x = parent.x + dir * (ps[0] / 2f + gap + cs[0] / 2f)
        // Di bawah saudara terakhir yang searah, atau sejajar induknya bila belum ada.
        val sameSide = siblings.filter { (it.x >= parent.x) == (dir > 0) }
        var y = if (sameSide.isEmpty()) parent.y else sameSide.maxOf { it.y } + cs[1] + SIBLING_GAP
        // Jangan menumpuk di atas ide lain.
        var guard = 0
        while (guard++ < 200 && nodes.any { o ->
                val os = sizeOf.size(o, depths[o.id] ?: 1)
                kotlin.math.abs(o.x - x) < (os[0] + cs[0]) / 2f && kotlin.math.abs(o.y - y) < (os[1] + cs[1]) / 2f + 4f
            }) {
            y += cs[1] / 2f + SIBLING_GAP
        }
        return (nodes + MindNode(id, parentId, x, y, "", color)) to id
    }

    /** Hapus ide beserta keturunannya. Ide utama tidak bisa dihapus. */
    fun remove(nodes: List<MindNode>, id: Int): List<MindNode> {
        val n = nodes.firstOrNull { it.id == id } ?: return nodes
        if (n.parent < 0) return nodes
        val gone = subtree(nodes, id)
        return nodes.filter { it.id !in gone }
    }

    /** Geser ide beserta keturunannya. */
    fun moveSubtree(nodes: List<MindNode>, id: Int, dx: Float, dy: Float): List<MindNode> {
        val ids = subtree(nodes, id)
        return nodes.map { if (it.id in ids) it.moved(it.x + dx, it.y + dy) else it }
    }

    /** Ganti warna ide beserta keturunannya. */
    fun recolor(nodes: List<MindNode>, id: Int, color: Int): List<MindNode> {
        val ids = subtree(nodes, id)
        return nodes.map { if (it.id in ids) it.withColor(color) else it }
    }

    /**
     * Susun rapi: ide utama di tengah, cabang utama bergantian kanan dan kiri, tiap cabang
     * ditumpuk ke bawah dengan jarak yang sama dan anak-anaknya terpusat pada induknya.
     */
    fun arrange(nodes: List<MindNode>, sizeOf: SizeOf): List<MindNode> {
        val root = root(nodes) ?: return nodes
        val depths = depths(nodes)
        val byParent = nodes.groupBy { it.parent }
        val sizes = HashMap<Int, FloatArray>()
        for (n in nodes) sizes[n.id] = sizeOf.size(n, depths[n.id] ?: 0)
        val heights = HashMap<Int, Float>()
        fun height(id: Int): Float {
            heights[id]?.let { return it }
            val kids = byParent[id].orEmpty()
            val own = sizes[id]!![1]
            val h = if (kids.isEmpty()) own else maxOf(own, kids.sumOf { height(it.id).toDouble() }.toFloat() + SIBLING_GAP * (kids.size - 1))
            heights[id] = h
            return h
        }
        val pos = HashMap<Int, Pair<Float, Float>>()
        pos[root.id] = root.x to root.y
        fun place(parentId: Int, kids: List<MindNode>, dir: Int, centerY: Float, depth: Int) {
            if (kids.isEmpty()) return
            val (px, _) = pos[parentId]!!
            val pw = sizes[parentId]!![0]
            val total = kids.sumOf { height(it.id).toDouble() }.toFloat() + SIBLING_GAP * (kids.size - 1)
            var top = centerY - total / 2f
            val gap = if (depth == 1) LEVEL_GAP_FIRST else LEVEL_GAP
            for (k in kids) {
                val h = height(k.id)
                val cy = top + h / 2f
                val cx = px + dir * (pw / 2f + gap + sizes[k.id]!![0] / 2f)
                pos[k.id] = cx to cy
                place(k.id, byParent[k.id].orEmpty().sortedBy { it.id }, dir, cy, depth + 1)
                top += h + SIBLING_GAP
            }
        }
        val first = byParent[root.id].orEmpty().sortedBy { it.id }
        val right = first.filterIndexed { i, _ -> i % 2 == 0 }
        val left = first.filterIndexed { i, _ -> i % 2 == 1 }
        place(root.id, right, 1, root.y, 1)
        place(root.id, left, -1, root.y, 1)
        return nodes.map { n -> pos[n.id]?.let { (x, y) -> n.moved(x, y) } ?: n }
    }

    /** Kotak isi mind map (kiri, atas, kanan, bawah), termasuk jarak tepi [pad]. */
    fun bounds(nodes: List<MindNode>, sizeOf: SizeOf, pad: Float = 12f): FloatArray {
        if (nodes.isEmpty()) return floatArrayOf(0f, 0f, 1f, 1f)
        val depths = depths(nodes)
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (n in nodes) {
            val s = sizeOf.size(n, depths[n.id] ?: 0)
            l = minOf(l, n.x - s[0] / 2f)
            r = maxOf(r, n.x + s[0] / 2f)
            t = minOf(t, n.y - s[1] / 2f)
            b = maxOf(b, n.y + s[1] / 2f)
        }
        return floatArrayOf(l - pad, t - pad, r + pad, b + pad)
    }
}
