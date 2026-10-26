package com.yourapp.iptv.player.vod

import android.app.Application
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VodTracksTest {
    private val fake = VodTestPlayer().apply { tracks = Tracks(listOf(VodTestPlayer.group("de","de"),
        VodTestPlayer.group("en","en"),VodTestPlayer.group("bad","fr",supported = false),VodTestPlayer.group("ar","ar",text = true))) }
    private val names = TrackNameProvider { it.label ?: "Track" }
    private val preferences = VodTrackPreferences()
    @Test fun supportedAudioAndSubtitleChoicesComeFromTheActualPreparedMedia() {
        assertEquals(listOf("de","en"),vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).choices.map { it.name })
        assertEquals(listOf("ar"),vodTracks(fake.player,C.TRACK_TYPE_TEXT,names).choices.map { it.name })
    }
    @Test fun choosingAudioOverridesOnlyAudioAndAutomaticClearsTheExplicitOverride() {
        val audio = vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).choices.last()
        preferences.select(fake.player,C.TRACK_TYPE_AUDIO,audio)
        assertEquals(listOf("en"),vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).choices.filter { it.chosen }.map { it.name })
        assertFalse(vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).automatic)
        preferences.select(fake.player,C.TRACK_TYPE_AUDIO,null)
        assertTrue(vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).automatic);assertTrue(fake.parameters.overrides.isEmpty())
    }
    @Test fun subtitleOffAutomaticAndSessionLanguagePreferencesSurviveTheNextEpisode() {
        preferences.select(fake.player,C.TRACK_TYPE_AUDIO,vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).choices.last())
        preferences.disableText(fake.player);assertTrue(vodTracks(fake.player,C.TRACK_TYPE_TEXT,names).disabled)
        val next = VodTestPlayer();preferences.apply(next.player)
        assertEquals(listOf("en"),next.parameters.preferredAudioLanguages)
        assertTrue(C.TRACK_TYPE_TEXT in next.parameters.disabledTrackTypes)
        preferences.select(next.player,C.TRACK_TYPE_TEXT,null)
        assertFalse(C.TRACK_TYPE_TEXT in next.parameters.disabledTrackTypes)
    }
    @Test fun aStaleTrackDialogCannotOverrideTheProviderNewTrackGroups() {
        val old = vodTracks(fake.player,C.TRACK_TYPE_AUDIO,names).choices.first()
        fake.tracks = Tracks.EMPTY;preferences.select(fake.player,C.TRACK_TYPE_AUDIO,old)
        assertTrue(fake.parameters.overrides.isEmpty())
    }
}