package com.captord.screenrecorder.feature.recorder.model

import android.os.Build

data class CapabilityReport(
    val supportsH265: Boolean,
    val supportedFrameRates: List<Int>,
    val maxResolution: Int,
    val supportsInternalAudio: Boolean,
    val supportsPause: Boolean,
    val androidApi: Int,
    val supportsShowTouches: Boolean,
    val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    /**
     * Maximum video bitrate the device's hardware encoder actually accepts, in bps.
     * Populated by [com.captord.screenrecorder.core.capability.CapabilityChecker.probe];
     * [Int.MAX_VALUE] means "not probed / unbounded". Used by [RecordingConfig.validate]
     * and the recorder engines to reject user values that the encoder cannot accept.
     */
    val maxVideoBitrateBps: Int = Int.MAX_VALUE,
) {
    companion object {
        fun probeMinimal(): CapabilityReport = CapabilityReport(
            supportsH265 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportedFrameRates = RecordingConfig.ALLOWED_FRAME_RATES.sorted(),
            maxResolution = 1080,
            supportsInternalAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportsPause = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            androidApi = Build.VERSION.SDK_INT,
            supportsShowTouches = false,
            supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            maxVideoBitrateBps = RecordingConfig.MAX_VIDEO_BITRATE,
        )
    }
}
