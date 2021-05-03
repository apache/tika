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
package org.apache.tika.eval.core.textstats;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnicodeBlockCounter implements StringStatsCalculator<Map<String, MutableInt>> {

    private static final Logger LOG = LoggerFactory.getLogger(UnicodeBlockCounter.class);

    private final int maxContentLength;

    public UnicodeBlockCounter(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    @Override
    public Map<String, MutableInt> calculate(String txt) {
        if (txt.length() < 200) {
            return Collections.EMPTY_MAP;
        }
        String s = txt;
        if (s.length() > maxContentLength) {
            s = s.substring(0, maxContentLength);
        }
        Map<String, MutableInt> m = new HashMap<>();
        Reader r = new StringReader(s);
        try {
            int c = r.read();
            while (c != -1) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                String blockString = (block == null) ? "NULL" : block.toString();
                MutableInt i = m.get(blockString);
                if (i == null) {
                    i = new MutableInt(0);
                    m.put(blockString, i);
                }
                i.increment();
                if (block == null) {
                    blockString = "NULL";
                }
                m.put(blockString, i);
                c = r.read();
            }
        } catch (IOException e) {
            LOG.warn("IOException", e);
        }
        return m;
    }
}
