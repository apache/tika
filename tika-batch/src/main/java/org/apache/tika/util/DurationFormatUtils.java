package org.apache.tika.util;

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

/**
 * Functionality and naming conventions (roughly) copied from org.apache.commons.lang3
 * so that we didn't have to add another dependency.
 */
public class DurationFormatUtils {

    public static String formatMillis(long duration) {
        duration = Math.abs(duration);
        StringBuilder sb = new StringBuilder();
        int secs = (int) (duration / 1000) % 60;
        int mins = (int) ((duration / (1000 * 60)) % 60);
        int hrs = (int) ((duration / (1000 * 60 * 60)) % 24);
        int days = (int) ((duration / (1000 * 60 * 60 * 24)) % 7);

        //sb.append(millis + " milliseconds");
        addUnitString(sb, days, "day");
        addUnitString(sb, hrs, "hour");
        addUnitString(sb, mins, "minute");
        addUnitString(sb, secs, "second");
        if (duration < 1000) {
            addUnitString(sb, duration, "millisecond");
        }

        return sb.toString();
    }

    private static void addUnitString(StringBuilder sb, long unit, String unitString) {
        //only add unit if >= 1
        if (unit == 1) {
            addComma(sb);
            sb.append("1 ");
            sb.append(unitString);
        } else if (unit > 1) {
            addComma(sb);
            sb.append(unit);
            sb.append(" ");
            sb.append(unitString);
            sb.append("s");
        }
    }

    private static void addComma(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
    }
}
