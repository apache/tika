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
package org.apache.tika.detect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * A composite detector that orchestrates the detection pipeline:
 * <ol>
 *   <li>MimeTypes (magic byte) detection</li>
 *   <li>Spooling to temp file if needed for random access formats</li>
 *   <li>Container and other detectors loaded via SPI</li>
 *   <li>TextDetector as fallback for unknown types</li>
 *   <li>Returns the most specific type detected</li>
 * </ol>
 * <p>
 * Detectors are loaded and returned in a specified order, of user supplied
 * followed by non-MimeType Tika detectors.
 * If you need to control the order of the Detectors, you should instead
 * construct your own {@link CompositeDetector} and pass in the list
 * of Detectors in the required order.
 *
 * @since Apache Tika 0.9
 */
@TikaComponent(spi = false)
public class DefaultDetector extends CompositeDetector {

    private static final long serialVersionUID = -8170114575326908027L;

    private transient final ServiceLoader loader;
    private final Collection<Class<? extends Detector>> excludedClasses;
    private final MimeTypes mimeTypes;
    private final TextDetector textDetector;
    private Set<MediaType> spoolTypes;

    public DefaultDetector(MimeTypes types, ServiceLoader loader,
                           Collection<Class<? extends Detector>> excludeDetectors) {
        super(types.getMediaTypeRegistry(), getDefaultDetectors(loader, excludeDetectors));
        this.loader = loader;
        this.mimeTypes = types;
        this.textDetector = new TextDetector();
        this.excludedClasses = excludeDetectors != null ?
                Collections.unmodifiableCollection(new ArrayList<>(excludeDetectors)) :
                Collections.emptySet();
        this.spoolTypes = getDefaultSpoolTypes();
    }

    public DefaultDetector(MimeTypes types, ServiceLoader loader) {
        this(types, loader, Collections.emptySet());
    }

    public DefaultDetector(MimeTypes types, ClassLoader loader) {
        this(types, new ServiceLoader(loader));
    }

    public DefaultDetector(ClassLoader loader) {
        this(MimeTypes.getDefaultMimeTypes(), loader);
    }

    public DefaultDetector(MimeTypes types) {
        this(types, new ServiceLoader());
    }

    public DefaultDetector() {
        this(MimeTypes.getDefaultMimeTypes());
    }

    private static Set<MediaType> getDefaultSpoolTypes() {
        Set<MediaType> types = new HashSet<>();
        types.add(MediaType.application("zip"));
        types.add(MediaType.application("x-tika-msoffice"));
        types.add(MediaType.application("x-tika-ooxml"));
        types.add(MediaType.application("pdf"));
        types.add(MediaType.application("x-bplist"));
        return types;
    }

    /**
     * Finds all statically loadable detectors and sort the list by name,
     * rather than discovery order. Detectors are used in the given order,
     * so put the Tika parsers last so that non-Tika (user supplied)
     * parsers can take precedence.
     * <p>
     * If an {@link OverrideDetector} is loaded, it takes precedence over
     * all other detectors.
     * <p>
     * Note: MimeTypes is handled separately in the detect() method, not included here.
     *
     * @param loader service loader
     * @return ordered list of statically loadable detectors
     */
    private static List<Detector> getDefaultDetectors(ServiceLoader loader,
                                                      Collection<Class<? extends Detector>>
                                                              excludeDetectors) {
        List<Detector> detectors =
                loader.loadStaticServiceProviders(Detector.class, excludeDetectors);

        ServiceLoaderUtils.sortLoadedClasses(detectors);
        //look for the override index and put that first
        int overrideIndex = -1;
        int i = 0;
        for (Detector detector : detectors) {
            if (detector instanceof OverrideDetector) {
                overrideIndex = i;
                break;
            }
            i++;
        }
        if (overrideIndex > -1) {
            Detector detector = detectors.remove(overrideIndex);
            detectors.add(0, detector);
        }
        return detectors;
    }

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        // 1. Magic detection via MimeTypes
        MediaType magicType = mimeTypes.detect(tis, metadata, parseContext);
        metadata.set(TikaCoreProperties.CONTENT_TYPE_MAGIC_DETECTED, magicType.toString());

        // 2. Spool if needed for random access formats
        if (tis != null && shouldSpool(magicType)) {
            try {
                tis.getFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3. Run other detectors (container detectors, etc.)
        MediaType detectedType = super.detect(tis, metadata, parseContext);

        // 4. Text detection - only if still unknown
        MediaType textType = null;
        if (MediaType.OCTET_STREAM.equals(detectedType) &&
                MediaType.OCTET_STREAM.equals(magicType)) {
            textType = textDetector.detect(tis, metadata, parseContext);
        }

        // 5. Return most specific
        return mostSpecific(magicType, detectedType, textType);
    }

    private boolean shouldSpool(MediaType type) {
        if (spoolTypes == null || spoolTypes.isEmpty()) {
            return false;
        }
        // Check exact match
        if (spoolTypes.contains(type)) {
            return true;
        }
        // Check base type (without parameters)
        MediaType baseType = type.getBaseType();
        if (spoolTypes.contains(baseType)) {
            return true;
        }
        // Check if type is a specialization of any spool type
        MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
        for (MediaType spoolType : spoolTypes) {
            if (registry.isSpecializationOf(type, spoolType)) {
                return true;
            }
        }
        return false;
    }

    private MediaType mostSpecific(MediaType magicType, MediaType detectedType, MediaType textType) {
        MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();

        // Collect non-null, non-octet-stream candidates
        MediaType best = MediaType.OCTET_STREAM;

        // Start with magic type as baseline if valid
        if (magicType != null && !MediaType.OCTET_STREAM.equals(magicType)) {
            best = magicType;
        }

        // Container detectors may find more specific types (e.g., OLE -> msword)
        // or less specific (e.g., commons-compress tar vs magic gtar)
        // Use the registry to determine which is more specific
        if (detectedType != null && !MediaType.OCTET_STREAM.equals(detectedType)) {
            if (MediaType.OCTET_STREAM.equals(best)) {
                best = detectedType;
            } else if (registry.isSpecializationOf(detectedType, best)) {
                // detectedType is more specific than best
                best = detectedType;
            } else if (!registry.isSpecializationOf(best, detectedType)) {
                // Neither is a specialization of the other - prefer container detection
                // for unrelated types (e.g., different format families)
                best = detectedType;
            }
            // else: best is already more specific than detectedType, keep best
        }

        // Text detection as fallback only if still unknown
        if (MediaType.OCTET_STREAM.equals(best) && textType != null &&
                !MediaType.OCTET_STREAM.equals(textType)) {
            best = textType;
        }

        return best;
    }

    @Override
    public List<Detector> getDetectors() {
        if (loader != null && loader.isDynamic()) {
            List<Detector> detectors = loader.loadDynamicServiceProviders(Detector.class);
            if (!detectors.isEmpty()) {
                detectors.addAll(super.getDetectors());
                return detectors;
            } else {
                return super.getDetectors();
            }
        } else {
            return super.getDetectors();
        }
    }

    /**
     * Returns the classes that were explicitly excluded when constructing this detector.
     * Used for round-trip serialization to preserve exclusion configuration.
     *
     * @return unmodifiable collection of excluded detector classes, never null
     */
    public Collection<Class<? extends Detector>> getExcludedClasses() {
        return excludedClasses;
    }

    /**
     * Sets the media types that should be spooled to a temp file before
     * container detection. This enables random access for formats like
     * ZIP, OLE, and PDF.
     *
     * @param spoolTypes set of media types to spool
     */
    public void setSpoolTypes(Set<MediaType> spoolTypes) {
        this.spoolTypes = spoolTypes;
    }

    /**
     * Returns the media types that are spooled to temp files.
     *
     * @return set of media types to spool
     */
    public Set<MediaType> getSpoolTypes() {
        return spoolTypes;
    }
}
