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
package org.apache.tika.parser.video;

/** How H.264 and HEVC video is read for CEA-608 captions carried in its SEI messages. */
public enum VideoCaptionScan {
    /** Not read. */
    NONE,
    /**
     * A head of frames, then a few frames at intervals; every frame once a caption character
     * turns up. A caption run between two probes is missed.
     */
    PROBE,
    /** Every frame. */
    FULL
}
