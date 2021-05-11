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

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Properties;

/**
 * Configuration for {@link CTAKESContentHandler}.
 * <p>
 * This class allows to enable cTAKES and set its parameters.
 */
public class CTAKESConfig implements Serializable {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -1599741171775528923L;

    // Path to XML descriptor for AnalysisEngine
    private String aeDescriptorPath =
            "/ctakes-core/desc/analysis_engine/SentencesAndTokensAggregate.xml";

    // UMLS username
    private String UMLSUser = "";

    // UMLS password
    private String UMLSPass = "";

    // Enables formatted output
    private boolean prettyPrint = true;

    // Type of cTAKES (UIMA) serializer
    private CTAKESSerializer serializerType = CTAKESSerializer.XMI;

    // OutputStream object used for CAS serialization
    private OutputStream stream = NULL_OUTPUT_STREAM;

    // Enables CAS serialization
    private boolean serialize = false;

    // Enables text analysis using cTAKES
    private boolean text = true;

    // List of metadata to analyze using cTAKES
    private String[] metadata = null;

    // List of annotation properties to add to metadata in addition to text covered by an annotation
    private CTAKESAnnotationProperty[] annotationProps = null;

    // Character used to separate the annotation properties into metadata
    private char separatorChar = ':';

    /**
     * Default constructor.
     */
    public CTAKESConfig() {
        init(this.getClass().getResourceAsStream("CTAKESConfig.properties"));
    }

    /**
     * Loads properties from InputStream and then tries to close InputStream.
     *
     * @param stream {@link InputStream} object used to read properties.
     */
    public CTAKESConfig(InputStream stream) {
        init(stream);
    }

    private void init(InputStream stream) {
        if (stream == null) {
            return;
        }
        Properties props = new Properties();

        try {
            props.load(stream);
        } catch (IOException e) {
            // TODO warning
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ioe) {
                    // TODO warning
                }
            }
        }

        setAeDescriptorPath(props.getProperty("aeDescriptorPath", getAeDescriptorPath()));
        setUMLSUser(props.getProperty("UMLSUser", getUMLSUser()));
        setUMLSPass(props.getProperty("UMLSPass", getUMLSPass()));
        setText(Boolean.parseBoolean(props.getProperty("text", Boolean.toString(isText()))));
        setMetadata(props.getProperty("metadata", getMetadataAsString()).split(","));
        setAnnotationProps(
                props.getProperty("annotationProps", getAnnotationPropsAsString()).split(","));
        setSeparatorChar(props.getProperty("separatorChar", Character.toString(getSeparatorChar()))
                .charAt(0));
    }

    /**
     * Returns the path to XML descriptor for AnalysisEngine.
     *
     * @return the path to XML descriptor for AnalysisEngine.
     */
    public String getAeDescriptorPath() {
        return aeDescriptorPath;
    }

    /**
     * Sets the path to XML descriptor for AnalysisEngine.
     *
     * @param aeDescriptorPath the path to XML descriptor for AnalysisEngine.
     */
    public void setAeDescriptorPath(String aeDescriptorPath) {
        this.aeDescriptorPath = aeDescriptorPath;
    }

    /**
     * Returns the UMLS username.
     *
     * @return the UMLS username.
     */
    public String getUMLSUser() {
        return UMLSUser;
    }

    /**
     * Sets the UMLS username.
     *
     * @param uMLSUser the UMLS username.
     */
    public void setUMLSUser(String uMLSUser) {
        this.UMLSUser = uMLSUser;
    }

    /**
     * Returns the UMLS password.
     *
     * @return the UMLS password.
     */
    public String getUMLSPass() {
        return UMLSPass;
    }

    /**
     * Sets the UMLS password.
     *
     * @param uMLSPass the UMLS password.
     */
    public void setUMLSPass(String uMLSPass) {
        this.UMLSPass = uMLSPass;
    }

    /**
     * Returns {@code true} if formatted output is enabled, {@code false} otherwise.
     *
     * @return {@code true} if formatted output is enabled, {@code false} otherwise.
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Enables the formatted output for serializer.
     *
     * @param prettyPrint {@code true} to enable formatted output, {@code false} otherwise.
     */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Returns the type of cTAKES (UIMA) serializer used to write the CAS.
     *
     * @return the type of cTAKES serializer.
     */
    public CTAKESSerializer getSerializerType() {
        return serializerType;
    }

    /**
     * Sets the type of cTAKES (UIMA) serializer used to write CAS.
     *
     * @param serializerType the type of cTAKES serializer.
     */
    public void setSerializerType(CTAKESSerializer serializerType) {
        this.serializerType = serializerType;
    }

    /**
     * Returns an {@link OutputStream} object used write the CAS.
     *
     * @return {@link OutputStream} object used write the CAS.
     */
    public OutputStream getOutputStream() {
        return stream;
    }

    /**
     * Sets the {@link OutputStream} object used to write the CAS.
     *
     * @param stream the {@link OutputStream} object used to write the CAS.
     */
    public void setOutputStream(OutputStream stream) {
        this.stream = stream;
    }

    /**
     * Returns {@code true} if CAS serialization is enabled, {@code false} otherwise.
     *
     * @return {@code true} if CAS serialization output is enabled, {@code false} otherwise.
     */
    public boolean isSerialize() {
        return serialize;
    }

    /**
     * Enables CAS serialization.
     *
     * @param serialize {@code true} to enable CAS serialization, {@code false} otherwise.
     */
    public void setSerialize(boolean serialize) {
        this.serialize = serialize;
    }

    /**
     * Returns {@code true} if content text analysis is enabled {@code false} otherwise.
     *
     * @return {@code true} if content text analysis is enabled {@code false} otherwise.
     */
    public boolean isText() {
        return text;
    }

    /**
     * Enables content text analysis using cTAKES.
     *
     * @param text {@code true} to enable content text analysis, {@code false} otherwise.
     */
    public void setText(boolean text) {
        this.text = text;
    }

    /**
     * Returns an array of metadata whose values will be analyzed using cTAKES.
     *
     * @return an array of metadata whose values will be analyzed using cTAKES.
     */
    public String[] getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata whose values will be analyzed using cTAKES.
     *
     * @param metadata the metadata whose values will be analyzed using cTAKES.
     */
    public void setMetadata(String[] metadata) {
        this.metadata = metadata;
    }

    /**
     * Returns a string containing a comma-separated list of metadata whose
     * values will be analyzed using cTAKES.
     *
     * @return a string containing a comma-separated list of metadata whose
     * values will be analyzed using cTAKES.
     */
    public String getMetadataAsString() {
        if (metadata == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < metadata.length; i++) {
            sb.append(metadata[i]);
            if (i < metadata.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * Returns an array of {@link CTAKESAnnotationProperty}'s that will be
     * included into cTAKES metadata.
     *
     * @return an array of {@link CTAKESAnnotationProperty}'s that will be
     * included into cTAKES metadata.
     */
    public CTAKESAnnotationProperty[] getAnnotationProps() {
        return annotationProps;
    }

    /**
     * Sets the {@link CTAKESAnnotationProperty}'s that will be included into cTAKES metadata.
     *
     * @param annotationProps the {@link CTAKESAnnotationProperty}'s that will
     *                        be included into cTAKES metadata.
     */
    public void setAnnotationProps(CTAKESAnnotationProperty[] annotationProps) {
        this.annotationProps = annotationProps;
    }

    /**
     * ets the {@link CTAKESAnnotationProperty}'s that will be included into cTAKES metadata.
     *
     * @param annotationProps the {@link CTAKESAnnotationProperty}'s that will be
     *                       included into cTAKES metadata.
     */
    public void setAnnotationProps(String[] annotationProps) {
        CTAKESAnnotationProperty[] properties =
                new CTAKESAnnotationProperty[annotationProps.length];
        for (int i = 0; i < annotationProps.length; i++) {
            properties[i] = CTAKESAnnotationProperty.valueOf(annotationProps[i]);
        }
        setAnnotationProps(properties);
    }

    /**
     * Returns a string containing a comma-separated list of {@link CTAKESAnnotationProperty}
     * names that will be included into cTAKES metadata.
     *
     * @return
     */
    public String getAnnotationPropsAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("coveredText");
        if (annotationProps != null) {
            for (CTAKESAnnotationProperty property : annotationProps) {
                sb.append(separatorChar);
                sb.append(property.getName());
            }
        }
        return sb.toString();
    }

    /**
     * Returns the separator character used for annotation properties.
     *
     * @return the separator character used for annotation properties.
     */
    public char getSeparatorChar() {
        return separatorChar;
    }

    /**
     * Sets the separator character used for annotation properties.
     *
     * @param separatorChar the separator character used for annotation properties.
     */
    public void setSeparatorChar(char separatorChar) {
        this.separatorChar = separatorChar;
    }
}
