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
package org.apache.tika.inference.locator;

/**
 * Locator for a spatial region in an image or diagram.
 * <p>
 * The bounding box coordinates are normalized to [0, 1] relative to
 * image dimensions. An optional label identifies the region
 * (e.g. from object detection or VLM output).
 */
public class SpatialLocator {

    private final float[] bbox;
    private final String label;

    /**
     * @param bbox  normalized bounding box [x0, y0, x1, y1]
     * @param label optional label for the region, or null
     */
    public SpatialLocator(float[] bbox, String label) {
        this.bbox = bbox;
        this.label = label;
    }

    public SpatialLocator(float[] bbox) {
        this(bbox, null);
    }

    /**
     * @return normalized bbox [x0, y0, x1, y1]
     */
    public float[] getBbox() {
        return bbox;
    }

    public String getLabel() {
        return label;
    }
}
