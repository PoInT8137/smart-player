package app.tvplayer.player.afr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatcherTest {

    // Типичный набор режимов 4K-приставки (Amlogic) на телевизоре с HDMI 2.1
    private val uhd = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
        .mapIndexed { i, hz -> DisplayModeInfo(id = i + 1, width = 3840, height = 2160, refreshRate = hz) }
    private val fhd60 = DisplayModeInfo(id = 100, width = 1920, height = 1080, refreshRate = 60f)
    private val current60 = uhd.first { it.refreshRate == 60f }

    private fun pick(fps: Float, current: DisplayModeInfo = current60, modes: List<DisplayModeInfo> = uhd) =
        FrameRateMatcher.bestMode(fps, current, modes)?.refreshRate

    @Test fun film23976GoesTo23976() = assertEquals(23.976f, pick(23.976f))
    @Test fun film24GoesTo24() = assertEquals(24f, pick(24f))
    @Test fun pal25GoesTo25() = assertEquals(25f, pick(25f))
    @Test fun ntsc2997GoesTo2997() = assertEquals(29.97f, pick(29.97f))
    @Test fun video50GoesTo50() = assertEquals(50f, pick(50f))

    @Test fun video5994StaysOn5994WhenAlreadyThere() =
        assertNull(pick(59.94f, current = uhd.first { it.refreshRate == 59.94f }))

    @Test fun alreadyMatchingModeIsNotSwitched() =
        assertNull(pick(24f, current = uhd.first { it.refreshRate == 24f }))

    @Test fun prefersDoubleRateWhenNoExact() {
        val modes = listOf(50f, 60f).mapIndexed { i, hz -> DisplayModeInfo(i, 3840, 2160, hz) }
        assertEquals(50f, pick(25f, current = modes[1], modes = modes))
    }

    @Test fun film23976FallsBackTo24WhenNo23976() {
        val modes = listOf(24f, 60f).mapIndexed { i, hz -> DisplayModeInfo(i, 3840, 2160, hz) }
        assertEquals(24f, pick(23.976f, current = modes[1], modes = modes))
    }

    @Test fun neverChangesResolution() =
        assertNull(pick(24f, current = fhd60, modes = uhd + fhd60))

    @Test fun noSuitableModeKeepsCurrent() {
        val modes = listOf(DisplayModeInfo(1, 1920, 1080, 60f))
        assertNull(pick(24f, current = modes[0], modes = modes))
    }

    @Test fun normalizeSnapsMeasuredValues() {
        assertEquals(23.976f, FrameRateMatcher.normalize(23.9762f))
        assertEquals(24f, FrameRateMatcher.normalize(24.004f))
        assertEquals(29.97f, FrameRateMatcher.normalize(29.969f))
        assertNull(FrameRateMatcher.normalize(37f))
    }

    @Test fun formatRates() {
        assertEquals("23.976", FrameRateMatcher.format(23.976f))
        assertEquals("24", FrameRateMatcher.format(24f))
        assertEquals("29.97", FrameRateMatcher.format(29.97f))
        assertEquals("59.94", FrameRateMatcher.format(59.94f))
    }
}
