/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;


import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Locale;

import org.apache.poi.ss.usermodel.DataFormatter;

/**
 * A Format that allows up to 15 significant digits for integers.
 * This goes against the Microsoft spec, but it preserves information
 * for long strings of digits.
 * <p>
 * This was derived from POI's ExcelGeneralNumberFormat
 */
public class TikaExcelGeneralFormat extends Format {

    private static final long serialVersionUID = 1L;

    private static final MathContext TO_15_SF = new MathContext(15, RoundingMode.HALF_UP);

    private final DecimalFormatSymbols decimalSymbols;
    private final DecimalFormat integerFormat;
    private final DecimalFormat decimalFormat;
    private final DecimalFormat scientificFormat;

    public TikaExcelGeneralFormat(final Locale locale) {
        decimalSymbols = DecimalFormatSymbols.getInstance(locale);
        scientificFormat = new DecimalFormat("0.##############E0", decimalSymbols);
        DataFormatter.setExcelStyleRoundingMode(scientificFormat);
        integerFormat = new DecimalFormat("#", decimalSymbols);
        DataFormatter.setExcelStyleRoundingMode(integerFormat);
        decimalFormat = new DecimalFormat("#.##########", decimalSymbols);
        DataFormatter.setExcelStyleRoundingMode(decimalFormat);
    }

    public StringBuffer format(Object number, StringBuffer toAppendTo, FieldPosition pos) {
        final double value;
        if (number instanceof Number) {
            value = ((Number) number).doubleValue();
            if (Double.isInfinite(value) || Double.isNaN(value)) {
                return integerFormat.format(number, toAppendTo, pos);
            }
        } else {
            // testBug54786 gets here with a date, so retain previous behaviour
            return integerFormat.format(number, toAppendTo, pos);
        }
        final double abs = Math.abs(value);
        if (abs > 1E15 || (abs <= 1E-15 && abs > 0)) {
            return scientificFormat.format(number, toAppendTo, pos);
        } else if (Math.floor(value) == value || abs > 1E15) {
            // integer, or integer portion uses all 15 allowed digits
            return integerFormat.format(number, toAppendTo, pos);
        }
        // Non-integers of non-scientific magnitude are formatted as "up to 11
        // numeric characters, with the decimal point counting as a numeric
        // character". We know there is a decimal point, so limit to 10 digits.
        // https://support.microsoft.com/en-us/kb/65903
        final double rounded = new BigDecimal(value).round(TO_15_SF).doubleValue();
        return decimalFormat.format(rounded, toAppendTo, pos);
    }

    public Object parseObject(String source, ParsePosition pos) {
        throw new UnsupportedOperationException();
    }

}