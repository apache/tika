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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

class ClassLoaderResource implements ForkResource {

    private final ClassLoader loader;

    public ClassLoaderResource(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Processes a request for one (code 1) or many (code 2) class loader
     * resources. The requested resources are sent preceded with a boolean
     * <code>true</code> value. If the resource was not found (code 1) or
     * when the last resource has been sent (code 2), a boolean
     * <code>false</code> value is sent instead.
     *
     * @param name resource name
     * @throws IOException if the resource could not be sent
     */
    public Throwable process(DataInputStream input, DataOutputStream output)
            throws IOException {
        byte type = input.readByte();
        String name = input.readUTF();
        if (type == 1) {
            InputStream stream = loader.getResourceAsStream(name);
            if (stream != null) {
                output.writeBoolean(true);
                writeAndCloseStream(output, stream);
            } else {
                output.writeBoolean(false);
            }
        } else if (type == 2) {
            Enumeration<URL> resources = loader.getResources(name);
            while (resources.hasMoreElements()) {
                output.writeBoolean(true);
                InputStream stream = resources.nextElement().openStream();
                writeAndCloseStream(output, stream);
            }
            output.writeBoolean(false);
        }
        output.flush();
        return null;
    }

    /**
     * Sends the contents of the given input stream to the given output.
     * The stream is sent in chunks of less than 64kB, each preceded by
     * a 16-bit integer value that indicates the length of the following
     * chunk. A zero short value is sent at the end to signify the end of
     * the stream.
     * <p>
     * The stream is guaranteed to be closed by this method, regardless of
     * the way it returns.
     *
     * @param stream the stream to be sent
     * @throws IOException if the stream could not be sent
     */
    private void writeAndCloseStream(
            DataOutputStream output, InputStream stream) throws IOException {
        try {
            byte[] buffer = new byte[0x10000 - 1];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                output.writeShort(n);
                output.write(buffer, 0, n);
            }
            output.writeShort(0);
        } finally {
            stream.close();
        }
    }

}
