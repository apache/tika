/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.xfdl;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

/**
* Junit test class for the {@link XFDLParser}.
* @author Pascal Essiembre
*/
public class XFDLParserTest extends TikaTest {

   @Test
   public void testXFDL_Plain() throws Exception {
       XMLResult r = getXML("testXFDL_7.6_plain.xfdl");
       assertEquals("application/vnd.xfdl",
               r.metadata.get(Metadata.CONTENT_TYPE));
       assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
       // body has content metadata in addition to metadata values 
       assertContains("Orange", r.xml);
       assertContains("This is a test", r.xml);
       assertEquals("This is a test.", r.metadata.get("xfdl:formid.title"));
   }

   @Test
   public void testXFDL_Base64Gzip() throws Exception {
       XMLResult r = getXML("testXFDL_6.5_base64gzip.xfdl");
       assertEquals("application/vnd.xfdl",
               r.metadata.get(Metadata.CONTENT_TYPE));
       assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
       // body has content metadata in addition to metadata values 
       assertContains("check for approval", r.xml);
       assertContains("PART IV - RECOMMENDATIONS", r.xml);
       assertEquals(3, r.metadata.getValues("xfdl:label.LABEL29.value").length);
       assertContains("PART IV - RECOMMENDATIONS/APPROVAL/DISAPPROVAL",
               Arrays.asList(r.metadata.getValues("xfdl:label.LABEL29.value")));
   }
}
