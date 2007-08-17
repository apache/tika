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
package org.apache.tika.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.config.Content;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Class util
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */

public class Utils {

    static Logger logger = Logger.getRootLogger();

    public static void print(Collection<Content> structuredContent) {
        for (Iterator<Content> iter = structuredContent.iterator(); iter
                .hasNext();) {
            Content ct = iter.next();
            if (ct.getValue() != null) {
                System.out.print(ct.getName() + ": ");
                System.out.println(ct.getValue());
            } else if (ct.getValues() != null) {

                System.out.print(ct.getName() + ": ");
                for (int j = 0; j < ct.getValues().length; j++) {
                    if (j == 0)
                        System.out.println(ct.getValues()[j]);
                    else {
                        System.out.println("\t" + ct.getValues()[j]);
                    }
                }
            }
        }

    }

    public static Document parse(InputStream is) {
        org.jdom.Document xmlDoc = new org.jdom.Document();
        try {
            SAXBuilder builder = new SAXBuilder();
            builder.setValidation(false);
            xmlDoc = builder.build(is);
        } catch (JDOMException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return xmlDoc;
    }

    public static List unzip(InputStream is) {
        List res = new ArrayList();
        try {
            ZipInputStream in = new ZipInputStream(is);
            ZipEntry entry = null;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    stream.write(buf, 0, len);
                }
                InputStream isEntry = new ByteArrayInputStream(stream
                        .toByteArray());
                File file = File.createTempFile("tmp", "_" + entry.getName());
                copyInputStream(isEntry, new BufferedOutputStream(
                        new FileOutputStream(file)));
                res.add(file);
            }
            in.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return res;
    }

    private static void copyInputStream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.close();
    }

    public static void saveInXmlFile(Document doc, String file) {
        Format f = Format.getPrettyFormat().setEncoding("UTF-8");

        XMLOutputter xop = new XMLOutputter(f);

        try {

            xop.output(doc, new FileOutputStream(file));

        }

        catch (IOException ex) {

            logger.error(ex.getMessage());

        }

    }

}
