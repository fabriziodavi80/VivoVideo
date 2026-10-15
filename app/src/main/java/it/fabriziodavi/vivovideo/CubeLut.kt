package it.fabriziodavi.vivovideo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.effect.SingleColorLut
import java.io.InputStream
import kotlin.math.roundToInt

/** LUT 3D letta da un file .cube (formato Adobe/Resolve). */
class CubeLut(val name: String, val size: Int, private val cube: Array<Array<IntArray>>) {
    fun toEffect(): SingleColorLut = SingleColorLut.createFromCube(cube)

    companion object {
        const val BUILT_IN_LOG = "luts/vivo_log_to_rec709.cube"

        fun fromAsset(context: Context, path: String, name: String): CubeLut =
            context.assets.open(path).use { parse(it, name) }

        fun fromUri(context: Context, uri: Uri): CubeLut {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "LUT"
            return context.contentResolver.openInputStream(uri)!!.use { parse(it, name.removeSuffix(".cube")) }
        }

        fun parse(input: InputStream, name: String): CubeLut {
            var size = 0
            var min = floatArrayOf(0f, 0f, 0f)
            var max = floatArrayOf(1f, 1f, 1f)
            val values = ArrayList<FloatArray>()
            input.bufferedReader().forEachLine { raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachLine
                val parts = line.split(Regex("\\s+"))
                when (parts[0].uppercase()) {
                    "TITLE" -> {}
                    "LUT_1D_SIZE" -> error("Le LUT 1D non sono supportate: serve una LUT 3D")
                    "LUT_3D_SIZE" -> size = parts[1].toInt()
                    "DOMAIN_MIN" -> min = FloatArray(3) { parts[it + 1].toFloat() }
                    "DOMAIN_MAX" -> max = FloatArray(3) { parts[it + 1].toFloat() }
                    else -> {
                        val v = parts.take(3).mapNotNull { it.toFloatOrNull() }
                        if (v.size == 3) values += v.toFloatArray()
                    }
                }
            }
            require(min.all { it == 0f } && max.all { it == 1f }) { "LUT con DOMAIN diverso da 0–1 non supportata" }
            require(size in 2..129) { "Dimensione LUT non valida ($size)" }
            require(values.size == size * size * size) {
                "Il file ha ${values.size} valori, ne servono ${size * size * size}"
            }
            // Nel .cube il rosso varia più velocemente; Media3 vuole cube[R][G][B] in ARGB.
            val cube = Array(size) { Array(size) { IntArray(size) } }
            var i = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val v = values[i++]
                fun ch(c: Int) = (v[c].coerceIn(0f, 1f) * 255f).roundToInt()
                cube[r][g][b] = (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
            }
            return CubeLut(name, size, cube)
        }
    }
}
