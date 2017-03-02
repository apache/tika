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

package org.apache.tika.parser.mail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

public class MailUtil {

    private final static Pattern EMAIL = Pattern.compile("(?i)([^<@\\s]+@[^@> ]+)");

    /**
     * This tries to split a "from" or "to" value into a person field and an email field.
     * This does not handle RFC encoded strings (e.g. "=?iso-8859-1?Q?H=E9roux_Louise?"),
     * you must decode them first.
     *
     * @param string
     * @param personProperty
     * @param emailProperty
     * @param metadata
     */
    public static void setPersonAndEmail(String string,
                                         Property personProperty,
                                         Property emailProperty, Metadata metadata) {

        StringBuffer sb = new StringBuffer();
        String email = extractEmail(string, sb);
        String person = clean(sb.toString());
        if (person != null && person.length() > 0) {
            metadata.set(personProperty, person);
        }
        if (email != null && email.length() > 0) {
            metadata.set(emailProperty, email);
        }

    }

    /**
     * This tries to split a "from" or "to" value into a person field and an email field.
     * This does not handle RFC encoded strings (e.g. "=?iso-8859-1?Q?H=E9roux_Louise?"),
     * you must decode them first.
     *
     * @param string
     * @param personProperty
     * @param emailProperty
     * @param metadata
     */
    public static void addPersonAndEmail(String string,
                                      Property personProperty,
                                      Property emailProperty, Metadata metadata) {

        StringBuffer sb = new StringBuffer();
        String email = extractEmail(string, sb);
        String person = clean(sb.toString());

        if (person != null && person.length() > 0) {
            metadata.add(personProperty, person);
        }
        if (email != null && email.length() > 0) {
            metadata.add(emailProperty, email);
        }

    }

    private static String clean(String s) {
        s = s.replaceAll("[<>\"]", " ");
        s = s.trim();
        return s;
    }

    private static String extractEmail(String string, StringBuffer sb) {
        Matcher emailMatcher = EMAIL.matcher(string);
        String email = "";
        //TODO: warn if more than one email is found?
        while (emailMatcher.find()) {
            emailMatcher.appendReplacement(sb, "");
            email = emailMatcher.group(1);
        }
        emailMatcher.appendTail(sb);
        return email;
    }

    /**
     * If the chunk looks like it contains an email
     * @param chunk
     * @return
     */
    public static boolean containsEmail(String chunk) {
        if (chunk == null) {
            return false;
        }

        if (EMAIL.matcher(chunk).find()) {
            return true;
        }
        return false;
    }
}
