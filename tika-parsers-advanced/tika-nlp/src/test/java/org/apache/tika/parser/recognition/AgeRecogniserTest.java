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
package org.apache.tika.parser.recognition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.junit.Assert;
import org.junit.Test;

import edu.usc.irds.agepredictor.authorage.AgePredicterLocal;


public class AgeRecogniserTest extends TikaTest {

    private static final String CONFIG_FILE = "tika-config-age.xml";
    private static final String TEST_TEXT = "I am student at University of Southern California (USC)," +
            " located in Los Angeles . USC's football team is called by name Trojans." +
            " Mr. John McKay was a head coach of the team from 1960 - 1975";
    private static final double TEST_AGE = 26.4;
    
    static{
    	/**
    	 * Injecting mock AgeClassifer into AgeParser to generate test response
    	 */
    	AgePredicterLocal mockAgeClassifier = mock(AgePredicterLocal.class);
	AgeRecogniser.setAgePredictorClient(mockAgeClassifier);
    	
		try {
			when(mockAgeClassifier.predictAge(TEST_TEXT)).thenReturn(TEST_AGE);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    @Test
    public void testAgeRecogniser() throws Exception {

        //test config is added to resources directory
        TikaConfig config = new TikaConfig(getClass().getResourceAsStream(CONFIG_FILE));
        Tika tika = new Tika(config);
        
        Metadata md = new Metadata();
        tika.parse(new ByteArrayInputStream(TEST_TEXT.getBytes(Charset.defaultCharset())), md);
        
        Assert.assertArrayEquals("Age Parser not invoked.",new String[] {CompositeParser.class.getCanonicalName(), 
        		AgeRecogniser.class.getCanonicalName()} , md.getValues("X-Parsed-By"));
        Assert.assertArrayEquals("Wrong age predicted.", new String[] {Double.toString(TEST_AGE)} , md.getValues(AgeRecogniser.MD_KEY_ESTIMATED_AGE));
        
        
    }

}