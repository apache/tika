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
 * Video metadata properties that have no suitable XMPDM equivalent.
 * See TIKA-4800.
 *
 * @since Apache Tika 4.0.0
 */
public interface Video {

    /**
     * Frame rate in frames per second. {@link XMPDM#VIDEO_FRAME_RATE} is a
     * closed-choice text field (24, NTSC, PAL) and cannot carry an arbitrary
     * measured rate.
     */
    Property FRAME_RATE = Property.internalReal("video:frame-rate");

    /**
     * Average bitrate in bits per second, from the video track's BitRateBox
     * ('btrt'). A per-stream value: in a file with several video tracks it
     * reflects the last one.
     */
    Property BITRATE = Property.internalInteger("video:bitrate");

    /**
     * The video track's four-character codec identifier from the MP4/QuickTime
     * sample description (e.g. "avc1" for H.264, "hvc1"/"hev1" for HEVC), or the
     * protected sample entry format ("encv"/"drmi") for protected streams. Non-printable
     * fourccs are not exposed. A per-stream value: with several video tracks it reflects
     * the last one.
     */
    Property FORMAT = Property.internalText("video:format");
}
