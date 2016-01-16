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

package org.apache.tika.parser.geoinfo;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.geoinfo.GeographicInformationParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import java.io.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class GeographicInformationParserTest {

    @Test
    public void testISO19139() throws Exception{
        String path ="/test-documents/sampleFile.iso19139";
		
        Metadata metadata = new Metadata();
        Parser parser=new org.apache.tika.parser.geoinfo.GeographicInformationParser();
        ContentHandler contentHandler=new BodyContentHandler();
        ParseContext parseContext=new ParseContext();
        
        InputStream inputStream = GeographicInformationParser.class.getResourceAsStream(path);
       
        parser.parse(inputStream, contentHandler, metadata, parseContext);

        assertEquals("text/iso19139+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", metadata.get("CharacterSet"));
        assertEquals("https", metadata.get("TransferOptionsOnlineProtocol "));
        assertEquals("browser", metadata.get("TransferOptionsOnlineProfile "));
        assertEquals("Barrow Atqasuk ARCSS Plant", metadata.get("TransferOptionsOnlineName "));

        String content = contentHandler.toString();
        assertTrue(content.contains("Barrow Atqasuk ARCSS Plant"));
        assertTrue(content.contains("GeographicElementWestBoundLatitude	-157.24"));
        assertTrue(content.contains("GeographicElementEastBoundLatitude	-156.4"));
        assertTrue(content.contains("GeographicElementNorthBoundLatitude	71.18"));
        assertTrue(content.contains("GeographicElementSouthBoundLatitude	70.27"));

    }

}
