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
package org.apache.tika.parser.code;

import static com.uwyn.jhighlight.renderer.XhtmlRendererFactory.CPP;
import static com.uwyn.jhighlight.renderer.XhtmlRendererFactory.GROOVY;
import static com.uwyn.jhighlight.renderer.XhtmlRendererFactory.JAVA;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Schema;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.uwyn.jhighlight.renderer.Renderer;
import com.uwyn.jhighlight.renderer.XhtmlRendererFactory;
/**
 * Generic Source code parser for Java, Groovy, C++.
 * Aware: This parser uses JHightlight library (https://github.com/codelibs/jhighlight) under CDDL/LGPL dual license
 *
 * @author Hong-Thai.Nguyen
 * @since 1.6
 */
public class SourceCodeParser implements Parser {

  private static final long serialVersionUID = -4543476498190054160L;

  private static final Pattern authorPattern = Pattern.compile("(?im)@author (.*) *$");

  private static final Map<MediaType, String> TYPES_TO_RENDERER = new HashMap<MediaType, String>() {
    private static final long serialVersionUID = -741976157563751152L;
    {
      put(MediaType.text("x-c++src"), CPP);
      put(MediaType.text("x-java-source"), JAVA);
      put(MediaType.text("x-groovy"), GROOVY);
    }
  };

  private static final ServiceLoader LOADER = new ServiceLoader(SourceCodeParser.class.getClassLoader());
  
  //Parse the HTML document
  private static final Schema HTML_SCHEMA = new HTMLSchema();
  
  @Override
  public Set<MediaType> getSupportedTypes(ParseContext context) {
    return TYPES_TO_RENDERER.keySet();
  }

  @Override
  public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
      throws IOException, SAXException, TikaException {

    AutoDetectReader reader = new AutoDetectReader(new CloseShieldInputStream(stream), metadata, context.get(ServiceLoader.class, LOADER));

    try {
      Charset charset = reader.getCharset();
      String mediaType = metadata.get(Metadata.CONTENT_TYPE);
      String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
      if (mediaType != null && name != null) {
        MediaType type = MediaType.parse(mediaType);
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
        metadata.set(Metadata.CONTENT_ENCODING, charset.name());

        StringBuilder out = new StringBuilder();
        String line;
        int nbLines =  0;
        while ((line = reader.readLine()) != null) {
            out.append(line + System.getProperty("line.separator"));
            String author = parserAuthor(line);
            if (author != null) {
              metadata.add(TikaCoreProperties.CREATOR, author);
            }
            nbLines ++;
        }
        metadata.set("LoC", String.valueOf(nbLines));
        Renderer renderer = getRenderer(type.toString());
        
        String codeAsHtml = renderer.highlight(name, out.toString(), charset.name(), false);
        
        Schema schema = context.get(Schema.class, HTML_SCHEMA);

        org.ccil.cowan.tagsoup.Parser parser = new org.ccil.cowan.tagsoup.Parser();
        parser.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, schema);
        parser.setContentHandler(handler);
        parser.parse(new InputSource(new StringReader(codeAsHtml)));
      }
    } finally {
      reader.close();
    }

  }

  private Renderer getRenderer(String mimeType) {
    MediaType mt = MediaType.parse(mimeType);
    String type = TYPES_TO_RENDERER.get(mt);
    if (type == null) {
      throw new RuntimeException("unparseable content type " + mimeType);
    }
    return XhtmlRendererFactory.getRenderer(type);
  }


  private String parserAuthor(String line) {
    Matcher m = authorPattern.matcher(line);
    if (m.find()) {
      return m.group(1).trim();
    }

    return null;
  }
}
