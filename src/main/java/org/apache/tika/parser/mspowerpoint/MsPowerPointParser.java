/**
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
package org.apache.tika.parser.mspowerpoint;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Content;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.RegexUtils;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * Power point parser
 * 
 * 
 */
public class MsPowerPointParser extends Parser {

    private PPTExtractor extrator = new PPTExtractor();

    static Logger logger = Logger.getRootLogger();

    public Map<String, Content> getContents() {
        if (contentStr == null) {
            extrator.setContents(super.getContents());
            try {
                contentStr = extrator.extractText(getInputStream());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        Map<String, Content> ctt = super.getContents();
        Iterator i = ctt.values().iterator();
        while (i.hasNext()) {
            Content ct = (Content) i.next();
            if (ct.getTextSelect() != null) {
                if (ct.getTextSelect().equalsIgnoreCase("fulltext")) {
                    ct.setValue(contentStr);
                }

            } else if (ct.getRegexSelect() != null) {
                try {
                    List<String> valuesLs = RegexUtils.extract(contentStr, ct
                            .getRegexSelect());
                    if (valuesLs.size() > 0) {
                        ct.setValue(valuesLs.get(0));
                        ct.setValues(valuesLs.toArray(new String[0]));
                    }
                } catch (MalformedPatternException e) {
                    logger.error(e.getMessage());
                }
            }
        }

        return ctt;
    }

    /*
     * public List<Content> getContents() {
     * extrator.setContents(getParserConfig().getContents()); try {
     * extrator.extract(getInputStream()); } catch (Exception e) { // TODO
     * Auto-generated catch block e.printStackTrace(); } return
     * getParserConfig().getContents(); }
     */

}
