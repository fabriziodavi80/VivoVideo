package it.fabriziodavi.vivovideo.engine

/**
 * Esportazione HDR: la libreria passa agli effetti la luce "lineare" (BT.2020, valori anche oltre la scala SDR).
 * I nostri shader sono pensati per colori normali (con gamma): in HDR si avvolgono in una conversione
 * lineare → gamma all'ingresso e gamma → lineare all'uscita, così filtri, transizioni e correzioni
 * hanno lo stesso aspetto che in SDR.
 */
object HdrGl {
    /**
     * Scala della luce lineare: con HLG 1.0 è il picco (circa 1000 nit); con HDR10 (PQ) 1.0 vale 10000 nit,
     * quindi ×10 per riportarla alla stessa scala. Si imposta prima di ogni esportazione.
     */
    @Volatile var gain: Float = 1f

    /** Scala adatta al progetto: [pqClips] clip HDR10 contro [hlgClips] clip HLG. */
    fun gainFor(pqClips: Int, hlgClips: Int): Float = if (pqClips > hlgClips) 10f else 1f

    private val fragColor = Regex("""gl_FragColor\s*=\s*(.+?);\s*$""", RegexOption.MULTILINE)

    /** Lo shader [src] com'è (SDR) o avvolto per l'HDR. */
    fun fragment(src: String, useHdr: Boolean): String {
        if (!useHdr) return src
        val g = "%.4f".format(java.util.Locale.US, gain)
        val helpers = """
const float VV_GAIN = $g;
vec4 vvIn(vec4 c) { return vec4(pow(max(c.rgb * VV_GAIN, vec3(0.0)), vec3(1.0 / 2.4)), c.a); }
vec4 vvOut(vec4 c) { return vec4(pow(max(c.rgb, vec3(0.0)), vec3(2.4)) / VV_GAIN, c.a); }
vec4 vvTex(sampler2D s, vec2 uv) { return vvIn(texture2D(s, uv)); }
"""
        var body = src.replace("texture2D(", "vvTex(")
        body = fragColor.replace(body) { m -> "gl_FragColor = vvOut(${m.groupValues[1]});" }
        val anchor = "precision highp float;"
        val i = body.indexOf(anchor)
        return if (i >= 0) body.substring(0, i + anchor.length) + "\n" + helpers + body.substring(i + anchor.length)
        else "precision highp float;\n$helpers$body"
    }
}
