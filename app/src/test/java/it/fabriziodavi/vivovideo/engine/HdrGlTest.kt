package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrGlTest {
    private val src = """
precision highp float;
uniform sampler2D uTex;
varying vec2 vUv;
void main() {
  if (vUv.x < 0.0) {
    gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
    return;
  }
  vec3 c = texture2D(uTex, vUv).rgb;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

    @Test fun sdrIsUntouched() = assertEquals(src, HdrGl.fragment(src, false))

    @Test fun hdrWrapsInputAndOutput() {
        HdrGl.gain = 10f
        val out = HdrGl.fragment(src, true)
        HdrGl.gain = 1f
        assertTrue(out.contains("const float VV_GAIN = 10.0000;"))
        // Ogni lettura passa da vvTex, ogni scrittura da vvOut; texture2D resta solo dentro vvTex.
        assertEquals(1, Regex("texture2D\\(").findAll(out).count())
        assertEquals(2, Regex("vvTex\\(uTex").findAll(out).count())
        assertEquals(2, Regex("gl_FragColor = vvOut\\(").findAll(out).count())
        assertFalse(out.contains("gl_FragColor = vec4"))
        // Le funzioni vengono subito dopo la precisione, prima del resto.
        assertTrue(out.indexOf("vec4 vvTex") in out.indexOf("precision")..out.indexOf("uniform sampler2D"))
    }

    @Test fun gainFollowsTheMajority() {
        assertEquals(10f, HdrGl.gainFor(3, 1))
        assertEquals(1f, HdrGl.gainFor(1, 3))
        assertEquals(1f, HdrGl.gainFor(0, 0))
    }

    @Test fun presetsKeepHdrExceptWhatsapp() {
        val o = ExportOptions(hdr = true, codec = OutputCodec.H265)
        val yt = ExportPreset.YOUTUBE_HD.applyTo(o)
        assertTrue(yt.hdr); assertEquals(OutputCodec.H265, yt.codec)
        assertTrue(ExportPreset.YOUTUBE_HD.matches(yt))
        val wa = ExportPreset.WHATSAPP.applyTo(o)
        assertFalse(wa.hdr); assertEquals(OutputCodec.H264, wa.codec)
    }
}
