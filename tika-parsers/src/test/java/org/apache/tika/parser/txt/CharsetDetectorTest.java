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
package org.apache.tika.parser.txt;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

public class CharsetDetectorTest {
  @Test
  public void testTagDropper() throws IOException {
    InputStream in = CharsetDetectorTest.class.getResourceAsStream( "/test-documents/resume.html" );

    try {
      CharsetDetector detector = new CharsetDetector();
      detector.enableInputFilter(true);
      detector.setText(in);
      CharsetMatch [] matches = detector.detectAll();
      CharsetMatch mm = null;
      for ( CharsetMatch m : matches ) {
        if ( mm == null || mm.getConfidence() < m.getConfidence() ) {
          mm = m;
        }
      }
      assertTrue( mm != null );
      assertEquals( "UTF-8", mm.getName() );
    } finally {
      in.close();
    }
  }
}
