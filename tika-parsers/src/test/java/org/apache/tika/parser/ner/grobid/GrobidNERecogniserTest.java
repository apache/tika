/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright owlocationNameEntitieship.
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

package org.apache.tika.parser.ner.grobid;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.junit.Test;

/**
*Test case for {@link Grobid NER}
*/
public class GrobidNERecogniserTest {
	 	
		@Test
	    public void testGetEntityTypes() throws Exception {
	        String text = "I've lost one minute.";
	        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, GrobidNERecogniser.class.getName());
	        Tika tika = new Tika(new TikaConfig(NamedEntityParser.class.getResourceAsStream("tika-config.xml")));
	        Metadata md = new Metadata();
	        tika.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), md);
	        
	        HashSet<String> set = new HashSet<String>();
	        
	        set.clear();
	        set.addAll(Arrays.asList(md.getValues("NER_MEASUREMENT_NUMBERS")));
	        assertTrue(set.contains("one"));

	        set.clear();
	        set.addAll(Arrays.asList(md.getValues("NER_MEASUREMENT_UNITS")));
	        assertTrue(set.contains("minute"));

	        set.clear();
	        set.addAll(Arrays.asList(md.getValues("NER_MEASUREMENTS")));
	        assertTrue(set.contains("one minute"));

	        set.clear();
	        set.addAll(Arrays.asList(md.getValues("NER_NORMALIZED_MEASUREMENTS")));
	        assertTrue(set.contains("60 s"));
         }
}


