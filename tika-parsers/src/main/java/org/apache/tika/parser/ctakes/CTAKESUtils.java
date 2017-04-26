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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;

import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.impl.XCASSerializer;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.cas.impl.XmiSerializationSharedData;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XmlCasSerializer;
import org.xml.sax.SAXException;

/**
 * This class provides methods to extract biomedical information from plain text
 * using {@see CTAKESContentHandler} that relies on Apache cTAKES.
 * 
 * <p>
 * Apache cTAKES is built on top of <a href="https://uima.apache.org/">Apache
 * UIMA</a> framework and <a href="https://opennlp.apache.org/">OpenNLP</a>
 * toolkit.
 * </p>
 */
public class CTAKESUtils {
	// UMLS username property
	private final static String CTAKES_UMLS_USER = "ctakes.umlsuser";

	// UMLS password property
	private final static String CTAKES_UMLS_PASS = "ctakes.umlspw";

	/**
	 * Returns a new UIMA Analysis Engine (AE). This method ensures that only
	 * one instance of an AE is created.
	 * 
	 * <p>
	 * An Analysis Engine is a component responsible for analyzing unstructured
	 * information, discovering and representing semantic content. Unstructured
	 * information includes, but is not restricted to, text documents.
	 * </p>
	 * 
	 * @param aeDescriptor
	 *            pathname for XML file including an AnalysisEngineDescription
	 *            that contains all of the information needed to instantiate and
	 *            use an AnalysisEngine.
	 * @param umlsUser
	 *            UMLS username for NLM database
	 * @param umlsPass
	 *            UMLS password for NLM database
	 * @return an Analysis Engine for analyzing unstructured information.
	 * @throws IOException
	 *             if any I/O error occurs.
	 * @throws InvalidXMLException
	 *             if the input XML is not valid or does not specify a valid
	 *             ResourceSpecifier.
	 * @throws ResourceInitializationException
	 *             if a failure occurred during production of the resource.
	 * @throws URISyntaxException
	 *             if URL of the resource is not formatted strictly according to
	 *             to RFC2396 and cannot be converted to a URI.
	 */
	public static AnalysisEngine getAnalysisEngine(String aeDescriptor,
			String umlsUser, String umlsPass) throws IOException,
			InvalidXMLException, ResourceInitializationException,
			URISyntaxException {
		// UMLS user ID and password.
		String aeDescriptorPath = CTAKESUtils.class.getResource(aeDescriptor)
				.toURI().getPath();

		// get Resource Specifier from XML
		XMLInputSource aeIputSource = new XMLInputSource(aeDescriptorPath);
		ResourceSpecifier aeSpecifier = UIMAFramework.getXMLParser()
				.parseResourceSpecifier(aeIputSource);

		// UMLS user ID and password
		if ((umlsUser != null) && (!umlsUser.isEmpty()) && (umlsPass != null)
				&& (!umlsPass.isEmpty())) {
			/*
			 * It is highly recommended that you change UMLS credentials in the
			 * XML configuration file instead of giving user and password using
			 * CTAKESConfig.
			 */
			System.setProperty(CTAKES_UMLS_USER, umlsUser);
			System.setProperty(CTAKES_UMLS_PASS, umlsPass);
		}

		// create AE
		AnalysisEngine ae = UIMAFramework.produceAnalysisEngine(aeSpecifier);

		return ae;
	}

	/**
	 * Returns a new JCas () appropriate for the given Analysis Engine. This
	 * method ensures that only one instance of a JCas is created. A Jcas is a
	 * Java Cover Classes based Object-oriented CAS (Common Analysis System)
	 * API.
	 * 
	 * <p>
	 * Important: It is highly recommended that you reuse CAS objects rather
	 * than creating new CAS objects prior to each analysis. This is because CAS
	 * objects may be expensive to create and may consume a significant amount
	 * of memory.
	 * </p>
	 * 
	 * @param ae
	 *            AnalysisEngine used to create an appropriate JCas object.
	 * @return a JCas object appropriate for the given AnalysisEngine.
	 * @throws ResourceInitializationException
	 *             if a CAS could not be created because this AnalysisEngine's
	 *             CAS metadata (type system, type priorities, or FS indexes)
	 *             are invalid.
	 */
	public static JCas getJCas(AnalysisEngine ae)
			throws ResourceInitializationException {
		JCas jcas = ae.newJCas();
		
		return jcas;
	}

	/**
	 * Serializes a CAS in the given format.
	 * 
	 * @param jcas
	 *            CAS (Common Analysis System) to be serialized.
	 * @param type
	 *            type of cTAKES (UIMA) serializer used to write CAS.
	 * @param prettyPrint
	 *            {@code true} to do pretty printing of output.
	 * @param stream
	 *            {@see OutputStream} object used to print out information
	 *            extracted by using cTAKES.
	 * @throws SAXException
	 *             if there was a SAX exception.
	 * @throws IOException
	 *             if any I/O error occurs.
	 */
	public static void serialize(JCas jcas, CTAKESSerializer type, boolean prettyPrint,
			OutputStream stream) throws SAXException, IOException {
		if (type == CTAKESSerializer.XCAS) {
			XCASSerializer.serialize(jcas.getCas(), stream, prettyPrint);
		} else if (type == CTAKESSerializer.XMI) {
			XmiCasSerializer.serialize(jcas.getCas(), jcas.getTypeSystem(),
					stream, prettyPrint, new XmiSerializationSharedData());
		} else {
			XmlCasSerializer.serialize(jcas.getCas(), jcas.getTypeSystem(),
					stream);
		}
	}

	/**
	 * Returns the annotation value based on the given annotation type.
	 * 
	 * @param annotation
	 *            {@see IdentifiedAnnotation} object.
	 * @param property
	 *            {@see CTAKESAnnotationProperty} enum used to identify the
	 *            annotation type.
	 * @return the annotation value.
	 */
	public static String getAnnotationProperty(IdentifiedAnnotation annotation,
			CTAKESAnnotationProperty property) {
		String value = null;
		if (property == CTAKESAnnotationProperty.BEGIN) {
			value = Integer.toString(annotation.getBegin());
		} else if (property == CTAKESAnnotationProperty.END) {
			value = Integer.toString(annotation.getEnd());
		} else if (property == CTAKESAnnotationProperty.CONDITIONAL) {
			value = Boolean.toString(annotation.getConditional());
		} else if (property == CTAKESAnnotationProperty.CONFIDENCE) {
			value = Float.toString(annotation.getConfidence());
		} else if (property == CTAKESAnnotationProperty.DISCOVERY_TECNIQUE) {
			value = Integer.toString(annotation.getDiscoveryTechnique());
		} else if (property == CTAKESAnnotationProperty.GENERIC) {
			value = Boolean.toString(annotation.getGeneric());
		} else if (property == CTAKESAnnotationProperty.HISTORY_OF) {
			value = Integer.toString(annotation.getHistoryOf());
		} else if (property == CTAKESAnnotationProperty.ID) {
			value = Integer.toString(annotation.getId());
		} else if (property == CTAKESAnnotationProperty.ONTOLOGY_CONCEPT_ARR) {
			FSArray mentions = annotation.getOntologyConceptArr();
			StringBuilder sb = new StringBuilder();
			if (mentions != null) {
				for (int i = 0; i < mentions.size(); i++) {
					if (mentions.get(i) instanceof UmlsConcept) {
						UmlsConcept concept = (UmlsConcept) mentions.get(i);
						sb.append("cui=").append(concept.getCui()).append(",").
							append(concept.getCodingScheme()).append("=").
							append(concept.getCode());
						if (i < mentions.size() - 1) {
							sb.append(",");
						}
					}
				}
			}
			value = sb.toString();
		} else if (property == CTAKESAnnotationProperty.POLARITY) {
			String polarity_pref = "POLARITY";
			value = new StringBuilder(polarity_pref).append("=").
					append(Integer.toString(annotation.getPolarity())).toString();
		}
		return value;
	}

	/**
	 * Resets cTAKES objects, if created. This method ensures that new cTAKES
	 * objects (a.k.a., Analysis Engine and JCas) will be created if getters of
	 * this class are called.
	 * 
	 * @param ae UIMA Analysis Engine
	 * @param jcas JCas object
	 */
	public static void reset(AnalysisEngine ae, JCas jcas) {
		// Analysis Engine
		resetAE(ae);

		// JCas
		resetCAS(jcas);
		jcas = null;
	}

	/**
	 * Resets the CAS (Common Analysis System), emptying it of all content.
	 * 
	 * @param jcas JCas object
	 */
	public static void resetCAS(JCas jcas) {
		if (jcas != null) {
			jcas.reset();
		}
	}

	/**
	 * Resets the AE (AnalysisEngine), releasing all resources held by the
	 * current AE.
	 * 
	 * @param ae UIMA Analysis Engine
	 */
	public static void resetAE(AnalysisEngine ae) {
		if (ae != null) {
			ae.destroy();
			ae = null;
		}
	}
}
