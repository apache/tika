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
package org.apache.tika.parser.microsoft;

import java.io.Serializable;

import org.apache.tika.renderer.microsoft.POIMetafileRenderer;

/**
 * Configuration of the {@link EMFParser} ("emf-parser") and the
 * {@link WMFParser} ("wmf-parser").
 */
public class MetafileParserConfig implements Serializable {

    private static final long serialVersionUID = -6371049153052164071L;

    private boolean renderImage = false;
    private int renderWidth = 800;

    /**
     * Whether to render the image and emit the rendering as a RENDERING
     * embedded document. Off by default.
     */
    public boolean isRenderImage() {
        return renderImage;
    }

    public void setRenderImage(boolean renderImage) {
        this.renderImage = renderImage;
    }

    /**
     * Width of the rendering in pixels when the default
     * {@link POIMetafileRenderer} is used; the height follows the image's
     * aspect ratio. Default 800.
     */
    public int getRenderWidth() {
        return renderWidth;
    }

    public void setRenderWidth(int renderWidth) {
        if (renderWidth < 1 || renderWidth > 10000) {
            throw new IllegalArgumentException(
                    "renderWidth must be between 1 and 10000, got: " + renderWidth);
        }
        this.renderWidth = renderWidth;
    }
}
