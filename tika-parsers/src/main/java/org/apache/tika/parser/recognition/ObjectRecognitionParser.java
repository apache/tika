/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.recognition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.captioning.CaptionObject;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.AnnotationUtils;
import org.apache.tika.utils.ServiceLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


/**
 * This parser recognises objects from Images.
 * The Object Recognition implementation can be switched using 'class' argument.
 * <p>
 * <b>Example Usage : </b>
 * <pre>
 * &lt;properties&gt;
 *  &lt;parsers&gt;
 *   &lt;parser class=&quot;org.apache.tika.parser.recognition.ObjectRecognitionParser&quot;&gt;
 *    &lt;params&gt;
 *      &lt;param name=&quot;class&quot; type=&quot;string&quot;&gt;org.apache.tika.parser.recognition.tf.TensorflowRESTRecogniser&lt;/param&gt;
 *      &lt;param name=&quot;class&quot; type=&quot;string&quot;&gt;org.apache.tika.parser.captioning.tf.TensorflowRESTCaptioner&lt;/param&gt;
 *    &lt;/params&gt;
 *   &lt;/parser&gt;
 *  &lt;/parsers&gt;
 * &lt;/properties&gt;
 * </pre>
 *
 * @since Apache Tika 1.14
 */
public class ObjectRecognitionParser extends AbstractParser implements Initializable {
    private static final Logger LOG = LoggerFactory.getLogger(ObjectRecognitionParser.class);

    public static final String MD_KEY_OBJ_REC = "OBJECT";
    public static final String MD_KEY_IMG_CAP = "CAPTION";
    public static final String MD_REC_IMPL_KEY =
            ObjectRecognitionParser.class.getPackage().getName() + ".object.rec.impl";
    private static final Comparator<RecognisedObject> DESC_CONFIDENCE_SORTER =
            new Comparator<RecognisedObject>() {
                @Override
                public int compare(RecognisedObject o1, RecognisedObject o2) {
                    return Double.compare(o2.getConfidence(), o1.getConfidence());
                }
            };

    private ObjectRecogniser recogniser;

    @Field(name = "class")
    public void setRecogniser(String recogniserClass) {
        this.recogniser = ServiceLoaderUtils.newInstance(recogniserClass);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        AnnotationUtils.assignFieldParams(recogniser, params);
        recogniser.initialize(params);
        LOG.info("Recogniser = {}", recogniser.getClass().getName());
        LOG.info("Recogniser Available = {}", recogniser.isAvailable());
    }

    @Override
    public void checkInitialization(InitializableProblemHandler handler) throws TikaConfigException {
        //TODO -- what do we want to check?
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return recogniser.isAvailable() ? recogniser.getSupportedMimes() : Collections.<MediaType>emptySet();
    }

    @Override
    public synchronized void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (!recogniser.isAvailable()) {
            LOG.warn("{} is not available for service", recogniser.getClass());
            return;
        }
        metadata.set(MD_REC_IMPL_KEY, recogniser.getClass().getName());
        long start = System.currentTimeMillis();
        List<? extends RecognisedObject> objects = recogniser.recognise(stream, handler, metadata, context);

        LOG.debug("Found {} objects", objects != null ? objects.size() : 0);
        LOG.info("Time taken {}ms", System.currentTimeMillis() - start);

        if (objects != null && !objects.isEmpty()) {
            int count;
            List<RecognisedObject> acceptedObjects = new ArrayList<RecognisedObject>();
            List<String> xhtmlIds = new ArrayList<String>();
            String xhtmlStartVal = null;
            count = 0;
            Collections.sort(objects, DESC_CONFIDENCE_SORTER);
            // first process all the MD objects
            for (RecognisedObject object : objects) {
                if (object instanceof CaptionObject) {
                    if (xhtmlStartVal == null) xhtmlStartVal = "captions";
                    String labelAndConfidence = String.format(Locale.ENGLISH, "%s (%.5f)", object.getLabel(), object.getConfidence());
                    metadata.add(MD_KEY_IMG_CAP, labelAndConfidence);
                    xhtmlIds.add(String.valueOf(count++));
                } else {
                    if (xhtmlStartVal == null) xhtmlStartVal = "objects";
                    String labelAndConfidence = String.format(Locale.ENGLISH, "%s (%.5f)", object.getLabel(), object.getConfidence());
                    metadata.add(MD_KEY_OBJ_REC, labelAndConfidence);
                    xhtmlIds.add(object.getId());
                }
                LOG.info("Add {}", object);
                acceptedObjects.add(object);
            }
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.startElement("ol", "id", xhtmlStartVal);
            count = 0;
            for (RecognisedObject object : acceptedObjects) {
                //writing to handler
                xhtml.startElement("li", "id", xhtmlIds.get(count++));
                String text = String.format(Locale.ENGLISH, " %s [%s](confidence = %f)",
                        object.getLabel(), object.getLabelLang(), object.getConfidence());
                xhtml.characters(text);
                xhtml.endElement("li");
            }
            xhtml.endElement("ol");
            xhtml.endDocument();
        } else {
            LOG.warn("NO objects");
            metadata.add("no.objects", Boolean.TRUE.toString());
        }
    }
}