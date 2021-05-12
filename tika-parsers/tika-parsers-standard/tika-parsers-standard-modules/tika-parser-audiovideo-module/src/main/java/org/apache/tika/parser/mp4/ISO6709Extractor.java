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
package org.apache.tika.parser.mp4;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;

class ISO6709Extractor implements Serializable {
    //based on: https://en.wikipedia.org/wiki/ISO_6709
    //strip lat long -- ignore crs for now
    private static final Pattern ISO6709_PATTERN =
            Pattern.compile("\\A([-+])(\\d{2,6})(\\.\\d+)?([-+])(\\d{3,7})(\\.\\d+)?");

    //must be thread safe
    public void extract(String s, Metadata m) {
        if (s == null) {
            return;
        }
        Matcher matcher = ISO6709_PATTERN.matcher(s);
        if (matcher.find()) {
            String lat = getLat(matcher.group(1), matcher.group(2), matcher.group(3));
            String lng = getLng(matcher.group(4), matcher.group(5), matcher.group(6));
            m.set(Metadata.LATITUDE, lat);
            m.set(Metadata.LONGITUDE, lng);
        } else {
            //ignore problems for now?
        }

    }

    private String getLng(String sign, String integer, String flot) {
        String flotNormed = (flot == null) ? "" : flot;
        if (integer.length() == 3) {
            return sign + integer + flotNormed;
        } else if (integer.length() == 5) {
            return calcDecimalDegrees(sign, integer.substring(0, 3),
                    integer.substring(3, 5) + flotNormed);
        } else if (integer.length() == 7) {
            return calcDecimalDegrees(sign, integer.substring(0, 3), integer.substring(3, 5),
                    integer.substring(5, 7) + flotNormed);
        } else {
            //ignore problems for now?
        }
        return "";
    }

    private String getLat(String sign, String integer, String flot) {
        String flotNormed = (flot == null) ? "" : flot;
        if (integer.length() == 2) {
            return sign + integer + flotNormed;
        } else if (integer.length() == 4) {
            return calcDecimalDegrees(sign, integer.substring(0, 2),
                    integer.substring(2, 4) + flotNormed);
        } else if (integer.length() == 6) {
            return calcDecimalDegrees(sign, integer.substring(0, 2), integer.substring(2, 4),
                    integer.substring(4, 6) + flotNormed);
        } else {
            //ignore problems for now?
        }
        return "";
    }

    private String calcDecimalDegrees(String sign, String degrees, String minutes) {
        double d = Integer.parseInt(degrees);
        d += (Double.parseDouble(minutes) / 60);
        return sign + String.format(Locale.ROOT, "%.8f", d);
    }

    private String calcDecimalDegrees(String sign, String degrees, String minutes, String seconds) {
        double d = Integer.parseInt(degrees);
        d += (Double.parseDouble(minutes) / 60);
        d += (Double.parseDouble(seconds) / 3600);
        return sign + String.format(Locale.ROOT, "%.8f", d);
    }
}
