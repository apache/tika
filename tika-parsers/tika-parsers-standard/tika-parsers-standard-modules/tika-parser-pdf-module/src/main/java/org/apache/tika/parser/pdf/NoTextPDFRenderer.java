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
package org.apache.tika.parser.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/**
 * This class extends the PDFRenderer to exclude rendering of electronic text.
 */
public class NoTextPDFRenderer extends PDFRenderer {

    public NoTextPDFRenderer(PDDocument document) {
        super(document);
    }

    /**
     * Returns a new PageDrawer instance, using the given parameters. May be overridden.
     */
    protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
        PageDrawer pageDrawer = new NoTextPageDrawer(parameters);
        pageDrawer.setAnnotationFilter(getAnnotationsFilter());
        return pageDrawer;
    }

    private static class NoTextPageDrawer extends PageDrawer {
        public NoTextPageDrawer(PageDrawerParameters parameters) throws IOException {
            super(parameters);
        }

        @Override
        public void beginText() throws IOException {
        }

        @Override
        public void endText() throws IOException {
        }


        @Override
        protected void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                                     Vector displacement) throws IOException {
        }

        @Override
        protected void showType3Glyph(Matrix textRenderingMatrix, PDType3Font font, int code,
                                      Vector displacement) throws IOException {
        }
    }
}
