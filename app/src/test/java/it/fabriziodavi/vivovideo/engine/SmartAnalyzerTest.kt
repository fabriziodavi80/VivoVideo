package it.fabriziodavi.vivovideo.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lettura completa dei video: i fotogrammi si raddrizzano come li vede chi guarda (soggetto e volti al posto giusto). */
class SmartAnalyzerTest {
    @Test fun grayImagesAreTurnedUpright() {
        // 3×2:  1 2 3
        //       4 5 6
        val g = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
        val (r90, w90, h90) = SmartAnalyzer.rotateGray(g, 3, 2, 90)
        assertEquals(2, w90); assertEquals(3, h90)
        // In senso orario: 4 1 / 5 2 / 6 3
        assertArrayEquals(floatArrayOf(4f, 1f, 5f, 2f, 6f, 3f), r90, 0f)
        val (r270, _, _) = SmartAnalyzer.rotateGray(g, 3, 2, 270)
        assertArrayEquals(floatArrayOf(3f, 6f, 2f, 5f, 1f, 4f), r270, 0f)
        assertArrayEquals(floatArrayOf(6f, 5f, 4f, 3f, 2f, 1f), SmartAnalyzer.rotateGray(g, 3, 2, 180).first, 0f)
        assertArrayEquals(g, SmartAnalyzer.rotateGray(g, 3, 2, 0).first, 0f)
    }
}
