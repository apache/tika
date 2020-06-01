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
package org.apache.tika.parser.apple;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSSet;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.lexicalscope.jewelcli.internal.cglib.asm.$MethodAdapter;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;


import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Parser for Apple's plist and bplist.  This is a wrapper around
 *       <groupId>com.googlecode.plist</groupId>
 *       <artifactId>dd-plist</artifactId>
 *       <version>1.23</version>
 */
public class PListParser extends AbstractParser {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-bplist"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        NSObject rootObj = null;
        try {
            if (stream instanceof TikaInputStream && ((TikaInputStream) stream).hasFile()) {
                rootObj = PropertyListParser.parse(((TikaInputStream) stream).getFile());
            } else {
                rootObj = PropertyListParser.parse(stream);
            }
        } catch (PropertyListFormatException|ParseException|ParserConfigurationException e) {
            throw new TikaException("problem parsing root", e);
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        parseObject(rootObj, xhtml, metadata);
        xhtml.endDocument();
    }

    private void parseObject(NSObject obj, XHTMLContentHandler handler, Metadata metadata)
            throws SAXException {

        if (obj instanceof NSDictionary) {
            parseDict((NSDictionary)obj, handler, metadata);
        } else if (obj instanceof NSArray) {
            NSArray nsArray = (NSArray)obj;
            for (NSObject child : nsArray.getArray()) {
                parseObject(child, handler, metadata);
            }
        } else if (obj instanceof NSString) {
            handler.characters(((NSString)obj).toString());
        } else if (obj instanceof NSNumber) {
            handler.characters(((NSNumber) obj).toString());
        } else if (obj instanceof NSData) {
            handleData((NSData) obj, handler, metadata);
        } else if (obj instanceof NSDate) {
            handler.characters(((NSDate)obj).toString());
        } else{
            throw new UnsupportedOperationException("don't know baout: "+obj.getClass());

        }
    }

    private void parseDict(NSDictionary obj, XHTMLContentHandler xhtml, Metadata metadata) throws SAXException {
        for (Map.Entry<String, NSObject> mapEntry : obj.getHashMap().entrySet()) {
            String key = mapEntry.getKey();
            NSObject value = mapEntry.getValue();
            xhtml.startElement("div", "class", key);
            parseObject(value, xhtml, metadata);
            xhtml.endElement("div");
        }
    }

    private void handleData(NSData value, XHTMLContentHandler handler, Metadata metadata) {
        byte[] bytes = value.bytes();
        //TODO handle embedded file
    }
}
