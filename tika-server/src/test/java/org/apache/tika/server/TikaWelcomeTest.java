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

package org.apache.tika.server;

import java.io.InputStream;

import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.Tika;
import org.junit.Test;

public class TikaWelcomeTest extends CXFTestBase {
   private static final String WELCOME_PATH = "/";

   @Override
   protected void setUpResources(JAXRSServerFactoryBean sf) {
       sf.setResourceClasses(TikaWelcome.class);
       sf.setResourceProvider(
               TikaWelcome.class,
           new SingletonResourceProvider(new TikaWelcome(tika, sf))
       );
   }

   @Override
   protected void setUpProviders(JAXRSServerFactoryBean sf) {}

   @Test
   public void testGetHTMLWelcome() throws Exception {
       Response response = WebClient
               .create(endPoint + WELCOME_PATH)
               .type("text/html")
               .accept("text/html")
               .get();

       String html = getStringFromInputStream((InputStream) response.getEntity());
       
       assertContains(new Tika().toString(), html);
       assertContains("href=\"http", html);
   }

   @Test
   public void testGetTextWelcome() throws Exception {
       Response response = WebClient
               .create(endPoint + WELCOME_PATH)
               .type("text/plain")
               .accept("text/plain")
               .get();

       assertContains(new Tika().toString(), 
               getStringFromInputStream((InputStream) response.getEntity()));
   }
}
