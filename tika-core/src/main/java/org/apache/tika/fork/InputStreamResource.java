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

class InputStreamResource implements ForkResource {

    private final InputStream stream;

    public InputStreamResource(InputStream stream) {
        this.stream = stream;
    }

    public Throwable process(DataInputStream input, DataOutputStream output)
            throws IOException {
        int n = input.readInt();
        byte[] buffer = new byte[n];
        int m;
        try {
            m = stream.read(buffer);
        } catch (IOException e) {
            // returning exception causes deadlock
            // return e;
            e.printStackTrace();
            m = -1;
        }
        output.writeInt(m);
        if (m > 0) {
            output.write(buffer, 0, m);
        }
        output.flush();
        return null;
    }

}
