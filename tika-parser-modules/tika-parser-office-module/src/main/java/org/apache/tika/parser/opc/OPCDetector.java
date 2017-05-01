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
package org.apache.tika.parser.opc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Detector that detects OPC Packages
 *
 */
public class OPCDetector implements Detector {

    /**
     * 
     */
    private static final long serialVersionUID = -3569622763024617244L;
    
    private static final Pattern MACRO_TEMPLATE_PATTERN = Pattern.compile("macroenabledtemplate$", Pattern.CASE_INSENSITIVE);

    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes 
    private static final String VISIO_DOCUMENT =
            "http://schemas.microsoft.com/visio/2010/relationships/document";
    
    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes 
    private static final String STRICT_CORE_DOCUMENT = 
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";
    
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream stream = TikaInputStream.get(input, tmp);
            // Use POI to open and investigate it for us
            OPCPackage pkg = OPCPackage.open(stream.getFile().getPath(), PackageAccess.READ);
            stream.setOpenContainer(pkg);
    
            // Is at an OOXML format?
            MediaType type = detectOfficeOpenXML(pkg);
            if (type != null) return type;
            
            // Is it XPS format?
            type = detectXPSOPC(pkg);
            if (type != null) return type;
            
            // Is it an AutoCAD format?
            type = detectAutoCADOPC(pkg);
            
            return type;
        } catch (InvalidFormatException e) {
            //swallow
        }finally {
            tmp.close();
        }
        return null;
    }
    
    /**
     * Detects the type of an OfficeOpenXML (OOXML) file from
     *  opened Package 
     */
    public static MediaType detectOfficeOpenXML(OPCPackage pkg) {
        // Check for the normal Office core document
        PackageRelationshipCollection core = 
               pkg.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT);
        // Otherwise check for some other Office core document types
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(STRICT_CORE_DOCUMENT);
        }
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(VISIO_DOCUMENT);
        }
        
        // If we didn't find a single core document of any type, skip detection
        if (core.size() != 1) {
            // Invalid OOXML Package received
            return null;
        }

        // Get the type of the core document part
        PackagePart corePart = pkg.getPart(core.getRelationship(0));
        String coreType = corePart.getContentType();

        // Turn that into the type of the overall document
        String docType = coreType.substring(0, coreType.lastIndexOf('.'));

        // The Macro Enabled formats are a little special
        if(docType.toLowerCase(Locale.ROOT).endsWith("macroenabled")) {
            docType = docType.toLowerCase(Locale.ROOT) + ".12";
        }

        if(docType.toLowerCase(Locale.ROOT).endsWith("macroenabledtemplate")) {
            docType = MACRO_TEMPLATE_PATTERN.matcher(docType).replaceAll("macroenabled.12");
        }

        // Build the MediaType object and return
        return MediaType.parse(docType);
    }
    /**
     * Detects Open XML Paper Specification (XPS)
     */
    private static MediaType detectXPSOPC(OPCPackage pkg) {
        PackageRelationshipCollection xps = 
                pkg.getRelationshipsByType("http://schemas.microsoft.com/xps/2005/06/fixedrepresentation");
        if (xps.size() == 1) {
            return MediaType.application("vnd.ms-xpsdocument");
        } else {
            // Non-XPS Package received
            return null;
        }
    }
    /**
     * Detects AutoCAD formats that live in OPC packaging
     */
    private static MediaType detectAutoCADOPC(OPCPackage pkg) {
        PackageRelationshipCollection dwfxSeq = 
                pkg.getRelationshipsByType("http://schemas.autodesk.com/dwfx/2007/relationships/documentsequence");
        if (dwfxSeq.size() == 1) {
            return MediaType.parse("model/vnd.dwfx+xps");
        } else {
            // Non-AutoCAD Package received
            return null;
        }
    }

}
