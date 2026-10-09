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
package org.apache.tika.parser.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.parser.ParseContext;

/**
 * {@code imageGraphicsEngineFactoryClass} instantiates the class it names, so it is operator
 * configuration: a per-request {@code pdf-parser} block must not set it, an operator-authored
 * one still may.
 */
public class PDFParserRuntimeConfigTest extends TikaTest {

    private static final String PDF = "testPDF.pdf";

    /** Stands in for any class on the parse classpath; records that its constructor ran. */
    public static class ConstructorWitness {

        static volatile boolean constructed;

        public ConstructorWitness() {
            constructed = true;
        }
    }

    @Test
    public void testPerRequestConfigCannotNameEngineFactory() {
        ConstructorWitness.constructed = false;
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"imageGraphicsEngineFactoryClass\": \""
                + ConstructorWitness.class.getName() + "\"}");
        Exception e = assertThrows(Exception.class, () -> getXML(PDF, new PDFParser(), context));
        assertFalse(ConstructorWitness.constructed,
                "a class named by a per-request config was instantiated");
        assertTrue(rootMessage(e).contains("imageGraphicsEngineFactory"), rootMessage(e));
    }

    @Test
    public void testPerRequestConfigStillMerges() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"sortByPosition\": true}");
        getXML(PDF, new PDFParser(), context);
        assertTrue(context.get(PDFParserConfig.class).isSortByPosition());
    }

    @Test
    public void testOperatorConfigSetsEngineFactory() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"imageGraphicsEngineFactoryClass\": \""
                + MyCustomImageGraphicsEngineFactory.class.getName() + "\"}", true);
        getXML(PDF, new PDFParser(), context);
        assertEquals(MyCustomImageGraphicsEngineFactory.class,
                context.get(PDFParserConfig.class).getImageGraphicsEngineFactory().getClass());
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
