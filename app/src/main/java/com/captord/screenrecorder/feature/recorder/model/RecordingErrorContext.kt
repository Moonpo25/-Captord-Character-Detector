package com.captord.screenrecorder.feature.recorder.model

/**
 * Structured description of the configuration that was active when the
 * recorder failed. The HomeScreen error banner renders this block below
 * the raw `lastErrorMessage` so the user can see at a glance which
 * codec / resolution / frame-rate combination the engine was attempting
 * before the failure — directly addressing the "unclear error" gap
 * called out in GitHub issue #1 and the codec-error reproduction in
 * issue #2.
 *
 * The context is only populated for engine-start errors where
 * [ScreenRecorderService] has a known [RecordingConfig]; transient
 * errors mid-recording (e.g. muxer stop failure) keep the existing
 * message-only display.
 */
data class RecordingErrorContext(
    val engine: String,
    val codec: String,
    val resolution: String,
    val frameRate: Int,
    val bitrateBps: Int,
    val audioSource: String,
)