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

public interface Rendering {
    String RENDERING_PREFIX = "tk:rendering:";

    Property RENDERED_BY = Property.reservedExternalTextBag(RENDERING_PREFIX + "rendered-by");
    Property RENDERED_MS = Property.reservedExternalReal(RENDERING_PREFIX + "rendering-time-ms");

    /**
     * Time PDFBox took to render the page to a {@code BufferedImage}.
     * @see org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer
     */
    Property PDFBOX_RENDERING_TIME_MS =
            Property.reservedExternalReal(RENDERING_PREFIX + "pdfbox-rendering-ms");

    /**
     * Time PDFBox/java took to write the rendered image out (encoding cost varies by format).
     * @see org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer
     */
    Property PDFBOX_IMAGE_WRITING_TIME_MS =
            Property.reservedExternalReal(RENDERING_PREFIX + "pdfbox-image-writing-ms");
}
