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
package org.apache.tika.parser.txt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Text parser
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class TXTParser extends Parser {

    static Logger logger = Logger.getRootLogger();

    private Map<String, Content> contentsMap;

    private String contentStr;

    public Content getContent(String name) {
        if (contentsMap == null || contentsMap.isEmpty()) {
            getContents();
        }
        return contentsMap.get(name);
    }

    public List<Content> getContents() {
        if (contentStr == null) {
            contentStr = getStrContent();
        }
        List<Content> ctt = getParserConfig().getContents();
        contentsMap = new HashMap<String, Content>();
        Iterator i = ctt.iterator();
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
            contentsMap.put(ct.getName(), ct);
        }

        return getParserConfig().getContents();

    }

    @Override
    public String getStrContent() {
        StringBuffer sb = new StringBuffer();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    getInputStream()));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append(" ");
            }
        } catch (FileNotFoundException ex) {
            logger.error(ex.getMessage());
        } catch (IOException ex1) {
            logger.error(ex1.getMessage());
        } finally {
            try {
                getInputStream().close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        contentStr = sb.toString();
        return contentStr;
    }

}
