package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.FxCategory
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.PersonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Effetti "Social 2026" (reel e stories): shader a parte, quasi tutti a tempo di musica. */
class SocialFxTest {
    private val social = FxType.entries.filter { it.category == FxCategory.SOCIAL }

    @Test fun nineSocialEffectsInTheirOwnShader() {
        assertEquals(9, social.size)
        assertEquals(social.toSet(), SocialFxEffect.UNIFORMS.keys)
        assertTrue(social.none { it.wow2 || it in FX_UNIFORMS.keys })
        // Ogni uniforme è dichiarata e usata nello shader, ogni effetto ha il suo blocco.
        SocialFxEffect.UNIFORMS.forEach { (t, u) ->
            assertTrue("$u manca", Regex("\\b$u\\b").findAll(SocialFxEffect.FRAGMENT).count() >= 2)
            assertTrue("FX_${t.name} manca", SocialFxEffect.FRAGMENT.contains("defined(FX_${t.name})"))
        }
        assertTrue(FxType.QUADBEAT.toggle && FxType.STEPZOOM.onBeats && FxType.ECHOBEAT.onBeats && !FxType.THERMAL.onBeats)
        // La scheda Social è la prima del pannello effetti.
        assertEquals(FxCategory.SOCIAL, FxCategory.entries.first())
    }

    @Test fun neededOnlyWithSocialEffects() {
        assertTrue(SocialFxEffect.needed(listOf(ClipFx(FxType.CHROME))))
        assertFalse(SocialFxEffect.needed(listOf(ClipFx(FxType.BW), ClipFx(FxType.HALATION))))
        assertFalse(SocialFxEffect.needed(listOf(ClipFx(FxType.AURA, 0f))))
    }

    @Test fun beatsFromMusic() {
        val fx = SocialFxEffect(listOf(ClipFx(FxType.STEPZOOM)), listOf(1_000_000, 1_500_000, 2_000_000))
        assertEquals(-1, fx.beatIndex(500_000))
        assertEquals(0, fx.beatIndex(1_000_000))
        assertEquals(1, fx.beatIndex(1_700_000))
        assertEquals(2, fx.beatIndex(9_000_000))
        assertEquals(0f, fx.pulse(500_000, 0.1f), 0f)
        assertEquals(1f, fx.pulse(1_500_000, 0.1f), 1e-4f)
        assertTrue(fx.pulse(1_550_000, 0.1f) < 0.7f)
    }

    @Test fun withoutMusicABeatEveryHalfSecondFromClipStart() {
        val fx = SocialFxEffect(listOf(ClipFx(FxType.QUADBEAT)), emptyList(), clipStartUs = 3_000_000)
        assertEquals(0, fx.beatIndex(3_100_000))
        assertEquals(1, fx.beatIndex(3_500_000))
        assertEquals(4, fx.beatIndex(5_200_000))
        assertEquals(1f, fx.pulse(4_000_000, 0.1f), 1e-4f)
    }

    @Test fun viralThemeUsesSocialEffectsAndStickerOutlineExists() {
        assertTrue(AutoStyle.VIRALE.accents.any { it.type.category == FxCategory.SOCIAL })
        assertEquals(4, PersonMode.STICKER.ordinal)
        assertEquals("Contorno sticker", PersonMode.STICKER.label)
    }
}
