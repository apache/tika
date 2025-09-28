/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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
package org.apache.tika.server.controller;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.server.api.TranslateResourceApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * Translation controller.
 * 
 * Provides document translation capabilities with pluggable translators.
 * Note: This is a basic implementation that returns the original text.
 * A full implementation would require integration with translation services.
 */
@RestController
public class TranslateResourceController implements TranslateResourceApi {

    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public ResponseEntity<String> postTranslateAllSrcDest() {
        return ResponseEntity.ok("Translation not implemented - please POST document for translation");
    }

    @Override
    public ResponseEntity<String> putTranslateAllSrcDest() {
        return ResponseEntity.ok("Translation not implemented - please PUT document for translation");
    }

    @Override
    public ResponseEntity<String> postTranslateAllTranslatorSrcDest() {
        return ResponseEntity.ok("Translation not implemented - please POST document for translation");
    }

    @Override
    public ResponseEntity<String> putTranslateAllTranslatorSrcDest() {
        return ResponseEntity.ok("Translation not implemented - please PUT document for translation");
    }

    @RequestMapping(value = "/translate/all/src/dest", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> postTranslateAllSrcDestWithBody(@RequestBody byte[] body) {
        return translateDocument(body, "Translation not implemented - returning original text");
    }

    @RequestMapping(value = "/translate/all/src/dest", method = RequestMethod.PUT, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putTranslateAllSrcDestWithBody(@RequestBody byte[] body) {
        return translateDocument(body, "Translation not implemented - returning original text");
    }

    private ResponseEntity<String> translateDocument(byte[] body, String message) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(body)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            parser.parse(tikaInputStream, handler, new org.apache.tika.metadata.Metadata(), context);
            
            String extractedText = handler.toString();
            
            // For now, return the extracted text with a note that translation is not implemented
            String result = message + "\n\nExtracted text:\n" + extractedText;
            
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(result);
                
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document for translation: " + e.getMessage());
        }
    }
}
