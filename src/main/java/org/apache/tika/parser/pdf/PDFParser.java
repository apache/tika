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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Content;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.RegexUtils;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.pdfbox.exceptions.CryptographyException;
import org.pdfbox.exceptions.InvalidPasswordException;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;

/**
 * PDF parser
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class PDFParser extends Parser {
    static Logger logger = Logger.getRootLogger();

    private String contentStr = "";

    private PDDocument pdfDocument = null;

    private Map<String, Content> contentsMap;

    public String getStrContent() {

        try {
            pdfDocument = PDDocument.load(getInputStream());
            if (pdfDocument.isEncrypted()) {
                pdfDocument.decrypt("");
            }
            StringWriter writer = new StringWriter();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.writeText(pdfDocument, writer);
            contentStr = writer.getBuffer().toString();
        } catch (CryptographyException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } catch (InvalidPasswordException e) {
            logger.error(e.getMessage());
        } finally {
            if (pdfDocument != null) {
                try {
                    pdfDocument.close();
                } catch (IOException ex) {
                    logger.error(ex.getMessage());
                }
            }
        }
        return contentStr;
    }

    public List<Content> getContents() {

        // String contents = getContent();
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

                } else {
                    try {
                        PDDocumentInformation metaData = pdfDocument
                                .getDocumentInformation();
                        if (ct.getTextSelect().equalsIgnoreCase("title")) {
                            if (metaData.getTitle() != null) {
                                ct.setValue(metaData.getTitle());

                            }
                        } else if (ct.getTextSelect()
                                .equalsIgnoreCase("author")) {
                            if (metaData.getAuthor() != null) {
                                ct.setValue(metaData.getAuthor());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "creator")) {
                            if (metaData.getCreator() != null) {
                                ct.setValue(metaData.getCreator());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "keywords")) {
                            if (metaData.getKeywords() != null) {
                                ct.setValue(metaData.getKeywords());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "producer")) {
                            if (metaData.getProducer() != null) {
                                ct.setValue(metaData.getProducer());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "subject")) {
                            if (metaData.getSubject() != null) {
                                ct.setValue(metaData.getSubject());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "trapped")) {
                            if (metaData.getTrapped() != null) {
                                ct.setValue(metaData.getTrapped());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "creationDate")) {
                            if (metaData.getCreationDate() != null) {
                                ct.setValue(metaData.getCreationDate()
                                        .getTime().toString());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "modificationDate")) {
                            if (metaData.getModificationDate() != null) {
                                ct.setValue(metaData.getModificationDate()
                                        .getTime().toString());

                            }
                        } else if (ct.getTextSelect().equalsIgnoreCase(
                                "summary")) {
                            int summarySize = Math
                                    .min(contentStr.length(), 500);
                            String summary = contentStr.substring(0,
                                    summarySize);
                            ct.setValue(summary);
                        }
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
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

    public Content getContent(String name) {
        if (contentsMap == null || contentsMap.isEmpty()) {
            getContents();
        }
        return contentsMap.get(name);
    }

}
