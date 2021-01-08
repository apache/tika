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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser for PureEdge Extensible Forms Description Language (XFDL).
 * This parser extracts any text found in the XFDL XML, whether that XML
 * is Base64 encoded or just plain XML (two possible formats for XFDL).
 * In addition, it will store label and field values as metadata, on top
 * of some global information (form title, form version, ...).
 * 
 * @author Pascal Essiembre 
*/
public class XFDLParser extends XMLParser {

    private static final long serialVersionUID = 3502707305732365813L;

    private static final String MAGIC_BASE64_GZIP = 
          "application/vnd.xfdl;content-encoding=\"base64-gzip\"";
    
    private static final Set<MediaType> SUPPORTED_TYPES =
           Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                   MediaType.application("vnd.xfdl")
                   // what about these?
                   //application/uwi_form
                   //application/vnd.ufdl
                   //application/x-xfdl
           )));
   
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, 
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

       InputStream is = IOUtils.buffer(stream);
       is.mark(MAGIC_BASE64_GZIP.length());
       byte[] signature = new byte[MAGIC_BASE64_GZIP.length()];
       is.read(signature);
       if (MAGIC_BASE64_GZIP.equals(new String(signature, CharEncoding.UTF_8))) {
           // un-encode and uncompress the stream
           is = new GZIPInputStream(new Base64InputStream(is)); 
       } else {
           is.reset();
       }
       super.parse(is, handler, metadata, context);
    }
   
   
   protected ContentHandler getContentHandler(
           ContentHandler handler, Metadata metadata, ParseContext context) {

       return new TeeContentHandler(
               super.getContentHandler(handler, metadata, context),
               new XFDLHandler(metadata));
   }
   private class XFDLHandler extends DefaultHandler {
       private final Metadata metadata;
       private final StringBuilder path = new StringBuilder();
       private String lastSID = null;
       public XFDLHandler(Metadata metadata) {
           this.metadata = metadata;
       }
       @Override
       public void startPrefixMapping(
               String prefix, String uri) throws SAXException {
           if ("xfdl".equals(prefix)) {
               metadata.add("xfdl:version", 
                       StringUtils.substringAfterLast(uri, "/"));
           }
       }
       @Override
       public void startElement(
               String uri, String localName, String qName, Attributes attrs)
               throws SAXException {
           path.append('/');           
           path.append(localName);
           
           for (int i = 0; i < attrs.getLength(); i++) {
               String attrName = attrs.getLocalName(i);
               String attrValue = attrs.getValue(i);
               parsedValue(path + "@" + attrName, attrValue);
               if ("sid".equals(attrName)) {
                   lastSID = attrValue;
               }
           }
       }
       @Override
       public void endElement(
               String uri, String localName, String qName) throws SAXException {
           path.setLength(path.length() - localName.length());
           if(path.charAt(path.length() - 1) == '/') {
               path.setLength(path.length() - 1);
           }
       }
       @Override
       public void characters(
               char[] str, int offset, int len) throws SAXException {
           String path = this.path.toString();
           String value = new String(str, offset, len);
           if (StringUtils.isNotBlank(value)) {
               parsedValue(path, value);
           }
       }
       private void parsedValue(String path, String value) {
           if (path.endsWith("global/formid/title")) {
               metadata.add("xfdl:formid.title", value);
           } else if (path.endsWith("global/formid/serialnumber")) {
               metadata.add("xfdl:formid.serialnumber", value);
           } else if (path.endsWith("global/formid/version")) {
               metadata.add("xfdl:formid.version", value);
           } else if (path.endsWith("page/field/label")) {
               metadata.add("xfdl:field." + lastSID + ".label", value);
           } else if (path.endsWith("page/field/value")) {
               metadata.add("xfdl:field." + lastSID + ".value", value);
           } else if (path.endsWith("page/label/value")) {
               metadata.add("xfdl:label." + lastSID + ".value", value);
           }
       }
   }
   
}