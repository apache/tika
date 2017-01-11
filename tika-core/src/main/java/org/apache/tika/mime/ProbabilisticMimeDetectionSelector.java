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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;

/**
 * Selector for combining different mime detection results
 *  based on probability
 */
public class ProbabilisticMimeDetectionSelector implements Detector {
    private static final long serialVersionUID = 224589862960269260L;

    private MimeTypes mimeTypes;

    private final MediaType rootMediaType;

    /** probability parameters default value */
    private static final float DEFAULT_MAGIC_TRUST = 0.9f;
    private static final float DEFAULT_META_TRUST = 0.8f;
    private static final float DEFAULT_EXTENSION_TRUST = 0.8f;
    private float priorMagicFileType, priorExtensionFileType,
    priorMetaFileType;
    private float magic_trust, extension_trust, meta_trust;
    private float magic_neg, extension_neg, meta_neg;
    /*
     * any posterior probability lower than the threshold, will be considered as
     * an oct-stream type, the default value is 0.5
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

    /** ***********************/

    public ProbabilisticMimeDetectionSelector() {
        this(MimeTypes.getDefaultMimeTypes(), null);
    }

    public ProbabilisticMimeDetectionSelector(final Builder builder) {
        this(MimeTypes.getDefaultMimeTypes(), builder);
    }

    public ProbabilisticMimeDetectionSelector(final MimeTypes mimeTypes) {
        this(mimeTypes, null);
    } 

    public ProbabilisticMimeDetectionSelector(final MimeTypes mimeTypes,
            final Builder builder) {
        this.mimeTypes = mimeTypes;
        rootMediaType = MediaType.OCTET_STREAM;
        this.initializeDefaultProbabilityParameters();
        this.changeRate = 0.1f;
        if (builder != null) {
            priorMagicFileType = builder.priorMagicFileType == 0f ? 
                    priorMagicFileType : builder.priorMagicFileType;
            priorExtensionFileType = builder.priorExtensionFileType == 0f ? 
                    priorExtensionFileType : builder.priorExtensionFileType;
            priorMetaFileType = builder.priorMetaFileType == 0f ? 
                    priorMetaFileType : builder.priorMetaFileType;

            magic_trust = builder.magic_trust == 0f ? magic_trust : builder.extension_neg;
            extension_trust = builder.extension_trust == 0f ? extension_trust : builder.extension_trust;
            meta_trust = builder.meta_trust == 0f ? meta_trust : builder.meta_trust;

            magic_neg = builder.magic_neg == 0f ? magic_neg : builder.magic_neg;
            extension_neg = builder.extension_neg == 0f ? 
                    extension_neg : builder.extension_neg;
            meta_neg = builder.meta_neg == 0f ? meta_neg : builder.meta_neg;
            threshold = builder.threshold == 0f ? threshold : builder.threshold;
        }
    }

    /**
     * Initilize probability parameters with default values;
     */
    private void initializeDefaultProbabilityParameters() {
        priorMagicFileType = 0.5f;
        priorExtensionFileType = 0.5f;
        priorMetaFileType = 0.5f;
        magic_trust = DEFAULT_MAGIC_TRUST;
        extension_trust = DEFAULT_EXTENSION_TRUST;
        meta_trust = DEFAULT_META_TRUST;

        // probability of the type detected by magic test given that the type is
        // not the detected type. The default is taken by 1 - the magic trust
        magic_neg = 1 - DEFAULT_MAGIC_TRUST;
        // probability of the type detected by extension test given that the
        // type is not the type detected by extension test
        extension_neg = 1 - DEFAULT_EXTENSION_TRUST;
        // same as above; but it could be customized to suffice different use.
        meta_neg = 1 - DEFAULT_META_TRUST;
        threshold = 0.5001f;
    }

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {

        List<MimeType> possibleTypes = new ArrayList<>();

        // Get type based on magic prefix
        if (input != null) {
            input.mark(mimeTypes.getMinLength());
            try {
                byte[] prefix = mimeTypes.readMagicHeader(input);
                //defensive copy
                possibleTypes.addAll(mimeTypes.getMimeType(prefix));
            } finally {
                input.reset();
            }
        }

        MimeType extHint = null;
        // Get type based on resourceName hint (if available)
        String resourceName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            String name = null;

            // Deal with a URI or a path name in as the resource name
            try {
                URI uri = new URI(resourceName);
                String path = uri.getPath();
                if (path != null) {
                    int slash = path.lastIndexOf('/');
                    if (slash + 1 < path.length()) {
                        name = path.substring(slash + 1);
                    }
                }
            } catch (URISyntaxException e) {
                name = resourceName;
            }

            if (name != null) {
                // MimeType hint = getMimeType(name);
                extHint = mimeTypes.getMimeType(name);
                // If we have some types based on mime magic, try to specialise
                // and/or select the type based on that
                // Otherwise, use the type identified from the name
                // possibleTypes = applyHint(possibleTypes, hint);
            }
        }

        // Get type based on metadata hint (if available)
        MimeType metaHint = null;
        String typeName = metadata.get(Metadata.CONTENT_TYPE);
        if (typeName != null) {
            try {
                // MimeType hint = forName(typeName);
                metaHint = mimeTypes.forName(typeName);
                // possibleTypes = applyHint(possibleTypes, hint);
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        /*
         * the following calls the probability selection.
         */
        return applyProbilities(possibleTypes, extHint, metaHint);
    }

    private MediaType applyProbilities(final List<MimeType> possibleTypes,
            final MimeType extMimeType, final MimeType metadataMimeType) {

        /* initialize some probability variables */
        MediaType extensionMediaType_ = extMimeType == null ? null : extMimeType.getType();
        MediaType metaMediaType_ = metadataMimeType == null ? null : metadataMimeType.getType();

        int n = possibleTypes.size();
        float mag_trust = magic_trust;
        float mag_neg = magic_neg;
        float ext_trust = extension_trust;
        float ext_neg = extension_neg;
        float met_trust = meta_trust;
        float met_neg = meta_neg;
        /* ************************** */

        /* pre-process some probability variables */
        if (extensionMediaType_ == null || extensionMediaType_.compareTo(rootMediaType) == 0) {
            /*
             * this is a root type, that means the extension method fails to
             * identify any type.
             */
            ext_trust = 1;
            ext_neg = 1;
        }
        if (metaMediaType_ == null || metaMediaType_.compareTo(rootMediaType) == 0) {
            met_trust = 1;
            met_neg = 1;
        }

        float maxProb = -1f;
        MediaType bestEstimate = rootMediaType;

        if (possibleTypes != null && !possibleTypes.isEmpty()) {
            int i;
            for (i = 0; i < n; i++) {
                MediaType magictype = possibleTypes.get(i).getType();
                MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
                if (magictype != null && magictype.equals(rootMediaType)) {
                    mag_trust = 1;
                    mag_neg = 1;
                } else {
                    // check if each identified type belongs to the same class;
                    if (extensionMediaType_ != null) {
                        if (extensionMediaType_.equals(magictype)
                                || registry.isSpecializationOf(
                                        extensionMediaType_, magictype)) {
                            // Use just this type
                            possibleTypes.set(i, extMimeType);
                        } else if (registry.isSpecializationOf(magictype,
                                extensionMediaType_)) {
                            extensionMediaType_ = magictype;
                        }
                    }
                    if (metaMediaType_ != null) {
                        if (metaMediaType_.equals(magictype)
                                || registry.isSpecializationOf(metaMediaType_,
                                        magictype)) {
                            // Use just this type
                            possibleTypes.set(i, metadataMimeType);
                        } else if (registry.isSpecializationOf(magictype,
                                metaMediaType_)) {
                            metaMediaType_ = magictype;
                        }
                    }
                }

                /*
                 * prepare the conditional probability for file type prediction.
                 */

                float[] results = new float[3];
                float[] trust1 = new float[3];
                float[] negtrust1 = new float[3];
                magictype = possibleTypes.get(i).getType();

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
                    if (metaMediaType_ != null && met_trust != 1) {
                        if (magictype.equals(metaMediaType_)) {
                            trust1[1] = met_trust;
                            negtrust1[1] = met_neg;
                        } else {
                            trust1[1] = 1 - met_trust;
                            negtrust1[1] = 1 - met_neg;
                        }
                    } else {
                        trust1[1] = 1;
                        negtrust1[1] = 1;
                    }
                    if (extensionMediaType_ != null && ext_trust != 1) {
                        if (magictype.equals(extensionMediaType_)) {
                            trust1[2] = ext_trust;
                            negtrust1[2] = ext_neg;
                        } else {
                            trust1[2] = 1 - ext_trust;
                            negtrust1[2] = 1 - ext_neg;
                        }
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
                    if (magictype != null && mag_trust != 1) {
                        if (metaMediaType_.equals(magictype)) {
                            trust2[0] = mag_trust;
                            negtrust2[0] = mag_neg;
                        } else {
                            trust2[0] = 1 - mag_trust;
                            negtrust2[0] = 1 - mag_neg;
                        }

                    } else {
                        trust2[0] = 1f;
                        negtrust2[0] = 1f;
                    }
                    if (extensionMediaType_ != null && ext_trust != 1) {
                        if (metaMediaType_.equals(extensionMediaType_)) {
                            trust2[2] = ext_trust;
                            negtrust2[2] = ext_neg;
                        } else {
                            trust2[2] = 1 - ext_trust;
                            negtrust2[2] = 1 - ext_neg;
                        }
                    } else {
                        trust2[2] = 1f;
                        negtrust2[2] = 1f;
                    }
                } else {
                    results[1] = 0.1f;
                }

                float[] trust3 = new float[3];
                float[] negtrust3 = new float[3];
                if (extensionMediaType_ != null && ext_trust != 1) {
                    trust3[2] = ext_trust;
                    negtrust3[2] = ext_neg;
                    if (magictype != null && mag_trust != 1) {
                        if (magictype.equals(extensionMediaType_)) {
                            trust3[0] = mag_trust;
                            negtrust3[0] = mag_neg;
                        } else {
                            trust3[0] = 1 - mag_trust;
                            negtrust3[0] = 1 - mag_neg;
                        }
                    } else {
                        trust3[0] = 1f;
                        negtrust3[0] = 1f;
                    }

                    if (metaMediaType_ != null && met_trust != 1) {
                        if (metaMediaType_.equals(extensionMediaType_)) {
                            trust3[1] = met_trust;
                            negtrust3[1] = met_neg;
                        } else {
                            trust3[1] = 1 - met_trust;
                            negtrust3[1] = 1 - met_neg;
                        }
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
                float pPrime = priorMagicFileType;
                float deno = 1 - priorMagicFileType;
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

                }
                if (maxProb < results[0]) {
                    maxProb = results[0];
                    bestEstimate = magictype;
                }

                pPrime = priorMetaFileType;
                deno = 1 - priorMetaFileType;
                if (results[1] == 0) {
                    for (j = 0; j < trust2.length; j++) {
                        pPrime *= trust2[j];
                        if (trust2[j] != 1) {
                            deno *= negtrust2[j];
                        }
                    }
                    pPrime /= (pPrime + deno);
                    results[1] = pPrime;

                }
                if (maxProb < results[1]) {
                    maxProb = results[1];
                    bestEstimate = metaMediaType_;
                }

                pPrime = priorExtensionFileType;
                deno = 1 - priorExtensionFileType;
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
                    bestEstimate = extensionMediaType_;
                }
                /*
				for (float r : results) {
					System.out.print(r + "; ");
				}
				System.out.println();
                 */
            }

        }
        return maxProb < threshold ? this.rootMediaType : bestEstimate;

    }

    public MediaTypeRegistry getMediaTypeRegistry() {
        return this.mimeTypes.getMediaTypeRegistry();
    }

    /**
     * build class for probability parameters setting
     * 
     * 
     */
    public static class Builder {
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
         * Initialize the MimeTypes with this builder instance
         */
        public ProbabilisticMimeDetectionSelector build2() {
            return new ProbabilisticMimeDetectionSelector(this);
        }
    }

}
