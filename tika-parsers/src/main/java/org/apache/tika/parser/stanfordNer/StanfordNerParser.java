package org.apache.tika.parser.stanfordNer;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Taichi1 on 11/4/15.
 */
public class StanfordNerParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType
            .application("stanford-ner");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MEDIA_TYPE);
    private static final Logger LOG = Logger.getLogger(StanfordNerParser.class
            .getName());

    // The trained model used to tag the entities. Feel free to change.
    private static final String serializedClassifier = "classifiers/english.all.3class.distsim.crf.ser.gz";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {


        if (!isAvailable()) {
            metadata.add("Parser", "Unavailable");
            return;
        }

        try {
            InputStream modelStream = this.getClass().getResourceAsStream(serializedClassifier);
            InputStream gzipStream = new GZIPInputStream(new BufferedInputStream(modelStream));
            CRFClassifier<? extends CoreMap> classifier =  CRFClassifier.getClassifier(gzipStream);
            String input = IOUtils.toString(stream, UTF_8);
            String output = classifier.classifyWithInlineXML(input);
            SAXParserFactory parserFactor = SAXParserFactory.newInstance();
            SAXParser parser = parserFactor.newSAXParser();
            MyXmlHandler myHandler = new MyXmlHandler();
            output = "<doc>" + output + "</doc>";
            parser.parse(IOUtils.toInputStream(output, UTF_8), myHandler);

            for (String entityName : myHandler.result.keySet()) {
                if (!entityName.equals("doc")) {
                    String entitiesAsString = myHandler.result.get(entityName).toString();
                    metadata.add(entityName, entitiesAsString);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            metadata.add("Parser", "ClassNotFound");
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            metadata.add("Parser", "ParserConfigWrong");
        }

    }

    public boolean isAvailable() {
        return this.getClass().getResource(serializedClassifier) != null;
    }

    class MyXmlHandler extends DefaultHandler {
        String content = "";
        HashMap<String, List<String>> result = new HashMap<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            List<String> list = result.get(qName);
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(content);
            result.put(qName, list);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            content = String.copyValueOf(ch, start, length).trim();
        }
    }
}
