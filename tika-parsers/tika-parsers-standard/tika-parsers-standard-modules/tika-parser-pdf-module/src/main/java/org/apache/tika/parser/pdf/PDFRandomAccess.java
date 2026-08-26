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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;

/**
 * One way to hand PDFBox a document: in-memory content is read in place through a zero-copy
 * view, anything else through PDFBox's buffered file reader. Neither copies the document, and
 * every call starts from byte 0 regardless of the stream's position -- so a renderer can
 * re-open the same document per page. Closing the returned reader releases the view.
 */
public final class PDFRandomAccess {

    private PDFRandomAccess() {
    }

    public static RandomAccessRead open(TikaInputStream tis, ParseContext context)
            throws IOException {
        if (tis.hasFile()) {
            return new RandomAccessReadBufferedFile(tis.getFile());
        }
        tis.enableRewind(context == null ? null : context.get(CacheMemoryBudget.class));
        SeekableByteChannel channel = tis.getSeekableByteChannel();
        ByteBuffer view = TikaInputStream.inMemoryContent(channel);
        if (view == null) {
            // the drain spilled: the content is on disk now
            channel.close();
            return new RandomAccessReadBufferedFile(tis.getFile());
        }
        return new RandomAccessReadBuffer(view) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    channel.close();
                }
            }
        };
    }
}
