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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class GrobidConfig {

  public static final String GROBID_PREFIX = "grobid:";
  public static final String HEADER_METADATA_PREFIX = "header_";

  private String grobidHome; 
  private String grobidProperties; 

  public GrobidConfig() {
    init(this.getClass().getResourceAsStream("GrobidExtractor.properties"));
  }

  private void init(InputStream in) {
    if (in == null) {
      return;
    }

    Properties props = new Properties();
    try {
      props.load(in);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    setGrobidHome(props.getProperty("grobid.home", getGrobidHome()));
    setGrobidProperties(props.getProperty("grobid.properties", getGrobidProperties()));
  }

  public String getGrobidHome() {
    return grobidHome;
  }

  public void setGrobidHome(String grobidHome) {
    this.grobidHome = grobidHome;
  }

  public String getGrobidProperties() {
    return grobidProperties;
  }

  public void setGrobidProperties(String grobidProperties) {
    this.grobidProperties = grobidProperties;
  }
}
