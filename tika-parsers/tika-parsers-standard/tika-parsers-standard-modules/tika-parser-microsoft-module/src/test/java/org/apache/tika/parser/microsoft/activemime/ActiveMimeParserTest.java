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
package org.apache.tika.parser.microsoft.activemime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class ActiveMimeParserTest extends TikaTest {


    @Test
    @Disabled("until we get permission to use the file")
    public void testBasic() throws Exception {
        //file used in testing is here: https://telparia.com/fileFormatSamples/archive/activeMime/editdata.mso
        //if we get permission to add it to our repo, these should work
        Path p = Paths.get(".../editdata.mso");
        List<Metadata> metadataList = getRecursiveMetadata(p);
        assertEquals("application/x-activemime", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals(5, metadataList.size());
        assertContains("Arquivo Gerado com sucesso!!!",
                metadataList.get(4).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
