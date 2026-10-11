package org.seedhecode.incidentassistant.assistant

import org.json.JSONObject
import java.text.Normalizer

/**
 * Laya's tokenizer (HF tokenizers byte-level BPE: NFC normalizer, GPT-2 split regex, added tokens matched
 * first, leftmost-longest). Equivalent to `Tokenizer.encode(text, add_special_tokens=False).ids`.
 */
class LayaTokenizer(json: String) {
    private val vocab = HashMap<String, Int>(60000)
    private val ranks = HashMap<String, Int>(60000)
    private val added: Regex?
    private val addedIds = HashMap<String, Int>()
    private val cache = HashMap<String, IntArray>()

    init {
        val root = JSONObject(json)
        val model = root.getJSONObject("model")
        val v = model.getJSONObject("vocab")
        for (k in v.keys()) vocab[k] = v.getInt(k)
        val merges = model.getJSONArray("merges")
        for (i in 0 until merges.length()) {
            val m = merges.get(i)
            val key = if (m is org.json.JSONArray) m.getString(0) + " " + m.getString(1) else m.toString()
            ranks[key] = i
        }
        val tokens = root.getJSONArray("added_tokens")
        for (i in 0 until tokens.length()) {
            val t = tokens.getJSONObject(i); addedIds[t.getString("content")] = t.getInt("id")
        }
        added = addedIds.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            .takeIf { it.isNotEmpty() }?.let { Regex(it) }
    }

    fun encode(text: String): IntArray {
        val norm = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = ArrayList<Int>()
        var pos = 0
        added?.findAll(norm)?.forEach { m ->
            if (m.range.first > pos) encodePlain(norm.substring(pos, m.range.first), out)
            out += addedIds.getValue(m.value); pos = m.range.last + 1
        }
        if (pos < norm.length) encodePlain(norm.substring(pos), out)
        return out.toIntArray()
    }

    private fun encodePlain(text: String, out: MutableList<Int>) {
        for (m in SPLIT.findAll(text)) {
            val piece = m.value
            val ids = cache.getOrPut(piece) {
                val mapped = StringBuilder()
                for (b in piece.toByteArray(Charsets.UTF_8)) mapped.append(BYTE_TO_CHAR[b.toInt() and 0xff])
                bpe(mapped.toString())
            }
            ids.forEach { out += it }
        }
    }

    private fun bpe(word: String): IntArray {
        var parts = word.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var best = -1; var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val r = ranks[parts[i] + " " + parts[i + 1]] ?: continue
                if (r < bestRank) { bestRank = r; best = i }
            }
            if (best < 0) break
            val a = parts[best]; val b = parts[best + 1]
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == a && parts[i + 1] == b) { merged += a + b; i += 2 }
                else { merged += parts[i]; i++ }
            }
            parts = merged
        }
        return IntArray(parts.size) { vocab[parts[it]] ?: error("token not in vocab: ${parts[it]}") }
    }

    companion object {
        // GPT-2 split with Unicode whitespace spelled out (JVM \s is ASCII-only and Android's ICU rejects (?U)).
        private const val WS = "\\s\\p{Z}\\u0085"
        private val SPLIT = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^$WS\\p{L}\\p{N}]+|[$WS]+(?![^$WS])|[$WS]+")
        private val BYTE_TO_CHAR: CharArray = CharArray(256).also { map ->
            val direct = (33..126) + (161..172) + (174..255)
            var n = 0
            for (b in 0 until 256) map[b] = if (b in direct) b.toChar() else (256 + n++).toChar()
        }
    }
}
