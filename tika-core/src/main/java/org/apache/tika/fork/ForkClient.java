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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.io.IOUtils;
import org.xml.sax.ContentHandler;

class ForkClient {

    private final String java = "java"; // TODO: Make configurable

    private final ClassLoader loader;

    private final File directory;

    private final Process process;

    private final DataOutputStream output;

    private final DataInputStream input;

    private final InputStream error;

    public ForkClient(ClassLoader loader) throws IOException {
        this.loader = loader;

        this.directory = File.createTempFile("apache-tika-", "-fork");
        directory.delete();
        directory.mkdir();

        boolean ok = false;
        try {
            copyClassToDirectory(ForkServer.class);
            copyClassToDirectory(ForkSerializer.class);
            copyClassToDirectory(ForkProxy.class);
            copyClassToDirectory(ClassLoaderProxy.class);

            ProcessBuilder builder = new ProcessBuilder();
            builder.directory(directory);
            builder.command(java, ForkServer.class.getName());
            this.process = builder.start();
            this.output = new DataOutputStream(process.getOutputStream());
            this.input = new DataInputStream(process.getInputStream());
            this.error = process.getErrorStream();

            ok = true;
        } finally {
            if (!ok) {
                delete(directory);
            }
        }
    }

    /**
     * Copies the <code>.class</code> file of the given class to the
     * directory from where the forked server process can load it
     * during startup before setting up the stdin/out communication
     * channel with the parent process.
     *
     * @param klass the class to be copied
     * @throws IOException if the class could not be copied
     */
    private void copyClassToDirectory(Class<?> klass) throws IOException {
        String path = klass.getName().replace('.', '/') + ".class";
        ClassLoader loader = klass.getClassLoader();
        InputStream input = loader.getResourceAsStream(path);
        try {
            File file = new File(directory, path);
            file.getParentFile().mkdirs();
            OutputStream output = new FileOutputStream(file);
            try {
                IOUtils.copy(input, output);
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    public synchronized void call(
            Object object, String method, Object... args)
            throws IOException {
        List<ForkResource> resources = new ArrayList<ForkResource>();
        sendObject(loader, resources);
        sendObject(object, resources);
        output.writeUTF("parse");
        for (int i = 0; i < args.length; i++) {
            sendObject(args[i], resources);
        }
        waitForResponse(resources);
    }

    /**
     * Serializes the object first into an in-memory buffer and then
     * writes it to the output stream with a preceding size integer.
     *
     * @param object object to be serialized
     * @param resources list of fork resources, used when adding proxies
     * @throws IOException if the object could not be serialized
     */
    private void sendObject(Object object, List<ForkResource> resources)
            throws IOException {
        int n = resources.size();
        if (object instanceof InputStream) {
            resources.add(new InputStreamResource((InputStream) object));
            object = new InputStreamProxy(n);
        } else if (object instanceof ContentHandler) {
            resources.add(new ContentHandlerResource((ContentHandler) object));
            object = new ContentHandlerProxy(n);
        } else if (object instanceof ClassLoader) {
            resources.add(new ClassLoaderResource((ClassLoader) object));
            object = new ClassLoaderProxy(n);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream serializer = new ObjectOutputStream(buffer);
        serializer.writeObject(object);
        serializer.close();

        byte[] data = buffer.toByteArray();
        output.writeInt(data.length);
        output.write(data);

        waitForResponse(resources);
    }

    public synchronized void close() {
        try {
            output.close();
            input.close();
            error.close();
        } catch (IOException ignore) {
        }
        process.destroy();
        delete(directory);
    }

    private byte waitForResponse(List<ForkResource> resources)
            throws IOException {
        output.flush();
        while (true) {
            consumeErrorStream();
            int type = input.read();
            if (type == -1) {
                throw new IOException(
                        "Lost connection to a forked server process");
            } else if (type == ForkServer.RESOURCE) {
                ForkResource resource =
                    resources.get(input.readUnsignedByte());
                resource.process(input, output);
            } else {
                return (byte) type;
            }
        }
    }

    /**
     * Consumes all pending bytes from the standard error stream of the
     * forked server process, and prints them out to the standard error
     * stream of this process. This method should be called always before
     * expecting some output from the server, to prevent the server from
     * blocking due to a filled up pipe buffer of the error stream.
     *
     * @throws IOException if the error stream could not be read
     */
    private void consumeErrorStream() throws IOException {
        int n;
        while ((n = error.available()) > 0) {
            byte[] b = new byte[n];
            n = error.read(b);
            if (n > 0) {
                System.err.write(b, 0, n);
            }
        }
    }

    /**
     * Recursively deletes the given file or directory.
     *
     * @param file file or directory
     */
    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

}
