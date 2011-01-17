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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

class ForkServer implements Runnable {

    public static final byte ERROR = -1;

    public static final byte REPLY = 0;

    public static final byte CALL = 1;

    public static final byte RESOURCE = 2;

    /**
     * Starts a forked server process using the standard input and output
     * streams for communication with the parent process. Any attempts by
     * stray code to read from standard input or write to standard output
     * is redirected to avoid interfering with the communication channel.
     * 
     * @param args command line arguments, ignored
     * @throws Exception if the server could not be started
     */
    public static void main(String[] args) throws Exception {
        ForkServer server = new ForkServer(System.in, System.out);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);
        server.run();
    }

    /** Input stream for reading from the parent process */
    private final DataInputStream input;

    /** Output stream for writing to the parent process */
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

    public void run() {
        try {
            while (true) {
                ClassLoader loader = (ClassLoader) readObject(
                        ForkServer.class.getClassLoader());
                Thread.currentThread().setContextClassLoader(loader);

                Object object = readObject(loader);
                Method method = getMethod(object, input.readUTF());
                Object[] args = new Object[method.getParameterTypes().length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = readObject(loader);
                }
                method.invoke(object, args);

                output.write(REPLY);
                output.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
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

    /**
     * Deserializes an object from the given stream. The serialized object
     * is expected to be preceded by a size integer, that is used for reading
     * the entire serialization into a memory before deserializing it.
     *
     * @param input input stream from which the serialized object is read
     * @param loader class loader to be used for loading referenced classes
     * @throws IOException if the object could not be deserialized
     * @throws ClassNotFoundException if a referenced class is not found
     */
    private Object readObject(ClassLoader loader)
            throws IOException, ClassNotFoundException {
        int n = input.readInt();
        byte[] data = new byte[n];
        input.readFully(data);

        ObjectInputStream deserializer =
            new ForkSerializer(new ByteArrayInputStream(data), loader);
        Object object = deserializer.readObject();
        if (object instanceof ForkProxy) {
            ((ForkProxy) object).init(input, output);
        }

        // Tell the parent process that we successfully received this object
        output.writeByte(ForkServer.REPLY);
        output.flush();

        return object;
    }

}
