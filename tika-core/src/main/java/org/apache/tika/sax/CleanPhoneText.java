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

package org.apache.tika.sax;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

/**
 * Class to help de-obfuscate phone numbers in text.
 */
public class CleanPhoneText {
    // Regex to identify a phone number
    static final String cleanPhoneRegex = "([2-9]\\d{2}[2-9]\\d{6})";

    // Regex which attempts to ignore punctuation and other distractions.
    static final String phoneRegex = "([{(<]{0,3}[2-9][\\W_]{0,3}\\d[\\W_]{0,3}\\d[\\W_]{0,6}[2-9][\\W_]{0,3}\\d[\\W_]{0,3}\\d[\\W_]{0,6}\\d[\\W_]{0,3}\\d[\\W_]{0,3}\\d[\\W_]{0,3}\\d)";

    public static ArrayList<String> extractPhoneNumbers(String text) {
        text = clean(text);
        int idx = 0;
        Pattern p = Pattern.compile(cleanPhoneRegex);
        Matcher m = p.matcher(text);
        ArrayList<String> phoneNumbers = new ArrayList<String>();
        while (m.find(idx)) {
            String digits = m.group(1);
            int start = m.start(1);
            int end = m.end(1);
            String prefix = "";
            if (start > 0) {
                prefix = text.substring(start-1, start);
            }
            if (digits.substring(0, 2).equals("82") && prefix.equals("*")) {
                // this number overlaps with a *82 sequence
                idx += 2;
            } else {
                // seems good
                phoneNumbers.add(digits);
                idx = end;
            }
        }
        return phoneNumbers;
    }

    public static String clean(String text) {
        text = text.toLowerCase(Locale.ROOT);
        for (String[][] group : cleanSubstitutions) {
            for (String[] sub : group) {
                text = text.replaceAll(sub[0], sub[1]);
            }
        }
        // Delete all non-digits and white space.
        text = text.replaceAll("[\\D+\\s]", "");
        return text;
    }


    public static final String[][][] cleanSubstitutions = new String[][][]{
            {{"&#\\d{1,3};", ""}},         // first simply remove numeric entities
            {{"th0usand", "thousand"},    // handle common misspellings
                    {"th1rteen", "thirteen"},
                    {"f0urteen", "fourteen"},
                    {"e1ghteen", "eighteen"},
                    {"n1neteen", "nineteen"},
                    {"f1fteen", "fifteen"},
                    {"s1xteen", "sixteen"},
                    {"th1rty", "thirty"},
                    {"e1ghty", "eighty"},
                    {"n1nety", "ninety"},
                    {"fourty", "forty"},
                    {"f0urty", "forty"},
                    {"e1ght", "eight"},
                    {"f0rty", "forty"},
                    {"f1fty", "fifty"},
                    {"s1xty", "sixty"},
                    {"zer0", "zero"},
                    {"f0ur", "four"},
                    {"f1ve", "five"},
                    {"n1ne", "nine"},
                    {"0ne", "one"},
                    {"tw0", "two"},
                    {"s1x", "six"}},
            // mixed compound numeral words
            // consider 7teen, etc.
            {{"twenty[\\W_]{0,3}1", "twenty-one"},
                    {"twenty[\\W_]{0,3}2", "twenty-two"},
                    {"twenty[\\W_]{0,3}3", "twenty-three"},
                    {"twenty[\\W_]{0,3}4", "twenty-four"},
                    {"twenty[\\W_]{0,3}5", "twenty-five"},
                    {"twenty[\\W_]{0,3}6", "twenty-six"},
                    {"twenty[\\W_]{0,3}7", "twenty-seven"},
                    {"twenty[\\W_]{0,3}8", "twenty-eight"},
                    {"twenty[\\W_]{0,3}9", "twenty-nine"},
                    {"thirty[\\W_]{0,3}1", "thirty-one"},
                    {"thirty[\\W_]{0,3}2", "thirty-two"},
                    {"thirty[\\W_]{0,3}3", "thirty-three"},
                    {"thirty[\\W_]{0,3}4", "thirty-four"},
                    {"thirty[\\W_]{0,3}5", "thirty-five"},
                    {"thirty[\\W_]{0,3}6", "thirty-six"},
                    {"thirty[\\W_]{0,3}7", "thirty-seven"},
                    {"thirty[\\W_]{0,3}8", "thirty-eight"},
                    {"thirty[\\W_]{0,3}9", "thirty-nine"},
                    {"forty[\\W_]{0,3}1", "forty-one"},
                    {"forty[\\W_]{0,3}2", "forty-two"},
                    {"forty[\\W_]{0,3}3", "forty-three"},
                    {"forty[\\W_]{0,3}4", "forty-four"},
                    {"forty[\\W_]{0,3}5", "forty-five"},
                    {"forty[\\W_]{0,3}6", "forty-six"},
                    {"forty[\\W_]{0,3}7", "forty-seven"},
                    {"forty[\\W_]{0,3}8", "forty-eight"},
                    {"forty[\\W_]{0,3}9", "forty-nine"},
                    {"fifty[\\W_]{0,3}1", "fifty-one"},
                    {"fifty[\\W_]{0,3}2", "fifty-two"},
                    {"fifty[\\W_]{0,3}3", "fifty-three"},
                    {"fifty[\\W_]{0,3}4", "fifty-four"},
                    {"fifty[\\W_]{0,3}5", "fifty-five"},
                    {"fifty[\\W_]{0,3}6", "fifty-six"},
                    {"fifty[\\W_]{0,3}7", "fifty-seven"},
                    {"fifty[\\W_]{0,3}8", "fifty-eight"},
                    {"fifty[\\W_]{0,3}9", "fifty-nine"},
                    {"sixty[\\W_]{0,3}1", "sixty-one"},
                    {"sixty[\\W_]{0,3}2", "sixty-two"},
                    {"sixty[\\W_]{0,3}3", "sixty-three"},
                    {"sixty[\\W_]{0,3}4", "sixty-four"},
                    {"sixty[\\W_]{0,3}5", "sixty-five"},
                    {"sixty[\\W_]{0,3}6", "sixty-six"},
                    {"sixty[\\W_]{0,3}7", "sixty-seven"},
                    {"sixty[\\W_]{0,3}8", "sixty-eight"},
                    {"sixty[\\W_]{0,3}9", "sixty-nine"},
                    {"seventy[\\W_]{0,3}1", "seventy-one"},
                    {"seventy[\\W_]{0,3}2", "seventy-two"},
                    {"seventy[\\W_]{0,3}3", "seventy-three"},
                    {"seventy[\\W_]{0,3}4", "seventy-four"},
                    {"seventy[\\W_]{0,3}5", "seventy-five"},
                    {"seventy[\\W_]{0,3}6", "seventy-six"},
                    {"seventy[\\W_]{0,3}7", "seventy-seven"},
                    {"seventy[\\W_]{0,3}8", "seventy-eight"},
                    {"seventy[\\W_]{0,3}9", "seventy-nine"},
                    {"eighty[\\W_]{0,3}1", "eighty-one"},
                    {"eighty[\\W_]{0,3}2", "eighty-two"},
                    {"eighty[\\W_]{0,3}3", "eighty-three"},
                    {"eighty[\\W_]{0,3}4", "eighty-four"},
                    {"eighty[\\W_]{0,3}5", "eighty-five"},
                    {"eighty[\\W_]{0,3}6", "eighty-six"},
                    {"eighty[\\W_]{0,3}7", "eighty-seven"},
                    {"eighty[\\W_]{0,3}8", "eighty-eight"},
                    {"eighty[\\W_]{0,3}9", "eighty-nine"},
                    {"ninety[\\W_]{0,3}1", "ninety-one"},
                    {"ninety[\\W_]{0,3}2", "ninety-two"},
                    {"ninety[\\W_]{0,3}3", "ninety-three"},
                    {"ninety[\\W_]{0,3}4", "ninety-four"},
                    {"ninety[\\W_]{0,3}5", "ninety-five"},
                    {"ninety[\\W_]{0,3}6", "ninety-six"},
                    {"ninety[\\W_]{0,3}7", "ninety-seven"},
                    {"ninety[\\W_]{0,3}8", "ninety-eight"},
                    {"ninety[\\W_]{0,3}9", "ninety-nine"}},
            // now resolve compound numeral words
            {{"twenty-one", "21"},
                    {"twenty-two", "22"},
                    {"twenty-three", "23"},
                    {"twenty-four", "24"},
                    {"twenty-five", "25"},
                    {"twenty-six", "26"},
                    {"twenty-seven", "27"},
                    {"twenty-eight", "28"},
                    {"twenty-nine", "29"},
                    {"thirty-one", "31"},
                    {"thirty-two", "32"},
                    {"thirty-three", "33"},
                    {"thirty-four", "34"},
                    {"thirty-five", "35"},
                    {"thirty-six", "36"},
                    {"thirty-seven", "37"},
                    {"thirty-eight", "38"},
                    {"thirty-nine", "39"},
                    {"forty-one", "41"},
                    {"forty-two", "42"},
                    {"forty-three", "43"},
                    {"forty-four", "44"},
                    {"forty-five", "45"},
                    {"forty-six", "46"},
                    {"forty-seven", "47"},
                    {"forty-eight", "48"},
                    {"forty-nine", "49"},
                    {"fifty-one", "51"},
                    {"fifty-two", "52"},
                    {"fifty-three", "53"},
                    {"fifty-four", "54"},
                    {"fifty-five", "55"},
                    {"fifty-six", "56"},
                    {"fifty-seven", "57"},
                    {"fifty-eight", "58"},
                    {"fifty-nine", "59"},
                    {"sixty-one", "61"},
                    {"sixty-two", "62"},
                    {"sixty-three", "63"},
                    {"sixty-four", "64"},
                    {"sixty-five", "65"},
                    {"sixty-six", "66"},
                    {"sixty-seven", "67"},
                    {"sixty-eight", "68"},
                    {"sixty-nine", "69"},
                    {"seventy-one", "71"},
                    {"seventy-two", "72"},
                    {"seventy-three", "73"},
                    {"seventy-four", "74"},
                    {"seventy-five", "75"},
                    {"seventy-six", "76"},
                    {"seventy-seven", "77"},
                    {"seventy-eight", "78"},
                    {"seventy-nine", "79"},
                    {"eighty-one", "81"},
                    {"eighty-two", "82"},
                    {"eighty-three", "83"},
                    {"eighty-four", "84"},
                    {"eighty-five", "85"},
                    {"eighty-six", "86"},
                    {"eighty-seven", "87"},
                    {"eighty-eight", "88"},
                    {"eighty-nine", "89"},
                    {"ninety-one", "91"},
                    {"ninety-two", "92"},
                    {"ninety-three", "93"},
                    {"ninety-four", "94"},
                    {"ninety-five", "95"},
                    {"ninety-six", "96"},
                    {"ninety-seven", "97"},
                    {"ninety-eight", "98"},
                    {"ninety-nine", "99"}},
            // larger units function as suffixes now
            // assume never have three hundred four, three hundred and four
            {{"hundred", "00"},
                    {"thousand", "000"}},
            // single numeral words now
            // some would have been ambiguous
            {{"seventeen", "17"},
                    {"thirteen", "13"},
                    {"fourteen", "14"},
                    {"eighteen", "18"},
                    {"nineteen", "19"},
                    {"fifteen", "15"},
                    {"sixteen", "16"},
                    {"seventy", "70"},
                    {"eleven", "11"},
                    {"twelve", "12"},
                    {"twenty", "20"},
                    {"thirty", "30"},
                    {"eighty", "80"},
                    {"ninety", "90"},
                    {"three", "3"},
                    {"seven", "7"},
                    {"eight", "8"},
                    {"forty", "40"},
                    {"fifty", "50"},
                    {"sixty", "60"},
                    {"zero", "0"},
                    {"four", "4"},
                    {"five", "5"},
                    {"nine", "9"},
                    {"one", "1"},
                    {"two", "2"},
                    {"six", "6"},
                    {"ten", "10"}},
            // now do letter for digit substitutions
            {{"oh", "0"},
                    {"o", "0"},
                    {"i", "1"},
                    {"l", "1"}}
    };
}