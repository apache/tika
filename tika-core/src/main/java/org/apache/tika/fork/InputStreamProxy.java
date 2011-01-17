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

class InputStreamProxy extends InputStream implements ForkProxy {

    /** Serial version UID */
    private static final long serialVersionUID = 4350939227765568438L;

    private final int resource;

    private transient DataInputStream input;

    private transient DataOutputStream output;

    public InputStreamProxy(int resource) {
        this.resource = resource;
    }

    public void init(DataInputStream input, DataOutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public int read() throws IOException {
        output.writeByte(ForkServer.RESOURCE);
        output.writeByte(resource);
        output.writeInt(1);
        output.flush();
        int n = input.readInt();
        if (n == 1) {
            return input.readUnsignedByte();
        } else {
            return n;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        output.writeByte(ForkServer.RESOURCE);
        output.writeByte(resource);
        output.writeInt(len);
        output.flush();
        int n = input.readInt();
        if (n > 0) {
            input.readFully(b, off, n);
        }
        return n;
    }

}
