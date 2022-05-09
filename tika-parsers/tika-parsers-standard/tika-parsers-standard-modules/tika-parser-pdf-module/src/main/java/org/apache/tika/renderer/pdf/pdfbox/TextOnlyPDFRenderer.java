/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.renderer.pdf.pdfbox;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.IOException;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;

/**
 * This class extends the PDFRenderer to render only the textual
 * elements
 */
public class TextOnlyPDFRenderer extends PDFRenderer {

    public TextOnlyPDFRenderer(PDDocument document) {
        super(document);
    }

    /**
     * Returns a new PageDrawer instance, using the given parameters. May be overridden.
     */
    protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
        PageDrawer pageDrawer = new TextOnlyPageDrawer(parameters);
        pageDrawer.setAnnotationFilter(getAnnotationsFilter());
        return pageDrawer;
    }

    private class TextOnlyPageDrawer extends PageDrawer {
        public TextOnlyPageDrawer(PageDrawerParameters parameters) throws IOException {
            super(parameters);
        }

        @Override
        protected void transferClip(Graphics2D graphics) {

        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {

        }

        @Override
        public void strokePath() throws IOException {

        }

        @Override
        public void fillPath(int windingRule) throws IOException {
        }

        @Override
        public void fillAndStrokePath(int windingRule) throws IOException {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {

        }

        @Override
        public void shadingFill(COSName shadingName) throws IOException {
        }
    }
}
