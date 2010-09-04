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

class ForkSerializer extends ObjectInputStream {

    private final ClassLoader loader;

    public ForkSerializer(InputStream input, ClassLoader loader)
            throws IOException {
        super(input);
        this.loader = loader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException {
        return Class.forName(desc.getName(), false, loader);
    }

    static void serialize(DataOutputStream output, Object object)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        ObjectOutputStream serializer = new ObjectOutputStream(buffer);
        serializer.writeObject(object);
        serializer.close();

        byte[] data = buffer.toByteArray();
        output.writeInt(data.length);
        output.write(data);
    }

    static Object deserialize(DataInputStream input, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        int n = input.readInt();
        byte[] data = new byte[n];
        input.readFully(data);

        ObjectInputStream deserializer =
            new ForkSerializer(new ByteArrayInputStream(data), loader);
        return deserializer.readObject();
    }

}
