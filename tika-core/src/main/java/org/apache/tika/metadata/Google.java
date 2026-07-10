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
 * Properties from the Google Photos XMP namespaces used by Motion Photos and the
 * legacy MicroVideo format. See
 * <a href="https://developer.android.com/media/platform/motion-photo-format">the
 * Motion Photo format</a>.
 * <p>
 * The camera namespace is declared with either the {@code Camera} (current) or
 * {@code GCamera} (legacy) prefix depending on the file, but the namespace URI is
 * the same. The image XMP handler maps these by URI so the keys below are stable
 * regardless of the prefix the file happens to use.
 */
public interface Google {

    String CAMERA_NS = "http://ns.google.com/photos/1.0/camera/";

    String CONTAINER_NS = "http://ns.google.com/photos/1.0/container/";

    String ITEM_NS = "http://ns.google.com/photos/1.0/container/item/";

    Property MOTION_PHOTO = Property.externalText("Camera:MotionPhoto");

    Property MOTION_PHOTO_VERSION = Property.externalText("Camera:MotionPhotoVersion");

    Property MOTION_PHOTO_PRESENTATION_TIMESTAMP_US =
            Property.externalText("Camera:MotionPhotoPresentationTimestampUs");

    Property MICRO_VIDEO = Property.externalText("Camera:MicroVideo");

    Property MICRO_VIDEO_VERSION = Property.externalText("Camera:MicroVideoVersion");

    Property MICRO_VIDEO_OFFSET = Property.externalText("Camera:MicroVideoOffset");

    Property MICRO_VIDEO_PRESENTATION_TIMESTAMP_US =
            Property.externalText("Camera:MicroVideoPresentationTimestampUs");
}
