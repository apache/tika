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
 * Metadata properties specific to the QuickTime container family (.mov,
 * .mp4, .m4a and friends). The container's com.apple.quicktime.* item-list
 * keys pass through under their own names; the properties defined here are
 * values Tika derives from structures beyond that key list.
 */
public interface QuickTime {

    /**
     * Presentation start of the com.apple.quicktime.still-image-time timed
     * metadata track, in microseconds. In an Apple Live Photo video this is
     * the moment the paired still image was captured. A value of 0 means the
     * still is the very first frame; the property is absent when the file
     * declares no such track. Only set when the track holds exactly one
     * sample (the Live Photo shape): the sample marks a point in time, its
     * own one-tick duration is filler, so there is deliberately no
     * corresponding end property.
     */
    Property STILL_IMAGE_TIME = Property.internalReal("quicktime:still-image-time");
}
