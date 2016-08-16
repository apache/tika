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

package org.apache.tika.parser.tagratio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

//Tika Utility to invoke Tika AutoDetect Parser

public class TikaUtil {

	//Parse the contents of the file as input to an intermediate XHTML file 
	public String parseToHTML(String filePath) throws IOException, SAXException, TikaException {
		ContentHandler handler = new ToXMLContentHandler();

		InputStream inputStream = new FileInputStream(new File(filePath));

		AutoDetectParser parser = new AutoDetectParser();	//Invoke Tika's AutoDetect Parser
		Metadata metadata = new Metadata();

		try (InputStream stream = inputStream) {
			parser.parse(stream, handler, metadata);
			return handler.toString();
		}	    
	}

}
