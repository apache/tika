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
package org.apache.tika.digest;

import java.io.IOException;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;


public class CompositeDigester implements Digester {

    private final Digester[] digesters;

    public CompositeDigester(Digester... digesters) {
        this.digesters = digesters;
    }

    @Override
    public void digest(TikaInputStream tis, Metadata m, ParseContext parseContext) throws IOException {
        for (Digester digester : digesters) {
            digester.digest(tis, m, parseContext);
        }
    }

    /** Fans each write out to every child's sink; abort and close reach them all. */
    @Override
    public DigestSink digestSink(Metadata m, ParseContext parseContext) throws IOException {
        DigestSink[] sinks = new DigestSink[digesters.length];
        try {
            for (int i = 0; i < digesters.length; i++) {
                sinks[i] = digesters[i].digestSink(m, parseContext);
            }
        } catch (IOException | RuntimeException e) {
            try {
                TemporaryResources.closeAll(sinks);
            } catch (Throwable t) {
                e.addSuppressed(t);
            }
            throw e;
        }
        return new DigestSink() {
            @Override
            public void write(int b) throws IOException {
                ensureOpen();
                for (DigestSink sink : sinks) {
                    sink.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ensureOpen();
                for (DigestSink sink : sinks) {
                    sink.write(b, off, len);
                }
            }

            @Override
            protected void finish(boolean publish) throws IOException {
                if (!publish) {
                    for (DigestSink sink : sinks) {
                        sink.abort();
                    }
                }
                TemporaryResources.closeAll(sinks);
            }
        };
    }
}
