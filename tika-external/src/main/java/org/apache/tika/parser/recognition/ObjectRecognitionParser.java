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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.tf.TensorflowRESTRecogniser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.AnnotationUtils;
import org.apache.tika.utils.ServiceLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * This parser recognises objects from Images.
 * The Object Recognition implementation can be switched using 'class' argument.
 * <p>
 * <b>Example Usage : </b>
 * <pre>
 * &lt;properties&gt;
 *  &lt;parsers&gt;
 *   &lt;parser class=&quot;org.apache.tika.parser.recognition.ObjectRecognitionParser&quot;&gt;
 *    &lt;mime&gt;image/jpeg&lt;/mime&gt;
 *    &lt;params&gt;
 *      &lt;param name=&quot;topN&quot; type=&quot;int&quot;&gt;2&lt;/param&gt;
 *      &lt;param name=&quot;minConfidence&quot; type=&quot;double&quot;&gt;0.015&lt;/param&gt;
 *      &lt;param name=&quot;class&quot; type=&quot;string&quot;&gt;org.apache.tika.parser.recognition.tf.TensorflowRESTRecogniser&lt;/param&gt;
 *    &lt;/params&gt;
 *   &lt;/parser&gt;
 *  &lt;/parsers&gt;
 * &lt;/properties&gt;
 * </pre>
 *
 * @since Apache Tika 1.14
 */
public class ObjectRecognitionParser extends AbstractParser implements Initializable {

    public static final Logger LOG = LoggerFactory.getLogger(ObjectRecognitionParser.class);
    public static final String MD_KEY = "OBJECT";
    public static final String MD_REC_IMPL_KEY =
            ObjectRecognitionParser.class.getPackage().getName() + ".object.rec.impl";
    private static final Comparator<RecognisedObject> DESC_CONFIDENCE_SORTER =
            new Comparator<RecognisedObject>() {
                @Override
                public int compare(RecognisedObject o1, RecognisedObject o2) {
                    return Double.compare(o2.getConfidence(), o1.getConfidence());
                }
            };

    @Field
    private double minConfidence = 0.05;

    @Field
    private int topN = 2;

    private ObjectRecogniser recogniser = new TensorflowRESTRecogniser();

    @Field(name = "class")
    public void setRecogniser(String recogniserClass) {
        this.recogniser = ServiceLoaderUtils.newInstance(recogniserClass);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        AnnotationUtils.assignFieldParams(recogniser, params);
        recogniser.initialize(params);
        LOG.info("minConfidence = {}, topN={}", minConfidence, topN);
        LOG.info("Recogniser = {}", recogniser.getClass().getName());
        LOG.info("Recogniser Available = {}", recogniser.isAvailable());
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
        List<RecognisedObject> objects = recogniser.recognise(stream, handler, metadata, context);
        LOG.debug("Found {} objects", objects != null ? objects.size() : 0);
        LOG.debug("Time taken {}ms", System.currentTimeMillis() - start);
        if (objects != null && !objects.isEmpty()) {

            Collections.sort(objects, DESC_CONFIDENCE_SORTER);
	    int count = 0;
	    List<RecognisedObject> acceptedObjects = new ArrayList<RecognisedObject>(topN);

	    // first process all the MD objects
	    for (RecognisedObject object: objects){
                if (object.getConfidence() >= minConfidence) {
		    if (object.getConfidence() >= minConfidence) {
			count++;
			LOG.debug("Add {}", object);
			String mdValue = String.format(Locale.ENGLISH, "%s (%.5f)",
						   object.getLabel(), object.getConfidence());
			metadata.add(MD_KEY, mdValue);
			acceptedObjects.add(object);
			if (count >= topN) {
			    break;
			}
		    }
		    else{
			LOG.warn("Object {} confidence {} less than min {}", object, object.getConfidence(), minConfidence);
		    }
		}
	    }

	    // now the handler
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
	    xhtml.startDocument();
            xhtml.startElement("ol", "id", "objects");
            count = 0;
            for (RecognisedObject object : acceptedObjects) {
                    //writing to handler
                    xhtml.startElement("li", "id", object.getId());
                    String text = String.format(Locale.ENGLISH, " %s [%s](confidence = %f )",
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
