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
package org.apache.tika.parser.asm;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for Java .class files.
 */
public class ClassParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        try {
            ClassVisitor visitor = new XHTMLClassVisitor(handler, metadata);
            ClassReader reader = new ClassReader(stream);
            reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Failed to parse a Java class", e);
            }
        }
    }

}
