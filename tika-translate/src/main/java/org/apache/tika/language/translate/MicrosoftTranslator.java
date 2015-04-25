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

package org.apache.tika.language.translate;

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Wrapper class to access the Windows translation service. This class uses the com.memetix.mst
 * package as a wrapper for the API calls.
 * @since Tika 1.6
 */
public class MicrosoftTranslator implements Translator {

    boolean available;              // Flag for whether or not translation is available.
    String clientId, clientSecret;  // Keys used for the API calls.

    public static final String PROPERTIES_FILE = "org/apache/tika/language/translate/translator.microsoft.properties";
    public static final String ID_PROPERTY = "translator.client-id";
    public static final String SECRET_PROPERTY = "translator.client-secret";
    public static final String DEFAULT_ID = "dummy-id";
    public static final String DEFAULT_SECRET = "dummy-secret";

    /**
     * Create a new MicrosoftTranslator with the client keys specified in
     * resources/org/apache/tika/language/translate/translator.microsoft.properties. Silently becomes unavailable
     * when client keys are unavailable. translator.microsoft.client-id and translator.client-secret must be set
     * in translator.microsoft.properties for translation to work.
     * @since Tika 1.6
     */
    public MicrosoftTranslator() {
        Properties props = new Properties();
        InputStream stream;
        stream = MicrosoftTranslator.class.getResourceAsStream(PROPERTIES_FILE);
        try {
            if(stream != null) {
                props.load(stream);
                clientId = props.getProperty(ID_PROPERTY);
                clientSecret = props.getProperty(SECRET_PROPERTY);
                this.available = checkAvailable();   
            }
        } catch (IOException e) {
        	e.printStackTrace();
            // Error with properties file. Translation will not work.
            available = false;
        }
    }

    /**
     * Use the Microsoft service to translate the given text from the given source language to the given target.
     * You must set the client keys in translator.microsoft.properties.
     *
     * @param text The text to translate.
     * @param sourceLanguage The input text language (for example, "en").
     * @param targetLanguage The desired language to translate to (for example, "fr").
     * @return The translated text. If translation is unavailable, returns the unchanged text.
     * @throws Exception
     * @see org.apache.tika.language.translate.Translator
     * @since Tika 1.6
     */
    public String translate(String text, String sourceLanguage, String targetLanguage) throws TikaException, IOException {
        if (!available) return text;
        Language source = Language.fromString(sourceLanguage);
        Language target = Language.fromString(targetLanguage);
        Translate.setClientId(clientId);
        Translate.setClientSecret(clientSecret);
        try {
            return Translate.execute(text, source, target);
        } catch (Exception e) {
            throw new TikaException("Error with Microsoft Translation: " + e.getMessage());
        }
    }

    /**
     * Use the Microsoft service to translate the given text to the given target language. The source language
     * is automatically detected by Microsoft. You must set the client keys in translator.microsoft.properties.
     * @param text The text to translate.
     * @param targetLanguage The desired language to translate to (for example, "hi").
     * @return The translated text. If translation is unavailable, returns the unchanged text.
     * @throws Exception
     * @see org.apache.tika.language.translate.Translator
     * @since Tika 1.6
     */
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        if (!available) return text;
        Language target = Language.fromString(targetLanguage);
        Translate.setClientId(clientId);
        Translate.setClientSecret(clientSecret);
        try {
            return Translate.execute(text, target);
        } catch (Exception e) {
            throw new TikaException("Error with Microsoft Translation: " + e.getMessage());
        }
    }

    /**
     * Check whether this instance has a working property file and its keys are not the defaults.
     * This is not guaranteed to work, since keys may be incorrect or the webservice may be down.
     * @return whether translation will probably work.
     */
    public boolean isAvailable(){
        return available;
    }
    
    /**
     * Sets the client Id for the translator API.
     * @param id The ID to set.
     */
    public void setId(String id){
    	this.clientId = id;
        this.available = checkAvailable();   
    }
    
    /**
     * Sets the client secret for the translator API.
     * @param secret The secret to set.
     */
    public void setSecret(String secret){
    	this.clientSecret = secret;
        this.available = checkAvailable();   	
    }
    
    private boolean checkAvailable(){
       return clientId != null && 
    		   !clientId.equals(DEFAULT_ID) && 
    		   clientSecret != null && 
    		   !clientSecret.equals(DEFAULT_SECRET);
    }
}
