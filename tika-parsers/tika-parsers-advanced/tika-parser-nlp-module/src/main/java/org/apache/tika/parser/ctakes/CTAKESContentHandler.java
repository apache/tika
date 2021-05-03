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
package org.apache.tika.parser.ctakes;

import java.util.Collection;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ContentHandlerDecorator;

/**
 * Class used to extract biomedical information while parsing.
 *
 * <p>
 * This class relies on <a href="http://ctakes.apache.org/">Apache cTAKES</a>
 * that is a natural language processing system for extraction of information
 * from electronic medical record clinical free-text.
 * </p>
 */
public class CTAKESContentHandler extends ContentHandlerDecorator {
    // Prefix used for metadata including cTAKES annotations
    public static String CTAKES_META_PREFIX = "ctakes:";

    // Configuration object for CTAKESContentHandler
    private CTAKESConfig config = null;

    // StringBuilder object used to build the clinical free-text for cTAKES
    private StringBuilder sb = null;

    // Metadata object used for cTAKES annotations
    private Metadata metadata = null;

    // UIMA Analysis Engine
    private AnalysisEngine ae = null;

    // JCas object for working with the CAS (Common Analysis System)
    private JCas jcas = null;

    /**
     * Creates a new {@link CTAKESContentHandler} for the given {@link ContentHandler}
     * and Metadata objects.
     *
     * @param handler  the {@link ContentHandler} object to be decorated.
     * @param metadata the {@link Metadata} object that will be populated using
     *                 biomedical information extracted by cTAKES.
     * @param config   the {@link CTAKESConfig} object used to configure the handler.
     */
    public CTAKESContentHandler(ContentHandler handler, Metadata metadata, CTAKESConfig config) {
        super(handler);
        this.metadata = metadata;
        this.config = config;
        this.sb = new StringBuilder();
    }

    /**
     * Creates a new {@link CTAKESContentHandler} for the given {@link
     * ContentHandler} and Metadata objects.
     *
     * @param handler  the {@link ContentHandler} object to be decorated.
     * @param metadata the {@link Metadata} object that will be populated using
     *                 biomedical information extracted by cTAKES.
     */
    public CTAKESContentHandler(ContentHandler handler, Metadata metadata) {
        this(handler, metadata, new CTAKESConfig());
    }

    /**
     * Default constructor.
     */
    public CTAKESContentHandler() {
        this(new DefaultHandler(), new Metadata());
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (config.isText()) {
            sb.append(ch, start, length);
        }
        super.characters(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            // create an Analysis Engine
            if (ae == null) {
                ae = CTAKESUtils
                        .getAnalysisEngine(config.getAeDescriptorPath(), config.getUMLSUser(),
                                config.getUMLSPass());
            }

            // create a JCas, given an AE
            if (jcas == null) {
                jcas = CTAKESUtils.getJCas(ae);
            }

            // get metadata to process
            StringBuilder metaText = new StringBuilder();
            String[] metadataToProcess = config.getMetadata();
            if (metadataToProcess != null) {
                for (String name : config.getMetadata()) {
                    for (String value : metadata.getValues(name)) {
                        metaText.append(value);
                        metaText.append(System.lineSeparator());
                    }
                }
            }

            // analyze text
            jcas.setDocumentText(metaText.toString() + sb.toString());
            ae.process(jcas);

            // add annotations to metadata
            metadata.add(CTAKES_META_PREFIX + "schema", config.getAnnotationPropsAsString());
            CTAKESAnnotationProperty[] annotationPros = config.getAnnotationProps();
            Collection<IdentifiedAnnotation> collection =
                    JCasUtil.select(jcas, IdentifiedAnnotation.class);
            for (IdentifiedAnnotation annotation : collection) {
                StringBuilder annotationBuilder = new StringBuilder();
                annotationBuilder.append(annotation.getCoveredText());
                if (annotationPros != null) {
                    for (CTAKESAnnotationProperty property : annotationPros) {
                        annotationBuilder.append(config.getSeparatorChar());
                        annotationBuilder
                                .append(CTAKESUtils.getAnnotationProperty(annotation, property));
                    }
                }
                metadata.add(CTAKES_META_PREFIX + annotation.getType().getShortName(),
                        annotationBuilder.toString());
            }

            if (config.isSerialize()) {
                // serialize data
                CTAKESUtils.serialize(jcas, config.getSerializerType(), config.isPrettyPrint(),
                        config.getOutputStream());
            }
        } catch (Exception e) {
            throw new SAXException(e.getMessage());
        } finally {
            CTAKESUtils.resetCAS(jcas);
        }
    }

    /**
     * Returns metadata that includes cTAKES annotations.
     *
     * @return {@link Metadata} object that includes cTAKES annotations.
     */
    public Metadata getMetadata() {
        return metadata;
    }
}
