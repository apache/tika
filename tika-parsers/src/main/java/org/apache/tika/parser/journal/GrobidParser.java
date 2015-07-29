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
package org.apache.tika.parser.journal;

import java.util.Map.Entry;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;
import org.xml.sax.ContentHandler;

public class GrobidParser {

  public GrobidParser(){

  }

  public void parse(String filePath, ContentHandler handler, Metadata metadata, ParseContext context) {
    GrobidConfig gConfig = new GrobidConfig();
    try {
      MockContext.setInitialContext(gConfig.getGrobidHome(), gConfig.getGrobidProperties());
      GrobidProperties.getInstance();

      Engine engine = GrobidFactory.getInstance().createEngine();
      BiblioItem resHeader = new BiblioItem();
      engine.processHeader(filePath, false, resHeader);
      GrobidHeaderMetadata gheaderMetada = new GrobidHeaderMetadata();
      gheaderMetada.generateHeaderMetada(resHeader);
      populateTikaMetadata(gheaderMetada, metadata);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void populateTikaMetadata(GrobidHeaderMetadata gheaderMetada, Metadata metadata) {
    for(Entry<String, String> pair: gheaderMetada.getHeaderMetadata().entrySet()) {
      metadata.add(GrobidConfig.GROBID_PREFIX + pair.getKey(), pair.getValue());
    }
  }

}
