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

package org.apache.tika.example;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * This example demonstrates how to interrupt document parsing if
 * some condition is met.
 * <p>
 * {@link InterruptableParsingExample.InterruptingContentHandler} throws special exception as soon as
 * find {@code query} string in parsed file.
 * <p>
 * See also http://stackoverflow.com/questions/31939851
 */
public class InterruptableParsingExample {
    private Tika tika = new Tika(); // for default autodetect parser

    public boolean findInFile(String query, Path path) {
        InterruptingContentHandler handler = new InterruptingContentHandler(query);

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, tika.getParser());

        try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
            tika.getParser().parse(is, handler, metadata, context);
        } catch (QueryMatchedException e) {
            return true;
        } catch (SAXException | TikaException | IOException e) {
            // something went wrong with parsing...
            e.printStackTrace();
        }
        return false;
    }

    static class QueryMatchedException extends SAXException {
    }

    /**
     * Trivial content handler that searched for {@code query} in characters send to it.
     * <p>
     * Throws {@link QueryMatchedException} when query string is found.
     */
    static class InterruptingContentHandler extends DefaultHandler {
        private String query;
        private StringBuilder sb = new StringBuilder();

        InterruptingContentHandler(String query) {
            this.query = query;
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            sb.append(new String(ch, start, length).toLowerCase(Locale.getDefault()));

            if (sb.toString().contains(query)) {
                throw new QueryMatchedException();
            }

            if (sb.length() > 2 * query.length()) {
                sb.delete(0, sb.length() - query.length()); // keep tail with query.length() chars
            }
        }
    }
}
