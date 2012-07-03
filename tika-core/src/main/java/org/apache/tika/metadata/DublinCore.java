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
package org.apache.tika.metadata;

/**
 * A collection of Dublin Core metadata names.
 *
 * @see <a href="http://dublincore.org">dublincore.org</a>
 */
public interface DublinCore {

    public static final String NAMESPACE_URI_DC = "http://purl.org/dc/elements/1.1/";
    public static final String NAMESPACE_URI_DC_TERMS = "http://purl.org/dc/terms/";
    public static final String PREFIX_DC = "dc";
    public static final String PREFIX_DC_TERMS = "dcterms";

    /**
     * Typically, Format may include the media-type or dimensions of the
     * resource. Format may be used to determine the software, hardware or
     * other equipment needed to display or operate the resource. Examples
     * of dimensions include size and duration. Recommended best practice is
     * to select a value from a controlled vocabulary (for example, the list
     * of Internet Media Types [MIME] defining computer media formats).
     */
	Property FORMAT = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "format");

    /**
     * Recommended best practice is to identify the resource by means of
     * a string or number conforming to a formal identification system.
     * Example formal identification systems include the Uniform Resource
     * Identifier (URI) (including the Uniform Resource Locator (URL)),
     * the Digital Object Identifier (DOI) and the International Standard
     * Book Number (ISBN).
     */
	Property IDENTIFIER = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "identifier");

    /**
     * Date on which the resource was changed.
     */
	Property MODIFIED = Property.internalDate(
		PREFIX_DC_TERMS + Metadata.NAMESPACE_PREFIX_DELIMITER + "modified");

    /**
     * An entity responsible for making contributions to the content of the
     * resource. Examples of a Contributor include a person, an organisation,
     * or a service. Typically, the name of a Contributor should be used to
     * indicate the entity.
     */
	Property CONTRIBUTOR = Property.internalTextBag(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "contributor");

    /**
     * The extent or scope of the content of the resource. Coverage will
     * typically include spatial location (a place name or geographic
     * coordinates), temporal period (a period label, date, or date range)
     * or jurisdiction (such as a named administrative entity). Recommended
     * best practice is to select a value from a controlled vocabulary (for
     * example, the Thesaurus of Geographic Names [TGN]) and that, where
     * appropriate, named places or time periods be used in preference to
     * numeric identifiers such as sets of coordinates or date ranges.
     */
	Property COVERAGE = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "coverage");

    /**
     * An entity primarily responsible for making the content of the resource.
     * Examples of a Creator include a person, an organisation, or a service.
     * Typically, the name of a Creator should be used to indicate the entity.
     */
	Property CREATOR = Property.internalTextBag(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "creator");

    /**
     * Date of creation of the resource.
     */
        Property CREATED = Property.internalDate(
                PREFIX_DC_TERMS + Metadata.NAMESPACE_PREFIX_DELIMITER + "created");

    /**
     * A date associated with an event in the life cycle of the resource.
     * Typically, Date will be associated with the creation or availability of
     * the resource. Recommended best practice for encoding the date value is
     * defined in a profile of ISO 8601 [W3CDTF] and follows the YYYY-MM-DD
     * format.
     */
	Property DATE = Property.internalDate(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "date");

    /**
     * An account of the content of the resource. Description may include
     * but is not limited to: an abstract, table of contents, reference to
     * a graphical representation of content or a free-text account of
     * the content.
     */
	Property DESCRIPTION = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "description");

    /**
     * A language of the intellectual content of the resource. Recommended
     * best practice is to use RFC 3066 [RFC3066], which, in conjunction
     * with ISO 639 [ISO639], defines two- and three-letter primary language
     * tags with optional subtags. Examples include "en" or "eng" for English,
     * "akk" for Akkadian, and "en-GB" for English used in the United Kingdom.
     */
	Property LANGUAGE = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "language");

    /**
     * An entity responsible for making the resource available. Examples of
     * a Publisher include a person, an organisation, or a service. Typically,
     * the name of a Publisher should be used to indicate the entity.
     */
	Property PUBLISHER = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "publisher");

    /**
     * A reference to a related resource. Recommended best practice is to
     * reference the resource by means of a string or number conforming to
     * a formal identification system.
     */
	Property RELATION = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "relation");

    /**
     * Information about rights held in and over the resource. Typically,
     * a Rights element will contain a rights management statement for
     * the resource, or reference a service providing such information.
     * Rights information often encompasses Intellectual Property Rights
     * (IPR), Copyright, and various Property Rights. If the Rights element
     * is absent, no assumptions can be made about the status of these and
     * other rights with respect to the resource.
     */
	Property RIGHTS = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "rights");

    /**
     * A reference to a resource from which the present resource is derived.
     * The present resource may be derived from the Source resource in whole
     * or in part. Recommended best practice is to reference the resource by
     * means of a string or number conforming to a formal identification
     * system.
     */
	Property SOURCE = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "source");

    /**
     * The topic of the content of the resource. Typically, a Subject will
     * be expressed as keywords, key phrases or classification codes that
     * describe a topic of the resource. Recommended best practice is to
     * select a value from a controlled vocabulary or formal classification
     * scheme.
     */
	Property SUBJECT = Property.internalTextBag(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "subject");

    /**
     * A name given to the resource. Typically, a Title will be a name by
     * which the resource is formally known.
     */
	Property TITLE = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "title");

    /**
     * The nature or genre of the content of the resource. Type includes terms
     * describing general categories, functions, genres, or aggregation levels
     * for content. Recommended best practice is to select a value from a
     * controlled vocabulary (for example, the DCMI Type Vocabulary
     * [DCMITYPE]). To describe the physical or digital manifestation of
     * the resource, use the Format element.
     */
	Property TYPE = Property.internalText(
    		PREFIX_DC + Metadata.NAMESPACE_PREFIX_DELIMITER + "type");

}
