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
package org.apache.tika.parser.mp4.boxes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ISO 6709 location strings as written in QuickTime/MP4 metadata: the udta
 * "&#169;xyz" box and the {@code com.apple.quicktime.location.ISO6709} value. Returns the
 * latitude, longitude and optional altitude.
 * <p>
 * Both the decimal-degree form (e.g. {@code +12.3456-098.7654+010.500/}) and the ISO 6709
 * sexagesimal compact forms ({@code ±DDMM.MMMM} / {@code ±DDMMSS.SSSS} for latitude and the
 * {@code ±DDD...} equivalents for longitude) are handled. The form is chosen by the
 * integer-digit count of each angle, which is unambiguous for valid coordinates: a decimal
 * latitude ({@code |lat| <= 90}) has at most 2 integer digits and a decimal longitude
 * ({@code |lon| <= 180}) at most 3, so neither can collide with the 4/6 (latitude) or 5/7
 * (longitude) integer-digit counts of the minute/second forms. Any other digit count falls
 * back to a lenient decimal parse.
 */
public final class ISO6709 {

    //latitude, longitude and an optional altitude, each a signed number; any trailing
    //CRS designator and the "/" terminator are ignored
    private static final Pattern PATTERN = Pattern.compile(
            "([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)?");

    private ISO6709() {
    }

    public static final class Location {
        public final double latitude;
        public final double longitude;
        //null when the string carries no altitude component
        public final Double altitude;

        Location(double latitude, double longitude, Double altitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
        }
    }

    /**
     * @param s an ISO 6709 location string, or null
     * @return the parsed location, or null if {@code s} is null or contains no location
     */
    public static Location parse(String s) {
        if (s == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(s);
        if (!matcher.find()) {
            return null;
        }
        double latitude = decodeAngle(matcher.group(1), 2);   //latitude: 2 degree digits
        double longitude = decodeAngle(matcher.group(2), 3);  //longitude: 3 degree digits
        Double altitude = matcher.group(3) == null ? null : Double.parseDouble(matcher.group(3));
        return new Location(latitude, longitude, altitude);
    }

    /**
     * Decodes one signed angle. {@code degreeDigits} is the number of integer digits the
     * degrees field occupies in the ISO 6709 compact forms: 2 for latitude, 3 for longitude.
     */
    private static double decodeAngle(String token, int degreeDigits) {
        char sign = token.charAt(0);
        int dot = token.indexOf('.');
        String intPart = dot < 0 ? token.substring(1) : token.substring(1, dot);
        String frac = dot < 0 ? "" : token.substring(dot);   //includes the '.'
        int digits = intPart.length();
        double value;
        if (digits == degreeDigits + 2) {
            //±DDMM.MMMM (or ±DDDMM.MMMM): degrees + minutes/60
            double degrees = Integer.parseInt(intPart.substring(0, degreeDigits));
            double minutes = Double.parseDouble(intPart.substring(degreeDigits) + frac);
            value = degrees + minutes / 60.0;
        } else if (digits == degreeDigits + 4) {
            //±DDMMSS.SSSS (or ±DDDMMSS.SSSS): degrees + minutes/60 + seconds/3600
            double degrees = Integer.parseInt(intPart.substring(0, degreeDigits));
            double minutes = Integer.parseInt(intPart.substring(degreeDigits, degreeDigits + 2));
            double seconds = Double.parseDouble(intPart.substring(degreeDigits + 2) + frac);
            value = degrees + minutes / 60.0 + seconds / 3600.0;
        } else {
            //decimal degrees (the common case) or a non-conformant count: lenient fallback
            value = Double.parseDouble(intPart + frac);
        }
        return sign == '-' ? -value : value;
    }
}
