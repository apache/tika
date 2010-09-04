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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;

import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.IOUtils;

class ForkClient {

    private final ClassLoader loader;

    private final File directory;

    private final Process process;

    private final DataOutputStream output;

    private final DataInputStream input;

    private final InputStream error;

    public ForkClient(ClassLoader loader) throws IOException {
        this.loader = loader;

        this.directory = File.createTempFile("apache-tika-", "-oop");
        directory.delete();
        directory.mkdir();

        boolean ok = false;
        try {
            copyClassToDirectory(ForkServer.class);
            copyClassToDirectory(ForkSerializer.class);

            ProcessBuilder builder = new ProcessBuilder();
            builder.directory(directory);
            builder.command("java", ForkServer.class.getName());
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

    private void copyClassToDirectory(Class<?> klass)
            throws FileNotFoundException, IOException {
        String path = klass.getName().replace('.', '/') + ".class";
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

    public synchronized Object echo(Object message) throws IOException {
        consumeErrors();
        output.write(ForkServer.ECHO);
        ForkSerializer.serialize(output, message);
        output.flush();

        readResponseType();
        try {
            return ForkSerializer.deserialize(input, loader).toString();
        } catch (ClassNotFoundException e) {
            throw new IOExceptionWithCause("Unable to read echo response", e);
        }
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

    private byte readResponseType() throws IOException {
        while (true) {
            consumeErrors();
            int type = input.read();
            if (type == -1) {
                throw new IOException("Unexpected end of stream encountered");
            } else if (type == ForkServer.FIND_RESOURCE) {
                findResource(input.readUTF());
            } else if (type == ForkServer.FIND_RESOURCES) {
                findResources(input.readUTF());
            } else {
                return (byte) type;
            }
        }
    }

    private void findResource(String name) throws IOException {
        InputStream stream = loader.getResourceAsStream(name);
        if (stream != null) {
            output.writeBoolean(true);
            writeAndCloseStream(stream);
        } else {
            output.writeBoolean(false);
        }
        output.flush();
    }

    private void findResources(String name) throws IOException {
        Enumeration<URL> resources = loader.getResources(name);
        while (resources.hasMoreElements()) {
            output.writeBoolean(true);
            writeAndCloseStream(resources.nextElement().openStream());
        }
        output.writeBoolean(false);
        output.flush();
    }

    private void writeAndCloseStream(InputStream stream) throws IOException {
        try {
            byte[] buffer = new byte[0xffff];
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

    private void consumeErrors() throws IOException {
        int n;
        while ((n = error.available()) > 0) {
            byte[] b = new byte[n];
            n = error.read(b);
            if (n > 0) {
                System.err.write(b, 0, n);
            }
        }
    }

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
