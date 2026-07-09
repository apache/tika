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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.xssf.binary.XSSFBParser;
import org.apache.poi.xssf.binary.XSSFBRecordType;
import org.apache.poi.xssf.binary.XSSFBUtils;
import org.apache.poi.xssf.model.SharedStrings;

/**
 * Replacement for POI's {@code XSSFBSharedStringsTable} that does not depend on
 * {@code XSSFRichTextString} (which pulls in poi-ooxml-lite / xmlbeans via {@code CTRst}).
 * <p>
 * The binary parsing logic is identical to POI's implementation; only
 * {@link #getItemAt(int)} is changed to return a lightweight {@link RichTextString}
 * wrapper instead of {@code XSSFRichTextString}.
 */
class TikaXSSFBSharedStringsTable implements SharedStrings {

    private static final String SHARED_STRINGS_BINARY_CT =
            "application/vnd.ms-excel.sharedStrings";

    private int count;
    private int uniqueCount;
    private final List<String> strings = new ArrayList<>();

    TikaXSSFBSharedStringsTable(OPCPackage pkg) throws IOException {
        ArrayList<PackagePart> parts =
                pkg.getPartsByContentType(SHARED_STRINGS_BINARY_CT);
        if (!parts.isEmpty()) {
            PackagePart sstPart = parts.get(0);
            try (InputStream stream = sstPart.getInputStream()) {
                readFrom(stream);
            }
        }
    }

    private void readFrom(InputStream inputStream) throws IOException {
        new SSTBinaryReader(inputStream).parse();
    }

    @Override
    public RichTextString getItemAt(int idx) {
        return new PlainRichTextString(strings.get(idx));
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public int getUniqueCount() {
        return uniqueCount;
    }

    private class SSTBinaryReader extends XSSFBParser {

        SSTBinaryReader(InputStream is) {
            super(is);
        }

        @Override
        public void handleRecord(int recordType, byte[] data) {
            XSSFBRecordType type = XSSFBRecordType.lookup(recordType);
            switch (type) {
                case BrtSstItem:
                    // Inline XSSFBRichStr.build() logic — that class is package-private in POI
                    StringBuilder sb = new StringBuilder();
                    XSSFBUtils.readXLWideString(data, 1, sb);
                    strings.add(sb.toString());
                    break;
                case BrtBeginSst:
                    count = (int) LittleEndian.getUInt(data, 0);
                    uniqueCount = (int) LittleEndian.getUInt(data, 4);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Minimal {@link RichTextString} that just wraps a plain string,
     * avoiding the xmlbeans dependency in {@code XSSFRichTextString}.
     */
    private static class PlainRichTextString implements RichTextString {

        private final String text;

        PlainRichTextString(String text) {
            this.text = text;
        }

        @Override
        public String getString() {
            return text;
        }

        @Override
        public int length() {
            return text == null ? 0 : text.length();
        }

        @Override
        public int numFormattingRuns() {
            return 0;
        }

        @Override
        public int getIndexOfFormattingRun(int index) {
            return 0;
        }

        @Override
        public void applyFont(int startIndex, int endIndex, short fontIndex) {
        }

        @Override
        public void applyFont(int startIndex, int endIndex, Font font) {
        }

        @Override
        public void applyFont(Font font) {
        }

        @Override
        public void clearFormatting() {
        }

        @Override
        public void applyFont(short fontIndex) {
        }
    }
}
