/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import static org.junit.Assert.fail;

public class TaggedInputStreamTest {

    @Test
    public void createdIOExceptionIsSerializable() {
        try {
            new TaggedInputStream(null).handleIOException(new IOException("Dummy"));
        } catch (IOException e) {
            assertCanSerialize(e);
        }
    }

    private static void assertCanSerialize(Object e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(out);
            oos.writeObject(e);
        } catch (IOException e1) {
            fail(e1.getMessage());
        } finally {
            if (oos != null)
                try {
                    oos.close();
                } catch (IOException ignore) {
                }
        }
    }

}