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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

class ForkServer extends ClassLoader {

    public static final byte ERROR = -1;

    public static final byte REPLY = 0;

    public static final byte CALL = 1;

    public static final byte RESOURCE = 2;

    /**
     * Starts a forked server process.
     * 
     * @param args command line arguments, ignored
     * @throws Exception if the server could not be started
     */
    public static void main(String[] args) throws Exception {
        ForkServer server =
            new ForkServer(System.in, System.out);

        // Set the server instance as the context class loader
        // to make classes from the parent process available
        Thread.currentThread().setContextClassLoader(server);

        // Redirect standard input and output streams to prevent
        // stray code from interfering with the message stream
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);

        // Start processing request
        server.run();
    }

    private final DataInputStream input;

    private final DataOutputStream output;

    /**
     * Sets up a forked server instance using the given stdin/out
     * communication channel.
     *
     * @param input input stream for reading from the parent process
     * @param output output stream for writing to the parent process
     * @throws IOException if the server instance could not be created
     */
    public ForkServer(InputStream input, OutputStream output)
            throws IOException {
        this.input = new DataInputStream(input);
        this.output = new DataOutputStream(output);
    }

    public void run() throws IOException {
        int b;
        while ((b = input.read()) != -1) {
            if (b == CALL) {
                try {
                    call();
                } catch (Exception e) {
                    output.write(ERROR);
                    ForkSerializer.serialize(output, e);
                }
                output.flush();
            }
        }
    }

    private void call() throws Exception {
        ClassLoader loader = (ClassLoader) ForkSerializer.deserialize(
                input, output, ForkServer.class.getClassLoader());
        System.err.println("Loader loaded");
        Object object = ForkSerializer.deserialize(input, output, loader);
        System.err.println("Object loaded");
        Method method = getMethod(object, input.readUTF());
        System.err.println("Method loaded");
        int n = method.getParameterTypes().length;
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = ForkSerializer.deserialize(input, output, loader);
        }
        method.invoke(object, args);
        output.write(REPLY);
        output.flush();
    }

    private Method getMethod(Object object, String name) {
        Class<?> klass = object.getClass();
        for (Method method : klass.getMethods()) {
            if (name.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

}
