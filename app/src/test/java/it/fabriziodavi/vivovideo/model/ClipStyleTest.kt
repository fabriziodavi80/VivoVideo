package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStyleTest {
    private val src = Clip(
        id = "s", uri = "a", sourceDurationUs = 10_000_000, trimEndUs = 4_000_000,
        fx = listOf(ClipFx(FxType.entries.first())), grade = Grade(exposure = 0.5f),
        transitionOut = TransitionSpec(TransitionType.GLITCH, 500_000), fadeInUs = 1_500_000, fadeColor = FadeColor.WHITE,
        blurBg = true, frame = Transform(scale = 1.3f), speed = ClipSpeed.entries.last(), volume = 0.4f,
        keyframes = listOf(Keyframe(0, Transform()), Keyframe(4_000_000, Transform(scale = 2f))),
    )
    private val video = Clip(id = "v", uri = "b", sourceDurationUs = 10_000_000, trimEndUs = 2_000_000)
    private val photo = Clip(id = "p", uri = "c", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true)

    @Test fun presentListsWhatChanged() {
        assertEquals(ClipAttr.entries.toSet(), ClipStyle.present(src))
        assertTrue(ClipStyle.present(video).isEmpty())
    }

    @Test fun pasteCopiesOnlyChosenGroups() {
        val d = ClipStyle.paste(src, video, setOf(ClipAttr.COLOR, ClipAttr.EFFECTS))
        assertEquals(src.grade, d.grade); assertEquals(src.fx, d.fx)
        assertEquals(null, d.transitionOut); assertEquals(1f, d.volume)
        assertEquals(video.uri, d.uri); assertEquals(video.id, d.id); assertEquals(video.trimEndUs, d.trimEndUs)
    }

    @Test fun pasteAdaptsToTheTarget() {
        val d = ClipStyle.paste(src, video, ClipAttr.entries.toSet())
        // Keyframe in proporzione (4 s → 2 s), dissolvenza al massimo metà clip.
        assertEquals(listOf(0L, 2_000_000L), d.keyframes.map { it.timeUs })
        assertTrue(d.fadeInUs <= d.outputUs / 2)
        assertEquals(FadeColor.WHITE, d.fadeColor)
        assertEquals(src.speed, d.speed); assertEquals(0.4f, d.volume)
        // Le foto non prendono velocità e volume.
        val p = ClipStyle.paste(src, photo, ClipAttr.entries.toSet())
        assertEquals(ClipSpeed.NORMAL, p.speed); assertEquals(1f, p.volume)
        assertEquals(src.grade, p.grade); assertTrue(p.blurBg)
    }
}
