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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class JsonFetchEmitTupleListTest {

    @Test
    public void testBasic() throws Exception {
        List<FetchEmitTuple> list = new ArrayList<>();
        for (int i = 0; i < 57; i++) {
            list.add(getFetchEmitTuple(i));
        }
        StringWriter writer = new StringWriter();
        JsonFetchEmitTupleList.toJson(list, writer);

        Reader reader = new StringReader(writer.toString());
        List<FetchEmitTuple> deserialized = JsonFetchEmitTupleList.fromJson(reader);
        assertEquals(list, deserialized);
    }

    private FetchEmitTuple getFetchEmitTuple(int i) {
        Metadata m = new Metadata();
        m.add("m1", "v1-" + i);
        m.add("m1", "v1-" + i);
        m.add("m2", "v2-" + i);
        m.add("m2", "v3-" + i);
        m.add("m3", "v4-" + i);

        return new FetchEmitTuple("id-" + i,
                new FetchKey("fetcher-" + i, "fetchkey-" + i),
                new EmitKey("emitter-" + i, "emitKey-" + i), m);
    }
}
