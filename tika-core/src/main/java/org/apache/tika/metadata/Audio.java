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
}
