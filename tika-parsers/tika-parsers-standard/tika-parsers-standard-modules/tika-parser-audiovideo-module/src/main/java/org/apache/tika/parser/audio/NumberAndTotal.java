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
package org.apache.tika.parser.audio;

/**
 * The combined "n/total" form used by ID3 TRCK/TPOS frames and Vorbis
 * track/disc comments, e.g. {@code "3/12"}. See TIKA-4779.
 */
public final class NumberAndTotal {

    //null when the part is absent or not a positive integer; non-numeric
    //forms (vinyl "A1") are preserved by the raw properties instead
    public final Integer number;
    public final Integer total;

    NumberAndTotal(Integer number, Integer total) {
        this.number = number;
        this.total = total;
    }

    /**
     * @param s a track or disc value, plain ("3") or combined ("3/12"), or null
     * @return the parsed value, or null if neither part is a positive integer
     */
    public static NumberAndTotal parse(String s) {
        if (s == null) {
            return null;
        }
        int slash = s.indexOf('/');
        Integer number;
        Integer total;
        if (slash < 0) {
            number = positiveInteger(s);
            total = null;
        } else {
            number = positiveInteger(s.substring(0, slash));
            total = positiveInteger(s.substring(slash + 1));
        }
        if (number == null && total == null) {
            return null;
        }
        return new NumberAndTotal(number, total);
    }

    private static Integer positiveInteger(String s) {
        try {
            int parsed = Integer.parseInt(s.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
