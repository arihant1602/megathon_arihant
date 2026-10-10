package org.seedhecode.incidentassistant.assistant

import org.json.JSONObject
import kotlin.math.exp

/** Runs the exported Laya graph: int32 ids/mask [1,128] -> one concatenated logits tensor. */
interface LayaRunner : AutoCloseable {
    fun run(ids: IntArray, mask: IntArray): FloatArray
}

data class SentenceInfo(val turn: Int, val text: String, val relevant: Double, val fields: List<String>)
data class Classification(val probs: Map<String, Double>, val sentences: List<SentenceInfo>, val ms: Double, val tokens: Int)

/** Fine-tuned Laya: input layout and output decoding as in laya/common.py build_samples + assistant.Classifier.analyse. */
class LayaClassifier(private val tokenizer: LayaTokenizer, labelsJson: String, private val runner: LayaRunner) : AutoCloseable {
    private val labels = JSONObject(labelsJson)
    val types = labels.getJSONArray("types").strings()
    val fields = labels.getJSONArray("fields").strings()
    private val temperature = labels.getDouble("temperature")

    class Sample(val ids: IntArray, val mask: IntArray, val sentIndex: IntArray, val kept: Int)

    /** Only the user's sentences: [CLS] turn1 sents [SEP] turn2 sents [SEP] ..., newest turns kept, padded to 128. */
    fun sample(turns: List<List<String>>): Sample {
        val chunks = ArrayList<Pair<IntArray, Boolean>>()   // (ids, isSentence)
        for (t in turns) {
            for (s in t) chunks += tokenizer.encode(" $s") to true
            chunks += intArrayOf(SEP) to false
        }
        while (chunks.sumOf { it.first.size } > L - 1) chunks.removeAt(0)
        val ids = IntArray(L) { PAD }; val mask = IntArray(L); val sent = IntArray(L) { -1 }
        ids[0] = CLS; mask[0] = 1
        var n = 1; var k = 0
        for ((tok, isSentence) in chunks) {
            if (isSentence) k++
            for (id in tok) { ids[n] = id; mask[n] = 1; sent[n] = if (isSentence) k - 1 else -1; n++ }
        }
        return Sample(ids, mask, sent, k)
    }

    fun classify(turns: List<List<String>>): Classification {
        val s = sample(turns)
        val t0 = System.nanoTime()
        val out = runner.run(s.ids, s.mask)
        val ms = (System.nanoTime() - t0) / 1e6
        return analyse(turns, s, out, ms)
    }

    fun analyse(turns: List<List<String>>, s: Sample, out: FloatArray, ms: Double): Classification {
        val nt = types.size; val nf = fields.size
        require(out.size == nt + L * 2 + L * nf) { "Laya output has ${out.size} values, expected ${nt + L * 2 + L * nf}" }
        val z = DoubleArray(nt) { out[it] / temperature }
        val mx = z.max(); val e = z.map { exp(it - mx) }; val sum = e.sum()
        val probs = types.indices.associate { types[it] to e[it] / sum }
        val flat = turns.flatMapIndexed { k, t -> t.map { k to it } }
        val kept = if (s.kept > 0) flat.takeLast(s.kept) else emptyList()
        val sentences = kept.mapIndexed { j, (k, text) ->
            val toks = (0 until L).filter { s.sentIndex[it] == j }
            val r0 = toks.map { out[nt + it * 2].toDouble() }.average()
            val r1 = toks.map { out[nt + it * 2 + 1].toDouble() }.average()
            val rel = 1.0 / (1.0 + exp(r0 - r1))
            val fl = (0 until nf).filter { f ->
                val m = toks.map { out[nt + L * 2 + it * nf + f].toDouble() }.average()
                1.0 / (1.0 + exp(-m)) > 0.5
            }.map { fields[it] }
            SentenceInfo(k, text, rel, fl)
        }
        return Classification(probs, sentences, ms, s.mask.sum())
    }

    override fun close() = runner.close()

    companion object {
        const val L = 128; const val CLS = 50281; const val SEP = 50282; const val PAD = 50283
    }
}
