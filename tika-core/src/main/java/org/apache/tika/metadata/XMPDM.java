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
 * XMP Dynamic Media schema. This is a collection of
 * {@link Property property definition} constants for the dynamic media
 * properties defined in the XMP standard.
 *
 * @since Apache Tika 0.7
 * @see <a href="http://www.adobe.com/devnet/xmp/pdfs/XMPSpecificationPart2.pdf"
 *        >XMP Specification, Part 2: Standard Schemas</a>
 */
public interface XMPDM {

    /**
     * "The absolute path to the file's peak audio file. If empty, no peak
     * file exists."
     */
    Property ABS_PEAK_AUDIO_FILE_PATH =
        Property.internalURI("xmpDM:absPeakAudioFilePath");

    /**
     * "The name of the album."
     */
    Property ALBUM = Property.externalText("xmpDM:album");

    /**
     * "An alternative tape name, set via the project window or timecode
     * dialog in Premiere. If an alternative name has been set and has not
     * been reverted, that name is displayed."
     */
    Property ALT_TAPE_NAME = Property.externalText("xmpDM:altTapeName");

//    /**
//     * "A timecode set by the user. When specified, it is used instead
//     * of the startTimecode."
//     */
//    Property ALT_TIMECODE = "xmpDM:altTimecode";

    /**
     * "The name of the artist or artists."
     */
    Property ARTIST = Property.externalText("xmpDM:artist");

    /**
     * "The date and time when the audio was last modified."
     */
    Property AUDIO_MOD_DATE = Property.internalDate("xmpDM:audioModDate");

    /**
     * "The audio sample rate. Can be any value, but commonly 32000, 41100,
     * or 48000."
     */
    Property AUDIO_SAMPLE_RATE =
        Property.internalInteger("xmpDM:audioSampleRate");

    /**
     * "The audio sample type."
     */
    Property AUDIO_SAMPLE_TYPE = Property.internalClosedChoise(
            "xmpDM:audioSampleType", "8Int", "16Int", "32Int", "32Float");

    /**
     * "The audio channel type."
     */
    Property AUDIO_CHANNEL_TYPE = Property.internalClosedChoise(
            "xmpDM:audioChannelType", "Mono", "Stereo", "5.1", "7.1");

    /**
     * "The audio compression used. For example, MP3."
     */
    Property AUDIO_COMPRESSOR = Property.internalText("xmpDM:audioCompressor");

//    /**
//     * "Additional parameters for Beat Splice stretch mode."
//     */
//    Property BEAT_SPLICE_PARAMS = "xmpDM:beatSpliceParams";

    /**
     * "The composer's name."
     */
    Property COMPOSER = Property.externalText("xmpDM:composer");

//    /**
//     * "An unordered list of all media used to create this media."
//     */
//    Property CONTRIBUTED_MEDIA = "xmpDM:contributedMedia";

    /**
     * "The copyright information."
     */
    Property COPYRIGHT = Property.externalText("xmpDM:copyright");

//    /**
//     * "The duration of the media file."
//     */
//    Property DURATION = "xmpDM:duration";

    /**
     * "The engineer's name."
     */
    Property ENGINEER = Property.externalText("xmpDM:engineer");

    /**
     * "The file data rate in megabytes per second. For example:
     * '36/10' = 3.6 MB/sec"
     */
    Property FILE_DATA_RATE = Property.internalRational("xmpDM:fileDataRate");

    /**
     * "The name of the genre."
     */
    Property GENRE = Property.externalText("xmpDM:genre");

    /**
     * "The musical instrument."
     */
    Property INSTRUMENT = Property.externalText("xmpDM:instrument");

//    /**
//     * "The duration of lead time for queuing music."
//     */
//    Property INTRO_TIME = "xmpDM:introTime";

    /**
     * "The audio's musical key."
     */
    Property KEY = Property.internalClosedChoise(
            "xmpDM:key", "C", "C#", "D", "D#", "E", "F", "F#",
            "G", "G#", "A", "A#", "B");

    /**
     * "User's log comments."
     */
    Property LOG_COMMENT = Property.externalText("xmpDM:logComment");

    /**
     * "When true, the clip can be looped seamlessly."
     */
    Property LOOP = Property.internalBoolean("xmpDM:loop");

    /**
     * "The number of beats."
     */
    Property NUMBER_OF_BEATS = Property.internalReal("xmpDM:numberOfBeats");

//    /**
//     * An ordered list of markers. See also {@link #TRACKS xmpDM:Tracks}.
//     */
//    Property MARKERS = "xmpDM:markers";

    /**
     * "The date and time when the metadata was last modified."
     */
    Property METADATA_MOD_DATE = Property.internalDate("xmpDM:metadataModDate");

//    /**
//     * "The time at which to fade out."
//     */
//    Property OUT_CUE = "xmpDM:outCue";

//    /**
//     * "A reference to the project that created this file."
//     */
//    Property PROJECT_REF = "xmpDM:projectRef"; 

    /**
     * "The sampling phase of film to be converted to video (pull-down)."
     */
    Property PULL_DOWN = Property.internalClosedChoise(
            "xmpDM:pullDown", "WSSWW", "SSWWW", "SWWWS", "WWWSS", "WWSSW",
            "WSSWW_24p", "SSWWW_24p", "SWWWS_24p", "WWWSS_24p", "WWSSW_24p");

    /**
     * "The relative path to the file's peak audio file. If empty, no peak
     * file exists."
     */
    Property RELATIVE_PEAK_AUDIO_FILE_PATH =
        Property.internalURI("xmpDM:relativePeakAudioFilePath");

//    /**
//     * "The start time of the media inside the audio project."
//     */
//    Property RELATIVE_TIMESTAMP = "xmpDM:relativeTimestamp";

    /**
     * "The date the title was released."
     */
    Property RELEASE_DATE = Property.externalDate("xmpDM:releaseDate");

//    /**
//     * "Additional parameters for Resample stretch mode."
//     */
//    Property RESAMPLE_PARAMS = "xmpDM:resampleParams";

    /**
     * "The musical scale used in the music. 'Neither' is most often used
     * for instruments with no associated scale, such as drums."
     */
    Property SCALE_TYPE = Property.internalClosedChoise(
            "xmpDM:scaleType", "Major", "Minor", "Both", "Neither");

    /**
     * "The name of the scene."
     */
    Property SCENE = Property.externalText("xmpDM:scene");

    /**
     * "The date and time when the video was shot."
     */
    Property SHOT_DATE = Property.externalDate("xmpDM:shotDate");

    /**
     * "The name of the location where the video was shot. For example:
     * 'Oktoberfest, Munich, Germany'. For more accurate  positioning,
     * use the EXIF GPS values."
     */
    Property SHOT_LOCATION = Property.externalText("xmpDM:shotLocation");

    /**
     * "The name of the shot or take."
     */
    Property SHOT_NAME = Property.externalText("xmpDM:shotName");

    /**
     * "A description of the speaker angles from center front in degrees.
     * For example: 'Left = -30, Right = 30, Center = 0, LFE = 45,
     * Left Surround = -110, Right Surround = 110'"
     */
    Property SPEAKER_PLACEMENT =
        Property.externalText("xmpDM:speakerPlacement");

//    /**
//     * "The timecode of the first frame of video in the file, as obtained
//     * from the device control."
//     */
//    Property START_TIMECODE = "xmpDM:startTimecode";

    /**
     * "The audio stretch mode."
     */
    Property STRETCH_MODE = Property.internalClosedChoise(
            "xmpDM:stretchMode", "Fixed length", "Time-Scale", "Resample",
            "Beat Splice", "Hybrid");

    /**
     * "The name of the tape from which the clip was captured, as set during
     * the capture process."
     */
    Property TAPE_NAME = Property.externalText("xmpDM:tapeName");

    /**
     * "The audio's tempo."
     */
    Property TEMPO = Property.internalReal("xmpDM:tempo");

//    /**
//     * "Additional parameters for Time-Scale stretch mode."
//     */
//    Property TIME_SCALE_PARAMS = "xmpDM:timeScaleParams";

    /**
     * "The time signature of the music."
     */
    Property TIME_SIGNATURE = Property.internalClosedChoise(
            "xmpDM:timeSignature", "2/4", "3/4", "4/4", "5/4", "7/4",
            "6/8", "9/8", "12/8", "other");

    /**
     * "A numeric value indicating the order of the audio file within its
     * original recording."
     */
    Property TRACK_NUMBER = Property.externalInteger("xmpDM:trackNumber");

//    /**
//     * "An unordered list of tracks. A track is a named set of markers,
//     * which can specify a frame rate for all markers in the set.
//     * See also {@link #MARKERS xmpDM:markers}."
//     */
//    Property TRACKS = "xmpDM:Tracks";

    /**
     * "The alpha mode."
     */
    Property VIDEO_ALPHA_MODE = Property.externalClosedChoise(
            "xmpDM:videoAlphaMode", "straight", "pre-multiplied");

//    /**
//     * "A color in CMYK or RGB to be used as the pre-multiple color when
//     * alpha mode is pre-multiplied."
//     */
//    Property VIDEO_ALPHA_PREMULTIPLE_COLOR = "xmpDM:videoAlphaPremultipleColor";

    /**
     * "When true, unity is clear, when false, it is opaque."
     */
    Property VIDEO_ALPHA_UNITY_IS_TRANSPARENT =
        Property.internalBoolean("xmpDM:videoAlphaUnityIsTransparent");

    /**
     * "The color space."
     */
    Property VIDEO_COLOR_SPACE = Property.internalClosedChoise(
            "xmpDM:videoColorSpace", "sRGB", "CCIR-601", "CCIR-709");

    /**
     * "Video compression used. For example, jpeg."
     */
    Property VIDEO_COMPRESSOR = Property.internalText("xmpDM:videoCompressor");

    /**
     * "The field order for video."
     */
    Property VIDEO_FIELD_ORDER = Property.internalClosedChoise(
            "xmpDM:videoFieldOrder", "Upper", "Lower", "Progressive");

    /**
     * "The video frame rate."
     */
    Property VIDEO_FRAME_RATE = Property.internalOpenChoise(
            "xmpDM:videoFrameRate", "24", "NTSC", "PAL");

//    /**
//     * "The frame size. For example: w:720, h: 480, unit:pixels"
//     */
//    Property VIDEO_FRAME_SIZE = "xmpDM:videoFrameSize";

    /**
     * "The date and time when the video was last modified."
     */
    Property VIDEO_MOD_DATE = Property.internalDate("xmpDM:videoModDate");

    /**
     * "The size in bits of each color component of a pixel. Standard
     *  Windows 32-bit pixels have 8 bits per component."
     */
    Property VIDEO_PIXEL_DEPTH = Property.internalClosedChoise(
            "xmpDM:videoPixelDepth", "8Int", "16Int", "32Int", "32Float");

    /**
     * "The aspect ratio, expressed as wd/ht. For example: '648/720' = 0.9"
     */
    Property VIDEO_PIXEL_ASPECT_RATIO =
        Property.internalRational("xmpDM:videoPixelAspectRatio");

}
