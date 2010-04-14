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
package org.apache.tika.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TikaInputStream extends ProxyInputStream {

    public static TikaInputStream get(InputStream stream) {
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        } else {
            return new TikaInputStream(stream);
        }
    }

    private File file;

    private boolean temporary;

    private long position = 0;

    public TikaInputStream(InputStream stream) {
        super(stream);
        this.file = null;
        this.temporary = true;
    }

    public TikaInputStream(File file) {
        super(null);
        this.file = file;
        this.temporary = false;
    }

    public File getFile() throws IOException {
        if (file == null) {
            if (in == null) {
                throw new IOException("Stream has already been read");
            } else if (position > 0) {
                throw new IOException("Stream is already being read");
            } else {
                file = File.createTempFile("apache-tika-", ".tmp");
                OutputStream out = new FileOutputStream(file);
                try {
                    IOUtils.copy(in, out);
                } finally {
                    out.close();
                }
                in.close();
                in = null;
            }
        }
        return file;
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
        if (file != null) {
            if (temporary) {
                file.delete();
            }
            file = null;
        }
    }

    @Override
    protected void beforeRead(int n) throws IOException {
        if (in == null) {
            if (file != null) {
                in = new FileInputStream(file);
            } else {
                throw new IOException("End of the stream reached");
            }
        }
    }

    @Override
    protected void afterRead(int n) throws IOException {
        if (n != -1) {
            position += n;
        } else {
            close();
        }
    }

}
