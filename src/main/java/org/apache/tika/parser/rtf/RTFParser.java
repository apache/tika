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
package org.apache.tika.parser.rtf;

import java.io.IOException;
import java.io.InputStream;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

/**
 * RTF parser
 */
public class RTFParser implements Parser {

    public String parse(
            InputStream stream, Iterable<Content> contents, Metadata metadata)
            throws IOException, TikaException {
        try {
            DefaultStyledDocument sd = new DefaultStyledDocument();
            new RTFEditorKit().read(stream, sd, 0);
            return sd.getText(0, sd.getLength());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("Error parsing an RTF document", e);
        }
    }

}
