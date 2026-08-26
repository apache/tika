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
import java.io.OutputStream;

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

    /** Fans each write out to every child's sink; close closes them all. */
    @Override
    public OutputStream digestSink(Metadata m, ParseContext parseContext) throws IOException {
        OutputStream[] sinks = new OutputStream[digesters.length];
        try {
            for (int i = 0; i < digesters.length; i++) {
                sinks[i] = digesters[i].digestSink(m, parseContext);
            }
        } catch (IOException | RuntimeException e) {
            closeAll(sinks, e);
            throw e;
        }
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                for (OutputStream sink : sinks) {
                    sink.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                for (OutputStream sink : sinks) {
                    sink.write(b, off, len);
                }
            }

            @Override
            public void close() throws IOException {
                closeAll(sinks, null);
            }
        };
    }

    // Closes every non-null sink even if one throws; the first failure is what propagates.
    private static void closeAll(OutputStream[] sinks, Throwable pending) throws IOException {
        IOException first = null;
        for (OutputStream sink : sinks) {
            if (sink == null) {
                continue;
            }
            try {
                sink.close();
            } catch (IOException e) {
                if (pending != null) {
                    pending.addSuppressed(e);
                } else if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
