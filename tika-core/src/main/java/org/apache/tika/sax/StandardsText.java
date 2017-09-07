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

package org.apache.tika.sax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.sax.StandardReference.StandardReferenceBuilder;

/**
 * StandardText relies on regular expressions to extract standard references
 * from text.
 * 
 * <p>
 * This class helps to find the standard references from text by performing the
 * following steps:
 * <ol>
 * <li>searches for headers;</li>
 * <li>searches for patterns that are supposed to be standard references
 * (basically, every string mostly composed of uppercase letters followed by an
 * alphanumeric characters);</li>
 * <li>each potential standard reference starts with score equal to 0.25;</li>
 * <li>increases by 0.25 the score of references which include the name of a
 * known standard organization ({@link StandardOrganizations});</li>
 * <li>increases by 0.25 the score of references which include the word 
 * Publication or Standard;</li>
 * <li>increases by 0.25 the score of references which have been found within
 * "Applicable Documents" and equivalent sections;</li>
 * <li>returns the standard references along with scores.</li>
 * </ol>
 * </p>
 *
 */
public class StandardsText {
	// Regular expression to match uppercase headers
	private static final String REGEX_HEADER = "(\\d+\\.(\\d+\\.?)*)\\p{Blank}+([A-Z]+(\\s[A-Z]+)*){5,}";

	// Regular expression to match the "APPLICABLE DOCUMENTS" and equivalent
	// sections
	private static final String REGEX_APPLICABLE_DOCUMENTS = "(?i:.*APPLICABLE\\sDOCUMENTS|REFERENCE|STANDARD|REQUIREMENT|GUIDELINE|COMPLIANCE.*)";

	// Regular expression to match the alphanumeric identifier of the standard
	private static final String REGEX_IDENTIFIER = "(?<identifier>([0-9]{3,}|([A-Z]+(-|_|\\.)?[0-9]{2,}))((-|_|\\.)?[A-Z0-9]+)*)";

	// Regular expression to match the standard organization
	private static final String REGEX_ORGANIZATION = StandardOrganizations.getOrganzationsRegex();

	// Regular expression to match the type of publication, often reported
	// between the name of the standard organization and the standard identifier
	private static final String REGEX_STANDARD_TYPE = "(\\s(?i:Publication|Standard))";

	// Regular expression to match a string that is supposed to be a standard
	// reference
	private static final String REGEX_FALLBACK = "\\(?" + "(?<mainOrganization>[A-Z]\\w+)"
			+ "\\)?((\\s?(?<separator>\\/)\\s?)(\\w+\\s)*\\(?" + "(?<secondOrganization>[A-Z]\\w+)" + "\\)?)?"
			+ REGEX_STANDARD_TYPE + "?" + "(-|\\s)?" + REGEX_IDENTIFIER;

	// Regular expression to match the standard organization within a string
	// that is supposed to be a standard reference
	private static final String REGEX_STANDARD = ".*" + REGEX_ORGANIZATION + ".+" + REGEX_ORGANIZATION + "?.*";

	/**
	 * Extracts the standard references found within the given text.
	 * 
	 * @param text
	 *            the text from which the standard references are extracted.
	 * @param threshold
	 *            the lower bound limit to be used in order to select only the
	 *            standard references with score greater than or equal to the
	 *            threshold. For instance, using a threshold of 0.75 means that
	 *            only the patterns with score greater than or equal to 0.75
	 *            will be returned.
	 * @return the list of standard references extracted from the given text.
	 */
	public static ArrayList<StandardReference> extractStandardReferences(String text, double threshold) {
		Map<Integer, String> headers = findHeaders(text);

		ArrayList<StandardReference> standardReferences = findStandards(text, headers, threshold);

		return standardReferences;
	}

	/**
	 * This method helps to find the headers within the given text.
	 * 
	 * @param text
	 *            the text from which the headers are extracted.
	 * @return the list of headers found within the given text.
	 */
	private static Map<Integer, String> findHeaders(String text) {
		Map<Integer, String> headers = new TreeMap<Integer, String>();

		Pattern pattern = Pattern.compile(REGEX_HEADER);
		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			headers.put(matcher.start(), matcher.group());
		}

		return headers;
	}

	/**
	 * This method helps to find the standard references within the given text.
	 * 
	 * @param text
	 *            the text from which the standards references are extracted.
	 * @param headers
	 *            the list of headers found within the given text.
	 * @param threshold
	 *            the lower bound limit to be used in order to select only the
	 *            standard references with score greater than or equal to the
	 *            threshold.
	 * @return the list of standard references extracted from the given text.
	 */
	private static ArrayList<StandardReference> findStandards(String text, Map<Integer, String> headers,
			double threshold) {
		ArrayList<StandardReference> standards = new ArrayList<StandardReference>();
		double score = 0;

		Pattern pattern = Pattern.compile(REGEX_FALLBACK);
		Matcher matcher = pattern.matcher(text);

		while (matcher.find()) {
			StandardReferenceBuilder builder = new StandardReference.StandardReferenceBuilder(
					matcher.group("mainOrganization"), matcher.group("identifier"))
							.setSecondOrganization(matcher.group("separator"), matcher.group("secondOrganization"));
			score = 0.25;

			// increases by 0.25 the score of references which include the name of a known standard organization
			if (matcher.group().matches(REGEX_STANDARD)) {
				score += 0.25;
			}
			
			// increases by 0.25 the score of references which include the word "Publication" or "Standard"
			if (matcher.group().matches(".*" + REGEX_STANDARD_TYPE + ".*")) {
				score += 0.25;
			}

			int startHeader = 0;
			int endHeader = 0;
			boolean headerFound = false;
			Iterator<Entry<Integer, String>> iterator = headers.entrySet().iterator();
			while (iterator.hasNext() && !headerFound) {
				startHeader = endHeader;
				endHeader = iterator.next().getKey();
				if (endHeader > matcher.start()) {
					headerFound = true;
				}
			}

			String header = headers.get(startHeader);
			
			// increases by 0.25 the score of references which have been found within "Applicable Documents" and equivalent sections
			if (header != null && headers.get(startHeader).matches(REGEX_APPLICABLE_DOCUMENTS)) {
				score += 0.25;
			}

			builder.setScore(score);
			
			if (score >= threshold) {
				standards.add(builder.build());
			}
		}

		return standards;
	}
}