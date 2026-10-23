package it.fabriziodavi.vivovideo.engine

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.PersonFx
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun project(): Project {
        val photo = File(app.filesDir, "photos/p1.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(3000) { it.toByte() }) }
        val mask = File(app.filesDir, "maschere/m.bin").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(100) { 7 }) }
        return Project(
            id = "orig", name = "Gita",
            clips = listOf(
                Clip(id = "v", uri = "content://media/external/video/media/42", sourceDurationUs = 5_000_000, fileName = "VID_1.mp4", fileSize = 123,
                    person = PersonFx(maskFile = mask.absolutePath, count = 3)),
                Clip(id = "p", uri = Uri.fromFile(photo).toString(), sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 2_000_000, isPhoto = true),
            ),
            texts = listOf(TextItem(text = "/files/ non è un file")),
            music = listOf(MusicTrack(uri = "content://com.android.providers/doc/9", name = "brano.mp3", sourceDurationUs = 10_000_000)),
        )
    }

    @Test fun refsFindEveryFileInAnyField() {
        val r = ProjectRefs.refs(project())
        assertEquals(4, r.size)
        assertTrue(r.any { it.endsWith("m.bin") && it.startsWith("/") })
        assertFalse(r.any { it.contains("non è un file") })
        val q = ProjectRefs.replace(project()) { if (it == "content://media/external/video/media/42") "content://nuovo/1" else null }
        assertEquals("content://nuovo/1", q.clips[0].uri)
        assertEquals(project().clips[1].uri, q.clips[1].uri)
    }

    @Test fun lightBackupRoundTrip() {
        val p = project()
        val out = ByteArrayOutputStream()
        Backup.export(app, p, withMedia = false, out = out) {}
        val imp = Backup.import(app, ByteArrayInputStream(out.toByteArray()), existingIds = setOf("orig"))
        val q = imp.project
        // Nuovo progetto (l'originale c'è già), stessi contenuti.
        assertNotEquals("orig", q.id); assertTrue(q.name.endsWith("(copia)"))
        assertEquals(p.durationUs, q.durationUs)
        // I file dell'app sono stati ricopiati altrove, con lo stesso contenuto.
        val photo = File(Uri.parse(q.clips[1].uri).path!!)
        assertTrue(photo.exists()); assertTrue(photo.absolutePath.contains("importati"))
        assertEquals(3000L, photo.length())
        val mask = File(q.clips[0].person!!.maskFile)
        assertTrue(mask.exists()); assertEquals(100L, mask.length())
        // I video della galleria restano indirizzi (non inclusi), con il loro nome per ritrovarli.
        assertEquals(p.clips[0].uri, q.clips[0].uri)
        assertEquals(listOf("content://media/external/video/media/42", "content://com.android.providers/doc/9").toSet(), imp.media.map { it.uri }.toSet())
        assertEquals(p.texts, q.texts)
    }

    @Test fun missingFilesAreReported() {
        val p = project()
        val miss = Backup.missing(app, p)
        assertTrue(p.clips[0].uri in miss)
        assertFalse(p.clips[1].uri in miss)
        File(Uri.parse(p.clips[1].uri).path!!).delete()
        assertTrue(p.clips[1].uri in Backup.missing(app, p))
    }

    @Test fun renamedCopiesStillMatch() {
        assertTrue(Backup.sameFile("VID_2026.mp4", "VID_2026.mp4"))
        assertTrue(Backup.sameFile("VID_2026.mp4", "vid_2026.MP4"))
        assertTrue(Backup.sameFile("VID_2026.mp4", "VID_2026 (1).mp4"))
        assertTrue(Backup.sameFile("VID_2026.mp4", "VID_2026_1.mp4"))
        assertTrue(Backup.sameFile("VID_2026.mp4", "VID_2026 - copia.mp4") || Backup.sameFile("VID_2026.mp4", "VID_2026-copia.mp4"))
        assertTrue(!Backup.sameFile("VID_2026.mp4", "VID_20261.mp4"))
        assertTrue(!Backup.sameFile("VID_2026.mp4", "VID_2026.mov"))
        assertTrue(!Backup.sameFile("VID_2026.mp4", "VID_2026_vacanze.mp4"))
    }
}
