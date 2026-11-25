package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Settings limit, the mobile-data limit and the Quality-menu pick. */
class VideoQualityTest {

    @Test
    fun `the mobile-data limit counts only on a metered connection, and the lower limit wins`() {
        assertNull(VideoQuality.cap(maxHeight = 0, mobileDataMaxHeight = 720, metered = false))
        assertEquals(720, VideoQuality.cap(maxHeight = 0, mobileDataMaxHeight = 720, metered = true))
        assertEquals(480, VideoQuality.cap(maxHeight = 480, mobileDataMaxHeight = 720, metered = true))
        assertEquals(1080, VideoQuality.cap(maxHeight = 1080, mobileDataMaxHeight = 0, metered = true))
    }

    @Test
    fun `an engine's own limit can only lower the result`() {
        assertEquals(360, VideoQuality.lower(360, 1080))
        assertEquals(720, VideoQuality.lower(null, 720))
        assertNull(VideoQuality.lower(null, null))
    }

    @Test
    fun `the menu lists distinct heights, highest first, and only when there is a choice`() {
        assertEquals(listOf(1080, 720, 360), VideoQuality.heights(listOf(720, 1080, 360, 720, 0)))
        assertEquals(emptyList<Int>(), VideoQuality.heights(listOf(1080, 1080)))
    }

    @Test
    fun `live variants of the same height and bitrate remain independently selectable`() {
        val group = videoGroup("hls", video("primary", 1080), video("backup", 1080))
        val tracks = Tracks(listOf(group))
        val variants = VideoQuality.variantsOf(tracks)

        assertEquals(listOf(1080, 1080), variants.map { it.height })
        assertEquals(listOf(0), VideoQuality.overrideFor(tracks, variants[0])?.trackIndices)
        assertEquals(listOf(1), VideoQuality.overrideFor(tracks, variants[1])?.trackIndices)
        assertEquals(group.mediaTrackGroup, VideoQuality.overrideFor(tracks, variants[1])?.mediaTrackGroup)
        assertEquals(emptyList<Int>(), VideoQuality.heightsOf(tracks))
        assertNull(VideoQuality.overrideFor(Tracks(listOf(videoGroup("new", video("other", 1080)))), variants[1]))
    }

    @Test
    fun `live variants omit unsupported tracks and require a choice`() {
        val group = TrackGroup("hls", video("primary", 1080), video("unsupported", 720))
        val tracks = Tracks(listOf(Tracks.Group(
            group, false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_SUBTYPE), booleanArrayOf(false, false),
        )))
        assertEquals(emptyList<VideoQuality.Variant>(), VideoQuality.variantsOf(tracks))
    }

    private fun video(id: String, height: Int): Format = Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.VIDEO_H264).setHeight(height).setPeakBitrate(5_000_000).build()

    private fun videoGroup(id: String, vararg formats: Format): Tracks.Group =
        Tracks.Group(TrackGroup(id, *formats), false, IntArray(formats.size) { C.FORMAT_HANDLED }, BooleanArray(formats.size))

    @Test
    fun `mpv takes the pick, else the tallest within the limit, else the smallest`() {
        val tracks = listOf(1 to 1080, 2 to 720, 3 to 480)
        assertEquals(3, VideoQuality.mpvTrack(tracks, cap = 1080, pick = 480))
        assertEquals(2, VideoQuality.mpvTrack(tracks, cap = 720, pick = null))
        assertEquals(3, VideoQuality.mpvTrack(tracks, cap = 360, pick = null))
        assertNull(VideoQuality.mpvTrack(tracks, cap = null, pick = null))
        assertNull(VideoQuality.mpvTrack(listOf(1 to 1080, 2 to 0), cap = 720, pick = null))
    }

    // HDR 2160 (1), SDR 2160 (2), SDR 1080 (3), HDR 1080 (4) — "Prefer SDR" allows 2 and 3.
    private val mixed = listOf(1 to 2160, 2 to 2160, 3 to 1080, 4 to 1080)

    @Test
    fun `with SDR allowed, a choice is always made inside it, even with no limit or pick`() {
        assertEquals(2, VideoQuality.mpvTrackWithin(mixed, allowed = setOf(2, 3), cap = null, pick = null))
        assertEquals(3, VideoQuality.mpvTrackWithin(mixed, allowed = setOf(2, 3), cap = 1080, pick = null))
        assertEquals(3, VideoQuality.mpvTrackWithin(mixed, allowed = setOf(2, 3), cap = null, pick = 1080))
    }

    @Test
    fun `one SDR track among HDR ones is still chosen`() {
        assertEquals(3, VideoQuality.mpvTrackWithin(mixed, allowed = setOf(3), cap = null, pick = null))
    }

    @Test
    fun `nothing allowed, or everything allowed, is the plain choice`() {
        assertNull(VideoQuality.mpvTrackWithin(mixed, allowed = null, cap = null, pick = null))
        assertNull(VideoQuality.mpvTrackWithin(mixed, allowed = setOf(1, 2, 3, 4), cap = null, pick = null))
        assertEquals(3, VideoQuality.mpvTrackWithin(mixed, allowed = setOf(9), cap = 1080, pick = null))
    }
}
