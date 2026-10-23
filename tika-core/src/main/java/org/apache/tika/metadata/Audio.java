/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.metadata;

/**
 * Audio metadata properties that have no XMPDM equivalent. XMPDM defines
 * {@link XMPDM#TRACK_NUMBER} and {@link XMPDM#DISC_NUMBER} but no properties
 * for the totals, although the common audio containers all carry them.
 * See TIKA-4779.
 *
 * @since Apache Tika 4.0.0
 */
public interface Audio {

    /**
     * Total number of tracks on the album / in the set
     * (MP4 'trkn' second value, ID3 TRCK "n/total", Vorbis TRACKTOTAL).
     */
    Property TRACK_COUNT = Property.internalInteger("audio:track-count");

    /**
     * Total number of discs in the set
     * (MP4 'disk' second value, ID3 TPOS "n/total", Vorbis DISCTOTAL).
     */
    Property DISC_COUNT = Property.internalInteger("audio:disc-count");

    /**
     * The track value exactly as tagged (e.g. "3/12" or a non-numeric form
     * like vinyl "A1"). {@link XMPDM#TRACK_NUMBER} only receives clean
     * integers, so nothing is lost.
     */
    Property RAW_TRACK_NUMBER = Property.internalText("audio:raw-track-number");

    /**
     * The disc value exactly as tagged, see {@link #RAW_TRACK_NUMBER}.
     */
    Property RAW_DISC_NUMBER = Property.internalText("audio:raw-disc-number");

    /**
     * Average or nominal bitrate in bits per second (averaged over the MP3
     * frames, the Vorbis nominal bitrate, or the MP4 'esds' average bitrate).
     * A per-stream value: in a file with several audio tracks it reflects
     * the last sound track's sample description.
     */
    Property BITRATE = Property.internalInteger("audio:bitrate");

    /**
     * True if the stream is variable bitrate: the MP3 frames declare differing
     * bitrates, or the Vorbis identification header does not declare one fixed
     * rate for upper, nominal and lower.
     */
    Property IS_VARIABLE_BITRATE = Property.internalBoolean("audio:is-variable-bitrate");

    /**
     * True if the container declares DRM protection through a protected
     * sample entry format such as 'drms' or 'enca'. A file-level flag: any
     * protected audio track sets it. Only set when protection is detected.
     */
    Property HAS_DRM = Property.internalBoolean("audio:has-drm");

    /**
     * Number of audio channels (e.g. 2 for stereo). {@link XMPDM#AUDIO_CHANNEL_TYPE}
     * only distinguishes Mono from Stereo and cannot represent more than two
     * channels. A per-stream value, see {@link #BITRATE}.
     */
    Property CHANNELS = Property.internalInteger("audio:channels");

    /**
     * Audio sample size in bits (e.g. 16). A per-stream value, see {@link #BITRATE}.
     */
    Property BITS_PER_SAMPLE = Property.internalInteger("audio:bits-per-sample");

    /**
     * The audio track's codec as a four-character code (FourCC), as used by
     * QuickTime/MP4 sample descriptions, AVI and other containers: e.g. "mp4a"
     * for MPEG-4 audio (AAC), "alac", "ac-3". For protected MP4 streams, where
     * {@link #HAS_DRM} is also set, this is the original codec named by the
     * protection scheme info ('sinf'/'frma'), or the protected sample entry format
     * ("drms"/"enca") if there is none. Trailing padding spaces are trimmed and
     * non-printable codes are not exposed. A per-stream value: with several audio
     * tracks or sample entries it reflects the last one.
     */
    Property FOURCC = Property.internalText("audio:fourcc");

    /**
     * The raw javax.sound encoding name (e.g. "PCM_SIGNED"), as reported by {@code
     * AudioFormat#getEncoding()} (AudioParser). Distinct from {@link XMPDM#AUDIO_SAMPLE_TYPE}
     * (bit depth) and {@link XMPDM#AUDIO_COMPRESSOR} (codec name).
     */
    Property ENCODING = Property.internalText("audio:encoding");

    // javax.sound SPI standard properties (AudioParser, TIKA-4816): the keys documented by
    // javax.sound.sampled.AudioFileFormat#properties()/AudioFormat#properties(), promoted from
    // an ad-hoc String key to a curated Property. Tika's built-in providers (WAV/AIFF/AU/basic)
    // populate none of these -- they are only ever non-empty when a third-party javax.sound SPI
    // is on the classpath. Values are stored verbatim (Object#toString()), not reinterpreted
    // into another property's unit or format (e.g. NOT folded into XMPDM#DURATION, which is
    // seconds, not the SPI's microseconds). "bitrate"/"vbr" are format-compatible with the
    // existing #BITRATE/#IS_VARIABLE_BITRATE and reuse them instead of duplicating. Any SPI
    // property name outside this stock vocabulary is residual and goes through AudioParser's
    // own `audio:` KeyPrefix.

    /** Stock "duration": playback duration in microseconds (java.lang.Long). */
    Property SPI_DURATION = Property.internalText("audio:spi-duration");

    /** Stock "author": name of the file's author. */
    Property SPI_AUTHOR = Property.internalText("audio:spi-author");

    /** Stock "title": title of the file. */
    Property SPI_TITLE = Property.internalText("audio:spi-title");

    /** Stock "copyright": copyright message. */
    Property SPI_COPYRIGHT = Property.internalText("audio:spi-copyright");

    /** Stock "date": date of the recording or release (java.util.Date#toString() form). */
    Property SPI_DATE = Property.internalText("audio:spi-date");

    /** Stock "comment": arbitrary text. */
    Property SPI_COMMENT = Property.internalText("audio:spi-comment");

    /** Stock "quality": encoding/conversion quality, 1..100. */
    Property SPI_QUALITY = Property.internalInteger("audio:spi-quality");
}
