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
package org.apache.tika.fork;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Semaphore;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class ForkParserTest extends TestCase {

    public void testHelloWorld() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParserTest.class.getClassLoader(),
                new ForkTestParser());
        try {
            Metadata metadata = new Metadata();
            ContentHandler output = new BodyContentHandler();
            InputStream stream = new ByteArrayInputStream(new byte[0]);
            ParseContext context = new ParseContext();
            parser.parse(stream, output, metadata, context);
            assertEquals("Hello, World!", output.toString().trim());
            assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        } finally {
            parser.close();
        }
    }

    public void testSerialParsing() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParserTest.class.getClassLoader(),
                new ForkTestParser());
        try {
            ParseContext context = new ParseContext();
            for (int i = 0; i < 10; i++) {
                ContentHandler output = new BodyContentHandler();
                InputStream stream = new ByteArrayInputStream(new byte[0]);
                parser.parse(stream, output, new Metadata(), context);
                assertEquals("Hello, World!", output.toString().trim());
            }
        } finally {
            parser.close();
        }
    }

    public void testParallelParsing() throws Exception {
        final ForkParser parser = new ForkParser(
                ForkParserTest.class.getClassLoader(),
                new ForkTestParser());
        try {
            final ParseContext context = new ParseContext();

            Thread[] threads = new Thread[10];
            ContentHandler[] output = new ContentHandler[threads.length];
            for (int i = 0; i < threads.length; i++) {
                final ContentHandler o = new BodyContentHandler();
                output[i] = o;
                threads[i] = new Thread() {
                    public void run() {
                        try {
                            InputStream stream =
                                new ByteArrayInputStream(new byte[0]);
                            parser.parse(stream, o, new Metadata(), context);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                threads[i].start();
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
                assertEquals("Hello, World!", output[i].toString().trim());
            }
        } finally {
            parser.close();
        }
    }

    public void testPoolSizeReached() throws Exception {
        final ForkParser parser = new ForkParser(
                ForkParserTest.class.getClassLoader(),
                new ForkTestParser());
        try {
            final Semaphore barrier = new Semaphore(0);

            Thread[] threads = new Thread[parser.getPoolSize()];
            PipedOutputStream[] pipes = new PipedOutputStream[threads.length];
            final ParseContext context = new ParseContext();
            for (int i = 0; i < threads.length; i++) {
                final PipedInputStream input = new PipedInputStream() {
                    @Override
                    public synchronized int read() throws IOException {
                        barrier.release();
                        return super.read();
                    }
                };
                pipes[i] = new PipedOutputStream(input);
                threads[i] = new Thread() {
                    public void run() {
                        try {
                            ContentHandler o = new DefaultHandler();
                            parser.parse(input, o, new Metadata(), context);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                threads[i].start();
            }

            // Wait until all the background parsers have been started
            barrier.acquire(parser.getPoolSize());

            final ContentHandler o = new BodyContentHandler();
            Thread blocked = new Thread() {
                public void run() {
                    try {
                        barrier.release();
                        InputStream stream =
                            new ByteArrayInputStream(new byte[0]);
                        parser.parse(stream, o, new Metadata(), context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            blocked.start();

            // Wait until the last thread is started, and then some to
            // make sure that it would have had a chance to start processing
            // data had it not been blocked.
            barrier.acquire();
            Thread.sleep(1000);

            assertEquals("", o.toString());

            for (int i = 0; i < threads.length; i++) {
                pipes[i].close();
                threads[i].join();
            }

            blocked.join();
            assertEquals("Hello, World!", o.toString().trim());
        } finally {
            parser.close();
        }
    }

}
