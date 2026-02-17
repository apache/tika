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
 * Locator for paginated documents (PDF, PPTX, DOCX, etc.).
 * <p>
 * The bounding box coordinates are normalized to [0, 1] relative to
 * page dimensions, making them resolution-independent.
 * The bbox array is {@code [x0, y0, x1, y1]} where (x0, y0) is the
 * top-left corner and (x1, y1) is the bottom-right corner.
 */
public class PaginatedLocator {

    private final int page;
    private final float[] bbox;

    /**
     * @param page 1-based page number
     * @param bbox normalized bounding box [x0, y0, x1, y1], or null
     *             if the entire page is referenced
     */
    public PaginatedLocator(int page, float[] bbox) {
        this.page = page;
        this.bbox = bbox;
    }

    public PaginatedLocator(int page) {
        this(page, null);
    }

    public int getPage() {
        return page;
    }

    /**
     * @return normalized bbox [x0, y0, x1, y1], or null if whole page
     */
    public float[] getBbox() {
        return bbox;
    }
}
