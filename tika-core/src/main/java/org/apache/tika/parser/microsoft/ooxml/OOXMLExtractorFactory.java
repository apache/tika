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
package org.apache.tika.parser.microsoft.ooxml;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Figures out the correct {@link OOXMLExtractor} for the supplied document and
 * returns it.
 */
public class OOXMLExtractorFactory {

    public static OOXMLExtractor createExtractor(POIXMLTextExtractor extractor) {
        POIXMLDocument document = extractor.getDocument();

        if (document instanceof XSLFSlideShow) {
            return new XSLFPowerPointExtractorDecorator(
                    (XSLFPowerPointExtractor) extractor);
        } else if (document instanceof XSSFWorkbook) {
            return new XSSFExcelExtractorDecorator(
                    (XSSFExcelExtractor) extractor);
        } else if (document instanceof XWPFDocument) {
            return new XWPFWordExtractorDecorator((XWPFWordExtractor) extractor);
        } else {
            return new POIXMLTextExtractorDecorator(extractor);
        }
    }
}
