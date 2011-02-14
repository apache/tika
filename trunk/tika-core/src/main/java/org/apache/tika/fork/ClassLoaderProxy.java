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
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ClassLoaderProxy extends ClassLoader implements ForkProxy {

    /** Serial version UID */
    private static final long serialVersionUID = -7303109260448540420L;

    /**
     * Names of resources that could not be found. Used to avoid repeated
     * lookup of commonly accessed, but often not present, resources like
     * <code>META-INF/services/javax.xml.parsers.SAXParserFactory</code>.
     */
    private final Set<String> notFound = new HashSet<String>();

    private final int resource;

    private transient DataInputStream input;

    private transient DataOutputStream output;

    public ClassLoaderProxy(int resource) {
        this.resource = resource;
    }

    public void init(DataInputStream input, DataOutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    protected synchronized URL findResource(String name) {
        if (notFound.contains(name)) {
            return null;
        }
        try {
            // Send a request to load the resource data
            output.write(ForkServer.RESOURCE);
            output.write(resource);
            output.write(1);
            output.writeUTF(name);
            output.flush();

            // Receive the response
            if (input.readBoolean()) {
                return MemoryURLStreamHandler.createURL(readStream());
            } else {
                notFound.add(name);
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected synchronized Enumeration<URL> findResources(String name)
            throws IOException {
        // Send a request to load the resources
        output.write(ForkServer.RESOURCE);
        output.write(resource);
        output.write(2);
        output.writeUTF(name);
        output.flush();

        // Receive the response
        List<URL> resources = new ArrayList<URL>();
        while (input.readBoolean()) {
            resources.add(MemoryURLStreamHandler.createURL(readStream()));
        }
        return Collections.enumeration(resources);
    }

    @Override
    protected synchronized Class<?> findClass(String name)
            throws ClassNotFoundException {
        try {
            // Send a request to load the class data
            output.write(ForkServer.RESOURCE);
            output.write(resource);
            output.write(1);
            output.writeUTF(name.replace('.', '/') + ".class");
            output.flush();

            // Receive the response
            if (input.readBoolean()) {
                byte[] data = readStream();
                return defineClass(name, data, 0, data.length);
            } else {
                throw new ClassNotFoundException("Unable to find class " + name);
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("Unable to load class " + name, e);
        }
    }

    private byte[] readStream() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xffff];
        int n;
        while ((n = input.readUnsignedShort()) > 0) {
            input.readFully(buffer, 0, n);
            stream.write(buffer, 0, n);
        }
        return stream.toByteArray();
    }

}
