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
package org.apache.tika.utils;

import static org.apache.tika.metadata.TikaCoreProperties.EMBEDDED_EXCEPTION;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * Helper util methods for Parsers themselves.
 */
public class ParserUtils {

    public final static Property EMBEDDED_PARSER = Property.internalText(
            TikaCoreProperties.TIKA_META_EXCEPTION_PREFIX + "embedded_parser");


    /**
     * Does a deep clone of a Metadata object.
     */
    public static Metadata cloneMetadata(Metadata m) {
        Metadata clone = new Metadata();

        for (String n : m.names()) {
            if (!m.isMultiValued(n)) {
                clone.set(n, m.get(n));
            } else {
                String[] vals = m.getValues(n);
                for (String val : vals) {
                    clone.add(n, val);
                }
            }
        }
        return clone;
    }

    /**
     * Identifies the real class name of the {@link Parser}, unwrapping
     * any {@link ParserDecorator} decorations on top of it.
     */
    public static String getParserClassname(Parser parser) {
        if (parser instanceof ParserDecorator) {
            return ((ParserDecorator) parser).getWrappedParser().getClass().getName();
        } else {
            return parser.getClass().getName();
        }
    }

    /**
     * Records details of the {@link Parser} used to the {@link Metadata},
     * typically wanted where multiple parsers could be picked between
     * or used.
     */
    public static void recordParserDetails(Parser parser, Metadata metadata) {
        String className = getParserClassname(parser);
        recordParserDetails(className, metadata);
    }

    /**
     * Records details of the {@link Parser} used to the {@link Metadata},
     * typically wanted where multiple parsers could be picked between
     * or used.
     */
    public static void recordParserDetails(String parserClassName, Metadata metadata) {
        String[] parsedBys = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
        if (parsedBys == null || parsedBys.length == 0) {
            metadata.add(TikaCoreProperties.TIKA_PARSED_BY, parserClassName);
        } else if (Arrays.stream(parsedBys).noneMatch(parserClassName::equals)) {
            //only add parser once
            metadata.add(TikaCoreProperties.TIKA_PARSED_BY, parserClassName);
        }
    }

    /**
     * Records details of a {@link Parser}'s failure to the
     * {@link Metadata}, so you can check what went wrong even if the
     * {@link Exception} wasn't immediately thrown (eg when several different
     * Parsers are used)
     */
    public static void recordParserFailure(Parser parser, Throwable failure, Metadata metadata) {
        String trace = ExceptionUtils.getStackTrace(failure);
        metadata.add(EMBEDDED_EXCEPTION, trace);
        metadata.add(EMBEDDED_PARSER, getParserClassname(parser));
    }

    /**
     * Ensures that the Stream will be able to be re-read, by buffering to
     * a temporary file if required.
     * Streams that are automatically OK include {@link TikaInputStream}s
     * created from Files or InputStreamFactories, and {@link RereadableInputStream}.
     */
    public static InputStream ensureStreamReReadable(InputStream stream, TemporaryResources tmp)
            throws IOException {
        // If it's re-readable, we're done
        if (stream instanceof RereadableInputStream) {
            return stream;
        }

        // Make sure it's a TikaInputStream
        TikaInputStream tstream = TikaInputStream.cast(stream);
        if (tstream == null) {
            tstream = TikaInputStream.get(stream, tmp);
        }

        // If it's factory based, it's ok
        if (tstream.getInputStreamFactory() != null) {
            return tstream;
        }

        // Ensure it's file based
        tstream.getFile();
        // Prepare for future re-reads
        tstream.mark(-1);
        return tstream;
    }

    /**
     * Resets the given {@link TikaInputStream} (checked by
     * {@link #ensureStreamReReadable(InputStream, TemporaryResources)})
     * so that it can be re-read again.
     */
    public static InputStream streamResetForReRead(InputStream stream, TemporaryResources tmp)
            throws IOException {
        // If re-readable, rewind to start
        if (stream instanceof RereadableInputStream) {
            ((RereadableInputStream) stream).rewind();
            return stream;
        }

        // File or Factory based?
        TikaInputStream tstream = (TikaInputStream) stream;
        if (tstream.getInputStreamFactory() != null) {
            // Just get a fresh one each time from the factory
            return TikaInputStream.get(tstream.getInputStreamFactory(), tmp);
        }

        // File based, reset stream to beginning of File
        tstream.reset();
        tstream.mark(-1);
        return tstream;
    }
}
