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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.fuzzing.general.GeneralTransformer;

public class TestTransformer {

    @Test
    @Ignore
    public void testBasic() throws Exception {
        //turn into actual unit test
        Path path = Paths.get("");//put something meaningful here

        GeneralTransformer transformer = new GeneralTransformer();
        byte[] bytes = Files.readAllBytes(path);

        for (int i = 0; i < 100; i++) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            transformer.transform(new ByteArrayInputStream(bytes), bos);

            if (Arrays.equals(bos.toByteArray(), bytes)) {
                System.out.println("SAME");
            }
        }
    }
}
