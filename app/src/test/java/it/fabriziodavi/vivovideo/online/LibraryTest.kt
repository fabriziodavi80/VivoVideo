package it.fabriziodavi.vivovideo.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lettura delle risposte di Openverse, Jamendo e Freesound, licenze e crediti. */
class LibraryTest {
    private val openverse = """
        {"result_count":2,"page_count":1,"page_size":20,"page":1,"results":[
          {"id":"a1b2","title":"Summer Breeze","creator":"Mario","url":"https://cdn.example/summer.mp3","duration":184000,
           "license":"by","license_version":"4.0","license_url":"https://creativecommons.org/licenses/by/4.0/",
           "foreign_landing_url":"https://www.jamendo.com/track/1","provider":"jamendo","category":"music","genres":["pop"],"extra":{"x":1}},
          {"id":"c3","title":"Dark","creator":"X","url":"https://cdn.example/dark.mp3","duration":null,"license":"by-nc-nd","license_version":"3.0"},
          {"id":"d4","title":"","creator":null,"url":"https://cdn.example/z.mp3","duration":5000,"license":"cc0"}
        ]}""".trimIndent()

    private val jamendo = """
        {"headers":{"status":"success","code":0,"error_message":"","warnings":"","results_count":2},
         "results":[
          {"id":"1532771","name":"Epic Journey","duration":215,"artist_name":"Band","license_ccurl":"http://creativecommons.org/licenses/by-sa/3.0/",
           "audio":"https://prod-1.storage.jamendo.com/?trackid=1532771&format=mp31","audiodownload":"https://prod-1.storage.jamendo.com/download/track/1532771/mp32/",
           "audiodownload_allowed":true,"shareurl":"https://www.jamendo.com/track/1532771","musicinfo":{"vocalinstrumental":"instrumental"}},
          {"id":"2","name":"No DL","duration":"120","artist_name":"Y","license_ccurl":"http://creativecommons.org/licenses/by-nc/3.0/",
           "audio":"https://stream/2","audiodownload":"https://dl/2","audiodownload_allowed":false,"shareurl":"https://j/2"}
         ]}""".trimIndent()

    private val freesound = """
        {"count":2,"next":null,"previous":null,"results":[
          {"id":4567,"name":"whoosh_fast.wav","username":"sfxguy","duration":1.25,"license":"http://creativecommons.org/publicdomain/zero/1.0/",
           "previews":{"preview-hq-mp3":"https://cdn.freesound.org/previews/4/4567_hq.mp3","preview-lq-mp3":"https://cdn.freesound.org/previews/4/4567_lq.mp3"},
           "url":"https://freesound.org/people/sfxguy/sounds/4567/"},
          {"id":89,"name":"rain","username":"r","duration":12.0,"license":"Attribution NonCommercial",
           "previews":{"preview-lq-mp3":"https://cdn/89_lq.mp3"},"url":"https://freesound.org/s/89/"}
        ]}""".trimIndent()

    @Test fun readsOpenverse() {
        val r = Library.parseOpenverse(openverse)
        assertEquals(3, r.size)
        val t = r[0]
        assertEquals("Summer Breeze", t.title); assertEquals("Mario", t.artist)
        assertEquals(184_000L, t.durationMs)
        assertEquals("https://cdn.example/summer.mp3", t.downloadUrl)
        assertEquals("CC BY 4.0", t.license.code)
        assertTrue(t.license.commercial && t.license.derivatives && t.license.attribution)
        assertEquals("Senza titolo", r[2].title)
        assertFalse(r[2].license.attribution)
        // "ND" non si può mettere sotto un video: sparisce sempre.
        assertEquals(listOf("a1b2", "d4"), Library.usable(r, commercialOnly = false).map { it.id })
    }

    @Test fun readsJamendo() {
        val r = Library.parseJamendo(jamendo)
        assertEquals(2, r.size)
        assertEquals(215_000L, r[0].durationMs)
        assertEquals("https://prod-1.storage.jamendo.com/download/track/1532771/mp32/", r[0].downloadUrl)
        assertEquals("CC BY-SA 3.0", r[0].license.code)
        assertEquals("https://stream/2", r[1].downloadUrl) // scaricamento non permesso: si usa lo stream
        assertEquals(120_000L, r[1].durationMs)
        assertEquals(listOf("1532771"), Library.usable(r, commercialOnly = true).map { it.id })
        assertEquals(2, Library.usable(r, commercialOnly = false).size)
        val err = runCatching { Library.parseJamendo("""{"headers":{"status":"failed","code":5,"error_message":"Invalid client id"},"results":[]}""") }
        assertEquals("Invalid client id", err.exceptionOrNull()?.message)
    }

    @Test fun readsFreesound() {
        val r = Library.parseFreesound(freesound)
        assertEquals(2, r.size)
        assertEquals("whoosh_fast", r[0].title)
        assertEquals(1_250L, r[0].durationMs)
        assertEquals("https://cdn.freesound.org/previews/4/4567_hq.mp3", r[0].downloadUrl)
        assertEquals("CC0", r[0].license.code)
        assertEquals("https://cdn/89_lq.mp3", r[1].previewUrl)
        assertFalse(r[1].license.commercial)
        assertTrue(runCatching { Library.parseFreesound("""{"detail":"Invalid token"}""") }.isFailure)
    }

    @Test fun licensesFromEveryForm() {
        assertEquals("CC BY-NC-SA 4.0", LicenseInfo.fromUrl("https://creativecommons.org/licenses/by-nc-sa/4.0/").code)
        assertFalse(LicenseInfo.fromUrl("https://creativecommons.org/licenses/by-nd/4.0/").derivatives)
        assertTrue(LicenseInfo.fromUrl("Creative Commons 0").commercial)
        assertFalse(LicenseInfo.fromCode("nc-sampling+").commercial)
        assertFalse(LicenseInfo.fromCode(null).derivatives) // sconosciuta: meglio non usarla
    }

    @Test fun searchAddressesAndCredits() {
        val o = Library.openverseUrl("epic music", effects = false, commercialOnly = true)
        assertTrue(o.startsWith("https://api.openverse.org/v1/audio/?q=epic+music"))
        assertTrue(o.contains("category=music") && o.contains("license_type=commercial%2Cmodification") || o.contains("license_type=commercial,modification"))
        assertTrue(Library.openverseUrl("", effects = true, commercialOnly = false).contains("category=sound_effect"))
        val j = Library.jamendoUrl("abc", "summer", commercialOnly = true, instrumental = true)
        assertTrue(j.contains("client_id=abc") && j.contains("ccnd=false") && j.contains("ccnc=false") && j.contains("vocalinstrumental=instrumental") && j.contains("search=summer"))
        val f = Library.freesoundUrl("KEY", "rain", effects = true)
        assertTrue(f.contains("token=KEY") && f.contains("query=rain") && f.contains("previews"))
        val t = Library.parseOpenverse(openverse)[0]
        assertEquals("“Summer Breeze” di Mario (Openverse) – CC BY 4.0 – https://www.jamendo.com/track/1", t.credit)
        assertEquals("openverse_a1b2.mp3", t.fileName)
        assertTrue(LibraryKeys(null, "k").has(LibrarySource.FREESOUND))
        assertFalse(LibraryKeys(null, "k").has(LibrarySource.JAMENDO))
        assertTrue(LibraryKeys(null, null).has(LibrarySource.OPENVERSE))
    }

    @Test fun clearErrorMessages() {
        assertTrue(friendlyError(java.net.SocketTimeoutException("timeout"), LibrarySource.OPENVERSE).startsWith("Openverse non risponde"))
        assertEquals("Nessuna connessione a internet.", friendlyError(java.net.UnknownHostException("x"), LibrarySource.JAMENDO))
        assertEquals("Codice errato", friendlyError(IllegalStateException("Codice errato"), LibrarySource.JAMENDO))
    }
}
