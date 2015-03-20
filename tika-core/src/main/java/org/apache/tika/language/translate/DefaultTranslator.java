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

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultTranslator implements Translator{
    private transient final ServiceLoader loader;

    public DefaultTranslator(ServiceLoader loader) {
        this.loader = loader;
    }

    /**
     * Finds all statically loadable translators and sort the list by name,
     * rather than discovery order.
     *
     * @param loader service loader
     * @return ordered list of statically loadable parsers
     */
    private static List<Translator> getDefaultTranslators(ServiceLoader loader) {
        List<Translator> translators = loader.loadStaticServiceProviders(Translator.class);
        Collections.sort(translators, new Comparator<Translator>() {
            public int compare(Translator t1, Translator t2) {
                String n1 = t1.getClass().getName();
                String n2 = t2.getClass().getName();
                boolean tika1 = n1.startsWith("org.apache.tika.");
                boolean tika2 = n2.startsWith("org.apache.tika.");
                if (tika1 == tika2) {
                    return n1.compareTo(n2);
                } else if (tika1) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
        return translators;
    }

    public String translate(String text, String sourceLanguage, String targetLanguage) throws TikaException, IOException {
        return getDefaultTranslators(loader).get(0).translate(text, sourceLanguage, targetLanguage);
    }

    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        return getDefaultTranslators(loader).get(0).translate(text, targetLanguage);
    }

    public boolean isAvailable() {
        return getDefaultTranslators(loader).get(0).isAvailable();
    }

}
