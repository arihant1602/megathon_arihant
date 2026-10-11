package org.seedhecode.incidentassistant.engine

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.File

/**
 * First-launch install of the models bundled in the APK (assets/models/, listed in assets/models/manifest.json) into
 * files/models, where SNPE, whisper.cpp, llama.cpp and sherpa-onnx can open them by path.
 *
 * - No bundled models (development build): nothing happens; models are pushed with scripts/push_models_kit.sh.
 * - Already installed from the same manifest (files/models/.bundle matches): nothing happens.
 * - Otherwise everything is copied into files/models.installing and swapped in atomically, so an interrupted copy is
 *   simply redone on the next launch.
 */
class ModelInstaller(private val context: Context) {
    private val assets = context.assets
    private val target = File(context.filesDir, "models")
    private val marker = File(target, ".bundle")

    private fun manifest(): String? = runCatching { assets.open("models/manifest.json").bufferedReader().use { it.readText() } }.getOrNull()

    /** True if the APK carries models that are not installed yet. */
    fun needed(): Boolean {
        val m = manifest() ?: return false
        return !(marker.isFile && marker.readText() == m)
    }

    /** Copies the bundled models; [progress] gets the fraction done (0..1). Throws with a readable message on failure. */
    fun install(progress: (Float) -> Unit) {
        val text = manifest() ?: return
        val files = JSONObject(text).getJSONArray("files")
        val entries = (0 until files.length()).map { files.getJSONObject(it) }
        val total = entries.sumOf { it.getLong("size") }
        val free = StatFs(context.filesDir.path).availableBytes
        check(free > total + 200L * 1024 * 1024) {
            "Not enough free storage to unpack the models: need ${total / 1_000_000 + 200} MB, have ${free / 1_000_000} MB."
        }
        val staging = File(context.filesDir, "models.installing")
        staging.deleteRecursively()
        var done = 0L
        val buffer = ByteArray(1 shl 20)
        for (e in entries) {
            val path = e.getString("path")
            require(!path.startsWith("/") && !path.contains("..")) { "Bad model path in manifest: $path" }
            val out = File(staging, path)
            out.parentFile!!.mkdirs()
            assets.open("models/$path").use { input ->
                out.outputStream().use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        progress((done.toDouble() / total).toFloat())
                    }
                }
            }
            check(out.length() == e.getLong("size")) { "Model file $path is incomplete (${out.length()} of ${e.getLong("size")} bytes)." }
        }
        File(staging, ".bundle").writeText(text)
        target.deleteRecursively()
        check(staging.renameTo(target)) { "Could not activate the unpacked models." }
        progress(1f)
    }
}
