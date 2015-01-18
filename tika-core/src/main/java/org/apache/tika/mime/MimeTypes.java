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
package org.apache.tika.mime;

// JDK imports
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.tika.Tika;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.TextDetector;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.metadata.Metadata;

/**
 * This class is a MimeType repository. It gathers a set of MimeTypes and
 * enables to retrieves a content-type from its name, from a file name, or from
 * a magic character sequence.
 * <p>
 * The MIME type detection methods that take an {@link InputStream} as an
 * argument will never reads more than {@link #getMinLength()} bytes from the
 * stream. Also the given stream is never {@link InputStream#close() closed},
 * {@link InputStream#mark(int) marked}, or {@link InputStream#reset() reset} by
 * the methods. Thus a client can use the {@link InputStream#markSupported()
 * mark feature} of the stream (if available) to restore the stream back to the
 * state it was before type detection if it wants to process the stream based on
 * the detected type.
 */
public final class MimeTypes implements Detector, Serializable {

	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = -1350863170146349036L;

	/**
	 * Name of the {@link #rootMimeType root} type, application/octet-stream.
	 */
	public static final String OCTET_STREAM = "application/octet-stream";

	/**
	 * Name of the {@link #textMimeType text} type, text/plain.
	 */
	public static final String PLAIN_TEXT = "text/plain";

	/**
	 * Name of the {@link #xml xml} type, application/xml.
	 */
	public static final String XML = "application/xml";

	/**
	 * Root type, application/octet-stream.
	 */
	private final MimeType rootMimeType;
	private final List<MimeType> rootMimeTypeL;

	/**
	 * Text type, text/plain.
	 */
	private final MimeType textMimeType;

	/*
	 * xml type, application/xml
	 */
	private final MimeType xmlMimeType;

	/**
	 * Registered media types and their aliases.
	 */
	private final MediaTypeRegistry registry = new MediaTypeRegistry();

	/** All the registered MimeTypes indexed on their canonical names */
	private final Map<MediaType, MimeType> types = new HashMap<MediaType, MimeType>();

	/** The patterns matcher */
	private Patterns patterns = new Patterns(registry);

	/** Sorted list of all registered magics */
	private final List<Magic> magics = new ArrayList<Magic>();

	/** Sorted list of all registered rootXML */
	private final List<MimeType> xmls = new ArrayList<MimeType>();

	/** [luke] probability parameters default value */
	private static final float DEFAULT_MAGIC_TRUST = 0.75f;
	private static final float DEFAULT_META_TRUST = 0.70f;
	private static final float DEFAULT_EXTENSION_TRUST = 0.65f;
	private float priorMagicFileType, priorExtensionFileType,
			priorMetaFileType;
	private float magic_trust, extension_trust, meta_trust;
	private float magic_neg, extension_neg, meta_neg;
	/*
	 * any posterior probability lower than the threshold, will be considered as
	 * an oct-stream type, the default value is 0.65 which is the same as the
	 * DEFAULT_EXTENSION_TRUST which is the lowest of probability of trust among
	 * 3 methods; On the other hands, if the threshold > magic_trust, it will
	 * allow at least one of other method to agree on the same type. Be careful
	 * with the choice of threshold which depends on the values of other
	 * probability parameters.
	 */
	private float threshold;

	/*
	 * this change rate is used when there are multiple types predicted by
	 * magic-bytes. the first predicted type has the highest probability, and
	 * the probability for the next type predicted by magic-bytes will decay
	 * with this change rate. The idea is to have the first one to take
	 * precedence among the multiple possible types predicted by MAGIC-bytes.
	 */
	private float changeRate;
	/*
	 * determine whether to apply probability selection machenism the default
	 * will be set to false;
	 */
	private boolean useProbSelection;

	/** [luke] ***********************/

	public MimeTypes() {
		this(false);
	}

	public MimeTypes(final boolean useProbSelection) {
		rootMimeType = new MimeType(MediaType.OCTET_STREAM);
		textMimeType = new MimeType(MediaType.TEXT_PLAIN);
		xmlMimeType = new MimeType(MediaType.APPLICATION_XML);

		rootMimeTypeL = Collections.singletonList(rootMimeType);

		add(rootMimeType);
		add(textMimeType);
		add(xmlMimeType);

		this.initializeProbabilityParameters();
		this.changeRate = 0.1f;
		this.useProbSelection = useProbSelection;
	}

	/**
	 * [luke] initialize the probability parameters with builder.
	 * 
	 * @param builder
	 */
	public MimeTypes(final Builder builder) {
		this(true);
		priorMagicFileType = builder.priorMagicFileType == 0f ? priorMagicFileType
				: builder.priorMagicFileType;
		priorExtensionFileType = builder.priorExtensionFileType == 0f ? priorExtensionFileType
				: builder.priorExtensionFileType;
		priorMetaFileType = builder.priorMetaFileType == 0f ? priorMetaFileType
				: builder.priorMetaFileType;

		magic_trust = builder.magic_trust == 0f ? magic_trust
				: builder.extension_neg;
		extension_trust = builder.extension_trust == 0f ? extension_trust
				: builder.extension_trust;
		meta_trust = builder.meta_trust == 0f ? meta_trust : builder.meta_trust;

		magic_neg = builder.magic_neg == 0f ? magic_neg : builder.magic_neg;
		extension_neg = builder.extension_neg == 0f ? extension_neg
				: builder.extension_neg;
		meta_neg = builder.meta_neg == 0f ? meta_neg : builder.meta_neg;
		threshold = builder.threshold == 0f ? threshold : builder.threshold;

	}

	public synchronized void enableProbabilitySelection(final boolean enable) {
		this.useProbSelection = enable;
	}

	/**
	 * [luke] Initilize probability parameters with default values;
	 */
	private void initializeProbabilityParameters() {

		priorMagicFileType = 1;
		priorExtensionFileType = 1;
		priorMetaFileType = 1;
		magic_trust = DEFAULT_MAGIC_TRUST;
		extension_trust = DEFAULT_EXTENSION_TRUST;
		meta_trust = DEFAULT_META_TRUST;

		magic_neg = 1 - DEFAULT_MAGIC_TRUST;
		extension_neg = 1 - DEFAULT_EXTENSION_TRUST;
		meta_neg = 1 - DEFAULT_META_TRUST;
		threshold = 0.65f;
	}

	/**
	 * build class for probability parameters setting
	 * 
	 * @author luke
	 * 
	 */
	public class Builder {
		/*
		 * the following are the prior probabilities for the file type
		 * identified by each method.
		 */
		private float priorMagicFileType, priorExtensionFileType,
				priorMetaFileType;
		/*
		 * the following are the conditional probability for each method with
		 * positive conditions
		 */
		private float magic_trust, extension_trust, meta_trust;

		/*
		 * the following *_neg are the conditional probabilities with negative
		 * conditions
		 */
		private float magic_neg, extension_neg, meta_neg;

		private float threshold;

		public synchronized Builder priorMagicFileType(final float prior) {
			this.priorMagicFileType = prior;
			return this;
		}

		public synchronized Builder priorExtensionFileType(final float prior) {
			this.priorExtensionFileType = prior;
			return this;
		}

		public synchronized Builder priorMetaFileType(final float prior) {
			this.priorMetaFileType = prior;
			return this;
		}

		public synchronized Builder magic_trust(final float trust) {
			this.magic_trust = trust;
			return this;
		}

		public synchronized Builder extension_trust(final float trust) {
			this.extension_trust = trust;
			return this;
		}

		public synchronized Builder meta_trust(final float trust) {
			this.meta_trust = trust;
			return this;
		}

		public synchronized Builder magic_neg(final float trust) {
			this.magic_neg = trust;
			return this;
		}

		public synchronized Builder extension_neg(final float trust) {
			this.extension_neg = trust;
			return this;
		}

		public synchronized Builder meta_neg(final float trust) {
			this.meta_neg = trust;
			return this;
		}

		public synchronized Builder threshold(final float threshold) {
			this.threshold = threshold;
			return this;
		}

		/**
		 * initialize the MimeTypes without builder
		 * 
		 * @return
		 */
		public MimeTypes build1() {
			return new MimeTypes();
		}

		/**
		 * initialize the MimeTypes with this builder instance
		 * 
		 * @return
		 */
		public MimeTypes build2() {
			return new MimeTypes(this);
		}
	}

	/**
	 * Find the Mime Content Type of a document from its name. Returns
	 * application/octet-stream if no better match is found.
	 * 
	 * @deprecated Use {@link Tika#detect(String)} instead
	 * @param name
	 *            of the document to analyze.
	 * @return the Mime Content Type of the specified document name
	 */
	public MimeType getMimeType(String name) {
		MimeType type = patterns.matches(name);
		if (type != null) {
			return type;
		}
		type = patterns.matches(name.toLowerCase(Locale.ENGLISH));
		if (type != null) {
			return type;
		} else {
			return rootMimeType;
		}
	}

	/**
	 * Find the Mime Content Type of a document stored in the given file.
	 * Returns application/octet-stream if no better match is found.
	 * 
	 * @deprecated Use {@link Tika#detect(File)} instead
	 * @param file
	 *            file to analyze
	 * @return the Mime Content Type of the specified document
	 * @throws MimeTypeException
	 *             if the type can't be detected
	 * @throws IOException
	 *             if the file can't be read
	 */
	public MimeType getMimeType(File file) throws MimeTypeException,
			IOException {
		return forName(new Tika(this).detect(file));
	}

	/**
	 * Returns the MIME type that best matches the given first few bytes of a
	 * document stream. Returns application/octet-stream if no better match is
	 * found.
	 * <p>
	 * If multiple matches are found, the best (highest priority) matching type
	 * is returned. If multiple matches are found with the same priority, then
	 * all of these are returned.
	 * <p>
	 * The given byte array is expected to be at least {@link #getMinLength()}
	 * long, or shorter only if the document stream itself is shorter.
	 * 
	 * @param data
	 *            first few bytes of a document stream
	 * @return matching MIME type
	 */
	private List<MimeType> getMimeType(byte[] data) {
		if (data == null) {
			throw new IllegalArgumentException("Data is missing");
		} else if (data.length == 0) {
			// See https://issues.apache.org/jira/browse/TIKA-483
			return rootMimeTypeL;
		}

		// Then, check for magic bytes
		List<MimeType> result = new ArrayList<MimeType>(1);
		int currentPriority = -1;
		for (Magic magic : magics) {
			if (currentPriority > 0 && currentPriority > magic.getPriority()) {
				break;
			}
			if (magic.eval(data)) {
				result.add(magic.getType());
				currentPriority = magic.getPriority();
			}
		}

		if (!result.isEmpty()) {
			for (int i = 0; i < result.size(); i++) {
				final MimeType matched = result.get(i);

				// When detecting generic XML (or possibly XHTML),
				// extract the root element and match it against known types
				if ("application/xml".equals(matched.getName())
						|| "text/html".equals(matched.getName())) {
					XmlRootExtractor extractor = new XmlRootExtractor();

					QName rootElement = extractor.extractRootElement(data);
					if (rootElement != null) {
						for (MimeType type : xmls) {
							if (type.matchesXML(rootElement.getNamespaceURI(),
									rootElement.getLocalPart())) {
								result.set(i, type);
								break;
							}
						}
					} else if ("application/xml".equals(matched.getName())) {
						// Downgrade from application/xml to text/plain since
						// the document seems not to be well-formed.
						result.set(i, textMimeType);
					}
				}
			}
			return result;
		}

		// Finally, assume plain text if no control bytes are found
		try {
			TextDetector detector = new TextDetector(getMinLength());
			ByteArrayInputStream stream = new ByteArrayInputStream(data);
			MimeType type = forName(detector.detect(stream, new Metadata())
					.toString());
			return Collections.singletonList(type);
		} catch (Exception e) {
			return rootMimeTypeL;
		}
	}

	/**
	 * Reads the first {@link #getMinLength()} bytes from the given stream. If
	 * the stream is shorter, then the entire content of the stream is returned.
	 * <p>
	 * The given stream is never {@link InputStream#close() closed},
	 * {@link InputStream#mark(int) marked}, or {@link InputStream#reset()
	 * reset} by this method.
	 * 
	 * @param stream
	 *            stream to be read
	 * @return first {@link #getMinLength()} (or fewer) bytes of the stream
	 * @throws IOException
	 *             if the stream can not be read
	 */
	private byte[] readMagicHeader(InputStream stream) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("InputStream is missing");
		}

		byte[] bytes = new byte[getMinLength()];
		int totalRead = 0;

		int lastRead = stream.read(bytes);
		while (lastRead != -1) {
			totalRead += lastRead;
			if (totalRead == bytes.length) {
				return bytes;
			}
			lastRead = stream.read(bytes, totalRead, bytes.length - totalRead);
		}

		byte[] shorter = new byte[totalRead];
		System.arraycopy(bytes, 0, shorter, 0, totalRead);
		return shorter;
	}

	/**
	 * Returns the registered media type with the given name (or alias). The
	 * named media type is automatically registered (and returned) if it doesn't
	 * already exist.
	 * 
	 * @param name
	 *            media type name (case-insensitive)
	 * @return the registered media type with the given name or alias
	 * @throws MimeTypeException
	 *             if the given media type name is invalid
	 */
	public MimeType forName(String name) throws MimeTypeException {
		MediaType type = MediaType.parse(name);
		if (type != null) {
			MediaType normalisedType = registry.normalize(type);
			MimeType mime = types.get(normalisedType);

			if (mime == null) {
				synchronized (this) {
					// Double check it didn't already get added while
					// we were waiting for the lock
					mime = types.get(normalisedType);
					if (mime == null) {
						mime = new MimeType(type);
						add(mime);
						types.put(type, mime);
					}
				}
			}
			return mime;
		} else {
			throw new MimeTypeException("Invalid media type name: " + name);
		}
	}

	/**
	 * Returns the registered media type with the given name (or alias).
	 * 
	 * Unlike {@link #forName(String)}, this function will *not* create a new
	 * MimeType and register it
	 * 
	 * @param name
	 *            media type name (case-insensitive)
	 * @return the registered media type with the given name or alias
	 * @throws MimeTypeException
	 *             if the given media type name is invalid
	 */
	public MimeType getRegisteredMimeType(String name) throws MimeTypeException {
		MediaType type = MediaType.parse(name);
		if (type != null) {
			MediaType normalisedType = registry.normalize(type);
			return types.get(normalisedType);
		} else {
			throw new MimeTypeException("Invalid media type name: " + name);
		}
	}

	public synchronized void setSuperType(MimeType type, MediaType parent) {
		registry.addSuperType(type.getType(), parent);
	}

	/**
	 * Adds an alias for the given media type. This method should only be called
	 * from {@link MimeType#addAlias(String)}.
	 * 
	 * @param type
	 *            media type
	 * @param alias
	 *            media type alias (normalized to lower case)
	 */
	synchronized void addAlias(MimeType type, MediaType alias) {
		registry.addAlias(type.getType(), alias);
	}

	/**
	 * Adds a file name pattern for the given media type. Assumes that the
	 * pattern being added is <b>not</b> a JDK standard regular expression.
	 * 
	 * @param type
	 *            media type
	 * @param pattern
	 *            file name pattern
	 * @throws MimeTypeException
	 *             if the pattern conflicts with existing ones
	 */
	public void addPattern(MimeType type, String pattern)
			throws MimeTypeException {
		this.addPattern(type, pattern, false);
	}

	/**
	 * Adds a file name pattern for the given media type. The caller can specify
	 * whether the pattern being added <b>is</b> or <b>is not</b> a JDK standard
	 * regular expression via the <code>isRegex</code> parameter. If the value
	 * is set to true, then a JDK standard regex is assumed, otherwise the
	 * freedesktop glob type is assumed.
	 * 
	 * @param type
	 *            media type
	 * @param pattern
	 *            file name pattern
	 * @param isRegex
	 *            set to true if JDK std regexs are desired, otherwise set to
	 *            false.
	 * @throws MimeTypeException
	 *             if the pattern conflicts with existing ones.
	 * 
	 */
	public void addPattern(MimeType type, String pattern, boolean isRegex)
			throws MimeTypeException {
		patterns.add(pattern, isRegex, type);
	}

	public MediaTypeRegistry getMediaTypeRegistry() {
		return registry;
	}

	/**
	 * Return the minimum length of data to provide to analyzing methods based
	 * on the document's content in order to check all the known MimeTypes.
	 * 
	 * @return the minimum length of data to provide.
	 * @see #getMimeType(byte[])
	 * @see #getMimeType(String, byte[])
	 */
	public int getMinLength() {
		// This needs to be reasonably large to be able to correctly detect
		// things like XML root elements after initial comment and DTDs
		return 64 * 1024;
	}

	/**
	 * Add the specified mime-type in the repository.
	 * 
	 * @param type
	 *            is the mime-type to add.
	 */
	void add(MimeType type) {
		registry.addType(type.getType());
		types.put(type.getType(), type);

		// Update the magics index...
		if (type.hasMagic()) {
			magics.addAll(type.getMagics());
		}

		// Update the xml (xmlRoot) index...
		if (type.hasRootXML()) {
			xmls.add(type);
		}
	}

	/**
	 * Called after all configured types have been loaded. Initializes the
	 * magics and xmls sets.
	 */
	void init() {
		for (MimeType type : types.values()) {
			magics.addAll(type.getMagics());
			if (type.hasRootXML()) {
				xmls.add(type);
			}
		}
		Collections.sort(magics);
		Collections.sort(xmls);
	}

	/**
	 * Automatically detects the MIME type of a document based on magic markers
	 * in the stream prefix and any given metadata hints.
	 * <p>
	 * The given stream is expected to support marks, so that this method can
	 * reset the stream to the position it was in before this method was called.
	 * 
	 * @param input
	 *            document stream, or <code>null</code>
	 * @param metadata
	 *            metadata hints
	 * @return MIME type of the document
	 * @throws IOException
	 *             if the document stream could not be read
	 */
	public MediaType detect(InputStream input, Metadata metadata)
			throws IOException {
		List<MimeType> possibleTypes = null;

		// Get type based on magic prefix
		if (input != null) {
			input.mark(getMinLength());
			try {
				byte[] prefix = readMagicHeader(input);
				possibleTypes = getMimeType(prefix);
			} finally {
				input.reset();
			}
		}

		MimeType extHint = null;// [luke]
		// Get type based on resourceName hint (if available)
		String resourceName = metadata.get(Metadata.RESOURCE_NAME_KEY);
		if (resourceName != null) {
			String extensionName = null;
			// Deal with a URI or a path name in as the resource name
			try {
				URI uri = new URI(resourceName);
				String path = uri.getPath();
				if (path != null) {
					int slash = path.lastIndexOf('/');
					if (slash + 1 < path.length()) {
						extensionName = path.substring(slash + 1);
					}
				}
			} catch (URISyntaxException e) {
				extensionName = resourceName;
			}
			extHint = getMimeType(extensionName);
		}

		// Get type based on metadata hint (if available)
		String typeName = metadata.get(Metadata.CONTENT_TYPE);
		MimeType metaHint = null;
		if (typeName != null) {
			try {
				metaHint = forName(typeName);
			} catch (MimeTypeException e) {
				// Malformed type name, ignore
			}
		}

		if (!useProbSelection) {
			/*
			 * here it goes with original selection process.
			 */
			if (extHint != null) {
				// If we have some types based on mime magic, try to specialise
				// and/or select the type based on that
				// Otherwise, use the type identified from the name
				possibleTypes = applyHint(possibleTypes, extHint);
			}

			// Get type based on metadata hint (if available)
			if (metaHint != null) {
				possibleTypes = applyHint(possibleTypes, metaHint);
			}

			if (possibleTypes == null || possibleTypes.isEmpty()) {
				// Report that we don't know what it is
				return MediaType.OCTET_STREAM;
			} else {
				return possibleTypes.get(0).getType();
			}

		} else {
			/*
			 * this will go with the probability selection. note probability
			 * selection is disabled by default.
			 */

			return applyProbilities(possibleTypes, extHint, metaHint).getType();
		}

	}

	private MimeType applyProbilities(final List<MimeType> possibleTypes,
			final MimeType extMimeType, final MimeType metadataMimeType) {

		/* initialize some probability variables */
		MimeType extensionMimeType_ = extMimeType;
		MimeType metaMimeType_ = metadataMimeType;

		int n = possibleTypes.size();
		float mag_trust = magic_trust;
		float mag_neg = magic_neg;
		float ext_trust = extension_trust;
		float ext_neg = extension_neg;
		float met_trust = meta_trust;
		float met_neg = meta_neg;
		/* ************************** */

		/* pre-process some probability variables */
		if (extensionMimeType_ == null
				|| extensionMimeType_.compareTo(rootMimeType) == 0) {
			/*
			 * this is a root type, that means the extension method fails to
			 * identify any type.
			 */
			ext_trust = 1;
			ext_neg = 1;
		}
		if (metaMimeType_ == null || metaMimeType_.compareTo(rootMimeType) == 0) {
			met_trust = 1;
			met_neg = 1;
		}

		float maxProb = -1f;
		MimeType bestEstimate = rootMimeType;

		if (possibleTypes != null && !possibleTypes.isEmpty()) {
			int i;
			for (i = 0; i < n; i++) {
				MimeType magictype = possibleTypes.get(i);
				if (magictype != null && magictype.equals(rootMimeType)) {
					mag_trust = 1;
					mag_neg = 1;
				} else {
					// check if each identified type belongs to the same class;
					if (extensionMimeType_ != null) {
						if (extensionMimeType_.equals(magictype)
								|| registry.isSpecializationOf(
										extensionMimeType_.getType(),
										magictype.getType())) {
							// Use just this type
							possibleTypes.set(i, extensionMimeType_);
						} else if (registry.isSpecializationOf(
								magictype.getType(),
								extensionMimeType_.getType())) {
							extensionMimeType_ = magictype;
						}
					}
					if (metaMimeType_ != null) {
						if (metaMimeType_.equals(magictype)
								|| registry.isSpecializationOf(
										metaMimeType_.getType(),
										magictype.getType())) {
							// Use just this type
							possibleTypes.set(i, metaMimeType_);
						} else if (registry.isSpecializationOf(
								magictype.getType(), metaMimeType_.getType())) {
							metaMimeType_ = magictype;
						}
					}
				}

				/*
				 * prepare the conditional probability for file type prediction.
				 */

				float[] results = new float[3];
				float[] trust1 = new float[3];
				float[] negtrust1 = new float[3];
				magictype = possibleTypes.get(i);

				if (i > 0) {
					/*
					 * decay as our trust goes down with next type predicted by
					 * magic
					 */
					mag_trust = mag_trust * (1 - changeRate);
					/*
					 * grow as our trust goes down
					 */
					mag_neg = mag_neg * (1 + changeRate);

				}

				if (magictype != null && mag_trust != 1) {
					trust1[0] = mag_trust;
					negtrust1[0] = mag_neg;
					if (metaMimeType_ != null
							&& magictype.equals(metaMimeType_)) {
						trust1[1] = met_trust;
						negtrust1[1] = met_neg;
					} else {
						trust1[1] = 1;
						negtrust1[1] = 1;
					}
					if (extensionMimeType_ != null
							&& magictype.equals(extensionMimeType_)) {
						trust1[2] = ext_trust;
						negtrust1[2] = ext_neg;
					} else {
						trust1[2] = 1;
						negtrust1[2] = 1;
					}
				} else {
					results[0] = 0.1f;
				}

				float[] trust2 = new float[3];
				float[] negtrust2 = new float[3];
				if (metadataMimeType != null && met_trust != 1) {
					trust2[1] = met_trust;
					negtrust2[1] = met_neg;
					if (magictype != null && metaMimeType_.equals(magictype)) {
						trust2[0] = mag_trust;
						negtrust2[0] = mag_neg;

					} else {
						trust2[0] = 1f;
						negtrust2[0] = 1f;
					}
					if (extensionMimeType_ != null
							&& metaMimeType_.equals(extensionMimeType_)) {
						trust2[2] = ext_trust;
						negtrust2[2] = ext_neg;
					} else {
						trust2[2] = 1f;
						negtrust2[2] = 1f;
					}
				} else {
					results[1] = 0.1f;
				}

				float[] trust3 = new float[3];
				float[] negtrust3 = new float[3];
				if (extensionMimeType_ != null && ext_trust != 1) {
					trust3[2] = ext_trust;
					negtrust3[2] = ext_neg;
					if (magictype != null
							&& magictype.equals(extensionMimeType_)) {
						trust3[0] = mag_trust;
						negtrust3[0] = mag_neg;
					} else {
						trust3[0] = 1f;
						negtrust3[0] = 1f;
					}

					if (metaMimeType_ != null
							&& metaMimeType_.equals(extensionMimeType_)) {
						trust3[1] = met_trust;
						negtrust3[1] = met_neg;
					} else {
						trust3[1] = 1f;
						negtrust3[1] = 1f;
					}
				} else {
					results[2] = 0.1f;
				}
				/*
				 * compute the posterior probability for each predicted file
				 * type and store them into the "results" array.
				 */
				float pPrime = 1;
				float deno = 1;
				int j;

				if (results[0] == 0) {
					for (j = 0; j < trust1.length; j++) {
						pPrime *= trust1[j];
						if (trust1[j] != 1) {
							deno *= negtrust1[j];
						}
					}
					pPrime /= (pPrime + deno);
					results[0] = pPrime;

					pPrime = 1;
					deno = 1;
				}
				if (maxProb < results[0]) {
					maxProb = results[0];
					bestEstimate = magictype;
				}
				if (results[1] == 0) {
					for (j = 0; j < trust2.length; j++) {
						pPrime *= trust2[j];
						if (trust2[j] != 1) {
							deno *= negtrust2[j];
						}
					}
					pPrime /= (pPrime + deno);
					results[1] = pPrime;

					pPrime = 1;
					deno = 1;
				}
				if (maxProb < results[1]) {
					maxProb = results[1];
					bestEstimate = extensionMimeType_;
				}
				if (results[2] == 0) {
					for (j = 0; j < trust3.length; j++) {
						pPrime *= trust3[j];
						if (trust3[j] != 1) {
							deno *= negtrust3[j];
						}
					}
					pPrime /= (pPrime + deno);
					results[2] = pPrime;
				}
				if (maxProb < results[2]) {
					maxProb = results[2];
					bestEstimate = metaMimeType_;
				}
				/*
				 * for (float r : results) { System.out.print(r + "; "); }
				 * System.out.println();
				 */
			}

		}
		return maxProb < threshold ? this.rootMimeType : bestEstimate;

	}

	/**
	 * Use the MimeType hint to try to clarify or specialise the current
	 * possible types list. If the hint is a specialised form, use that instead
	 * If there are multiple possible types, use the hint to select one
	 */
	private List<MimeType> applyHint(List<MimeType> possibleTypes, MimeType hint) {
		if (possibleTypes == null || possibleTypes.isEmpty()) {
			return Collections.singletonList(hint);
		} else {
			for (int i = 0; i < possibleTypes.size(); i++) {
				final MimeType type = possibleTypes.get(i);
				if (hint.equals(type)
						|| registry.isSpecializationOf(hint.getType(),
								type.getType())) {
					// Use just this type
					return Collections.singletonList(hint);

				}
			}
		}

		// Hint didn't help, sorry
		return possibleTypes;
	}

	private static MimeTypes DEFAULT_TYPES = null;
	private static Map<ClassLoader, MimeTypes> CLASSLOADER_SPECIFIC_DEFAULT_TYPES = new HashMap<ClassLoader, MimeTypes>();

	/**
	 * Get the default MimeTypes. This includes all the build in media types,
	 * and any custom override ones present.
	 * 
	 * @return MimeTypes default type registry
	 */
	public static synchronized MimeTypes getDefaultMimeTypes() {
		return getDefaultMimeTypes(null);
	}

	/**
	 * Get the default MimeTypes. This includes all the built-in media types,
	 * and any custom override ones present.
	 * 
	 * @param ClassLoader
	 *            to use, if not the default
	 * @return MimeTypes default type registry
	 */
	public static synchronized MimeTypes getDefaultMimeTypes(
			ClassLoader classLoader) {
		MimeTypes types = DEFAULT_TYPES;
		if (classLoader != null) {
			types = CLASSLOADER_SPECIFIC_DEFAULT_TYPES.get(classLoader);
		}

		if (types == null) {
			try {
				types = MimeTypesFactory.create("tika-mimetypes.xml",
						"custom-mimetypes.xml", classLoader);
			} catch (MimeTypeException e) {
				throw new RuntimeException(
						"Unable to parse the default media type registry", e);
			} catch (IOException e) {
				throw new RuntimeException(
						"Unable to read the default media type registry", e);
			}

			if (classLoader == null) {
				DEFAULT_TYPES = types;
			} else {
				CLASSLOADER_SPECIFIC_DEFAULT_TYPES.put(classLoader, types);
			}
		}
		return types;
	}
}
