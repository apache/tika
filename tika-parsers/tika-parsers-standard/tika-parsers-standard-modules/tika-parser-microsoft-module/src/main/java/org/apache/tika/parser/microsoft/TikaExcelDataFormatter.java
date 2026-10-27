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

import java.util.Locale;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.util.LocaleUtil;

/**
 * Overrides Excel's General format to include more
 * significant digits than the MS Spec allows.
 * See TIKA-2025.
 */
public class TikaExcelDataFormatter extends DataFormatter {

    private String dateOverrideFormatString;

    public TikaExcelDataFormatter() {
        this(LocaleUtil.getUserLocale());
    }

    public TikaExcelDataFormatter(Locale locale) {
        super(locale);
        addFormat("General", new TikaExcelGeneralFormat(locale));
        addFormat("general", new TikaExcelGeneralFormat(locale));
    }

    @Override
    public String formatRawCellContents(double value, int formatIndex, String formatString,
                                        boolean use1904Windowing) {
        String activeFormatString = formatString;
        if (dateOverrideFormatString != null && DateUtil.isADateFormat(formatIndex, formatString)) {
            activeFormatString = dateOverrideFormatString;
        }
        try {
            return super.formatRawCellContents(value, formatIndex, activeFormatString,
                    use1904Windowing);
        } catch (RuntimeException e) {
            // TODO: remove on POI 6.0.1, whose DataFormatter falls back per value itself; 6.0.0
            // throws from SimpleFraction on overflow for a fraction format with text, e.g. #\ ???/???
            return super.formatRawCellContents(value, 0, "General", use1904Windowing);
        }
    }

    public void setDateFormatOverride(String dateOverrideFormat) {
        if (dateOverrideFormat != null && !dateOverrideFormat.isBlank()) {
            this.dateOverrideFormatString = dateOverrideFormat;
        }
    }
}
