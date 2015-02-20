package org.apache.tika.parser.evil;

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


import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Parser to be used only for testing wrappers and drivers of Parsers.
 * <p>
 * This class enables tests for handling parsers that run into problems.
 */

public class EvilParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    //NOTE: these are just regexes, attributes must be in proper order!

    //<throwable message="some message">java.lang.SomeException</throwable>
    //<throwable>java.lang.SomeException</throwable>
    private static final Pattern THROWABLE =
            Pattern.compile("<throwable(?:\\s*message=\"([^\"]+)\")?\\s*>([^<>]+)</throwable>");

    //<hang type="heavy" max_millis="1000" pulse_check_millis="100000000"/>
    //<hang type="sleep" max_millis="1000"/>
    private static final Pattern HANG =
            Pattern.compile("<hang type=\"(heavy|sleep)\"\\s+max_millis=\"(\\d+)\"(?:\\s+pulse_check_millis=\"(\\d+)\")?\\s*/>");

    //<real_oom/>
    private final static Pattern REAL_OOM = Pattern.compile("<real_oom/>");


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        Set<MediaType> types = new HashSet<MediaType>();
        MediaType type = MediaType.application("evil");
        types.add(type);
        return types;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        String content = IOUtils.toString(stream, IOUtils.UTF_8.toString());

        Matcher hangMatcher = HANG.matcher(content);
        if (hangMatcher.find()) {
            handleHang(hangMatcher);
            handle(content, handler);
            return;
        }
        Matcher throwableMatcher = THROWABLE.matcher(content);
        if (throwableMatcher.find()) {
            String msg = throwableMatcher.group(1);
            String throwableClass = throwableMatcher.group(2);
            throwIt(throwableClass, msg);
            //exception should have been thrown by now
            assert(false);
        }

        Matcher realOOM = REAL_OOM.matcher(content);
        if (realOOM.find()) {
            kabOOM();
        }

        //if there has been no trigger, treat as
        //regular utf-8 text file
        handle(content, handler);

    }

    private void handleHang(Matcher hangMatcher) {
        String hangType = hangMatcher.group(1);

        if (hangMatcher.group(2) == null) {
            throw new RuntimeException("must specify max_millis attribute in <hang>");
        }

        long maxMillis = parseLong(hangMatcher.group(2));
        if ("heavy".equals(hangType)) {
            if (hangMatcher.group(3) == null) {
                throw new RuntimeException("must specify pulse_check_millis attribute in <hang> when type is heavy");
            }
            long heavyHangPulseMillis = parseLong(hangMatcher.group(3));
            hangHeavy(maxMillis, heavyHangPulseMillis);
        } else if ("sleep".equals(hangType)) {
            sleep(maxMillis);
        } else {
            throw new RuntimeException("need to specify heavy|sleep as value to type attribute for <hang>");
        }

    }

    private long parseLong(String s) {
        long millis = -1;
        try {
            millis = Long.parseLong(s);
        } catch (NumberFormatException e) {
            //shouldn't happen unless something goes wrong w regex
            throw new RuntimeException("Problem in regex parsing sleep duration");
        }
        return millis;
    }

    private void throwIt(String className, String msg) throws IOException,
            SAXException, TikaException {
        Throwable t = null;
        if (msg == null) {
            try {
                t = (Throwable) Class.forName(className).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("couldn't create throwable class:"+className, e);
            }
        } else {
            try {
                Class clazz = Class.forName(className);
                Constructor con = clazz.getConstructor(String.class);
                t = (Throwable) con.newInstance(msg);
            } catch (Exception e) {
                throw new RuntimeException("couldn't create throwable class:" + className, e);
            }
        }
        if (t instanceof SAXException) {
            throw (SAXException)t;
        } else if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof TikaException) {
            throw (TikaException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            //wrap the throwable in a RuntimeException
            throw new RuntimeException(t);
        }
    }

    private void handle(String content, ContentHandler handler) throws SAXException {

        handler.startDocument();
        Attributes attrs = new AttributesImpl();
        handler.startElement("", "body", "body", attrs);
        handler.startElement("", "p", "p", attrs);
        char[] charArr = content.toCharArray();
        handler.characters(charArr, 0, charArr.length);
        handler.endElement("", "p", "p");
        handler.endElement("", "body", "body");
        handler.endDocument();

    }

    private void kabOOM() {
        List<int[]> ints = new ArrayList<int[]>();

        while (true) {
            int[] intArr = new int[32000];
            ints.add(intArr);
        }
    }

    private void hangHeavy(long maxMillis, long pulseCheckMillis) {
        //do some heavy computation and occasionally check for
        //whether time has exceeded maxMillis. see TIKA-1132 for inspiration
        long start = new Date().getTime();
        int lastChecked = 0;
        while (true) {
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                for (int j = 1; j < Integer.MAX_VALUE; j++) {
                    double div = (double) i / (double) j;
                    lastChecked++;
                    if (lastChecked > pulseCheckMillis) {
                        lastChecked = 0;
                        long elapsed = new Date().getTime()-start;
                        if (elapsed > maxMillis) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void sleep(long maxMillis) {
        try {
            Thread.sleep(maxMillis);
        } catch (InterruptedException e) {

        }
    }
}