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

package org.apache.tika.example;

import org.apache.tika.language.translate.MicrosoftTranslator;

public class TranslatorExample {
    public String microsoftTranslateToFrench(String text) {
        MicrosoftTranslator translator = new MicrosoftTranslator();
        // Change the id and secret! See http://msdn.microsoft.com/en-us/library/hh454950.aspx.
        translator.setId("dummy-id");
        translator.setSecret("dummy-secret");
        try {
            return translator.translate(text, "fr");
        } catch (Exception e) {
            return "Error while translating.";
        }
    }
}
