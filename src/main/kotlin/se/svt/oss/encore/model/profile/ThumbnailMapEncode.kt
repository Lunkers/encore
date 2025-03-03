// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.analyzedVideo
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.stringValue
import se.svt.oss.mediaanalyzer.file.toFractionOrNull
import kotlin.math.round

data class ThumbnailMapEncode(
    val tileWidth: Int = 160,
    val tileHeight: Int = 90,
    val cols: Int = 12,
    val rows: Int = 20,
    val optional: Boolean = true,
    val suffix: String = "_${cols}x${rows}_${tileWidth}x${tileHeight}_thumbnail_map",
    val format: String = "jpg",
    val inputLabel: String = DEFAULT_VIDEO_LABEL
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(job: EncoreJob, audioMixPresets: Map<String, AudioMixPreset>): Output? {
        val videoStream = job.inputs.analyzedVideo(inputLabel)?.highestBitrateVideoStream
            ?: return logOrThrow("No input with label $inputLabel!")
        var numFrames = videoStream.numFrames
        val duration = job.duration
        if (job.duration != null || job.seekTo != null) {
            val frameRate = videoStream.frameRate.toFractionOrNull()?.toDouble()
                ?: return logOrThrow("Can not generate thumbnail map $suffix! No framerate detected in video input $inputLabel.")
            if (duration != null) {
                numFrames = round(duration * frameRate).toInt()
            } else {
                job.seekTo?.let { numFrames -= round(it * frameRate).toInt() }
            }
        }

        if (numFrames < cols * rows) {
            val message =
                "Video input $inputLabel did not contain enough frames to generate thumbnail map $suffix: $numFrames < $cols cols * $rows rows"
            return logOrThrow(message)
        }
        val pad = "aspect=${Fraction(tileWidth, tileHeight).stringValue()}:x=(ow-iw)/2:y=(oh-ih)/2" // pad to aspect ratio
        val nthFrame = numFrames / (cols * rows)
        var select = "not(mod(n\\,$nthFrame))"
        job.seekTo?.let { select += "*gte(t\\,$it)" }
        return Output(
            id = "$suffix.$format",
            video = VideoStreamEncode(
                params = listOf("-frames", "1", "-q:v", "5"),
                filter = "select=$select,pad=$pad,scale=-1:$tileHeight,tile=${cols}x$rows",
                inputLabels = listOf(inputLabel)
            ),
            audio = null,
            output = "${job.baseName}$suffix.$format",
            seekable = false
        )
    }

    private fun logOrThrow(message: String): Output? {
        if (optional) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
