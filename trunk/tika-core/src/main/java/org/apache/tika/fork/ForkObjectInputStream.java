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
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

/**
 * An object input stream that uses a given class loader when deserializing
 * objects.
 * <p>
 * Note that this functionality could easily be implemented as a simple
 * anonymous {@link ObjectInputStream} subclass, but since the
 * functionality is needed during the somewhat complicated bootstrapping
 * of the stdin/out communication channel of a forked server process,
 * it's better if class has a stable name that can be referenced at
 * compile-time by the {@link ForkClient} class.
 */
class ForkObjectInputStream extends ObjectInputStream {

    /** The class loader used when deserializing objects. */
    private final ClassLoader loader;

    /**
     * Creates a new object input stream that uses the given class loader
     * when deserializing objects.
     *
     * @param input underlying input stream
     * @param loader class loader used when deserializing objects
     * @throws IOException if this stream could not be initiated
     */
    public ForkObjectInputStream(InputStream input, ClassLoader loader)
            throws IOException {
        super(input);
        this.loader = loader;
    }

    /**
     * Loads the identified class from the specified class loader.
     *
     * @param desc class description
     * @return class loaded class
     * @throws ClassNotFoundException if the class can not be found
     */
    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
            throws ClassNotFoundException {
        return Class.forName(desc.getName(), false, loader);
    }

    /**
     * Serializes the object first into an in-memory buffer and then
     * writes it to the output stream with a preceding size integer.
     *
     * @param object object to be serialized
     * @param output output stream
     * @throws IOException if the object could not be serialized
     */
    public static void sendObject(Object object, DataOutputStream output)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream serializer = new ObjectOutputStream(buffer);
        serializer.writeObject(object);
        serializer.close();

        byte[] data = buffer.toByteArray();
        output.writeInt(data.length);
        output.write(data);
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
    public static Object readObject(DataInputStream input, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        int n = input.readInt();
        byte[] data = new byte[n];
        input.readFully(data);

        ObjectInputStream deserializer =
            new ForkObjectInputStream(new ByteArrayInputStream(data), loader);
        return deserializer.readObject();
    }

}
