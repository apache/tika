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
package org.apache.tika.renderer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class RenderResult implements Closeable {

    public enum STATUS {
        SUCCESS,
        EXCEPTION,
        TIMEOUT
    }
    private final STATUS status;

    private final int id;

    private final Object result;

    //TODO: we're relying on metadata to bring in a bunch of info.
    //Might be cleaner to add specific parameters for page number, embedded path, etc.?
    private final Metadata metadata;

    TemporaryResources tmp = new TemporaryResources();

    public RenderResult(STATUS status, int id, Object result, Metadata metadata) {
        this.status = status;
        this.id = id;
        this.result = result;
        this.metadata = metadata;
        if (result instanceof Path) {
            tmp.addResource(new Closeable() {
                @Override
                public void close() throws IOException {
                    Files.delete((Path)result);
                }
            });
        } else if (result instanceof Closeable) {
            tmp.addResource((Closeable) result);
        }
    }

    public InputStream getInputStream() throws IOException {
        if (result instanceof Path) {
            return TikaInputStream.get((Path)result, metadata);
        } else {
            TikaInputStream tis = TikaInputStream.get(new byte[0]);
            tis.setOpenContainer(result);
            return tis;
        }
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public STATUS getStatus() {
        return status;
    }

    public int getId() {
        return id;
    }

    @Override
    public void close() throws IOException {
        tmp.close();
    }

}
