package org.apache.tika.detect.microsoft.ooxml;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.openxml4j.util.ZipFileZipEntrySource;
import org.apache.tika.detect.zip.ZipDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

public class OPCPackageDetector implements ZipDetector {


    private static final Pattern MACRO_TEMPLATE_PATTERN = Pattern.compile("macroenabledtemplate$", Pattern.CASE_INSENSITIVE);


    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes
    private static final String VISIO_DOCUMENT =
            "http://schemas.microsoft.com/visio/2010/relationships/document";
    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes
    private static final String STRICT_CORE_DOCUMENT =
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";

    private static final String XPS_DOCUMENT =
            "http://schemas.microsoft.com/xps/2005/06/fixedrepresentation";

    private static final String STAR_OFFICE_6_WRITER = "application/vnd.sun.xml.writer";


    @Override
    public MediaType detect(ZipFile zipFile, TikaInputStream stream) throws IOException {
        //as of 4.x, POI throws an exception for non-POI OPC file types
        //unless we change POI, we can't rely on POI for non-POI files
        ZipEntrySource zipEntrySource = new ZipFileZipEntrySource(zipFile);

        // Use POI to open and investigate it for us
        //Unfortunately, POI can throw a RuntimeException...so we
        //have to catch that.
        OPCPackage pkg = null;
        MediaType type = null;
        try {
            pkg = OPCPackage.open(zipEntrySource);
            type = detectOfficeOpenXML(pkg);
        } catch (SecurityException e) {
            closeQuietly(zipEntrySource);
            closeQuietly(zipFile);
            //TIKA-2571
            throw e;
        } catch (InvalidFormatException | RuntimeException e) {
            closeQuietly(zipEntrySource);
            closeQuietly(zipFile);
            return null;
        }
        //only set the open container if we made it here
        stream.setOpenContainer(pkg);
        return type;
    }


    /**
     * Detects the type of an OfficeOpenXML (OOXML) file from
     * opened Package
     */
    private static MediaType detectOfficeOpenXML(OPCPackage pkg) {
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
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(XPS_DOCUMENT);
            if (core.size() == 1) {
                return MediaType.application("vnd.ms-xpsdocument");
            }
        }

        if (core.size() == 0) {
            core = pkg.getRelationshipsByType("http://schemas.autodesk.com/dwfx/2007/relationships/documentsequence");
            if (core.size() == 1) {
                return MediaType.parse("model/vnd.dwfx+xps");
            }
        }
        // If we didn't find a single core document of any type, skip detection
        if (core.size() != 1) {
            // Invalid OOXML Package received
            return null;
        }

        // Get the type of the core document part
        PackagePart corePart = pkg.getPart(core.getRelationship(0));
        String coreType = corePart.getContentType();

        if (coreType.contains(".xps")) {
            return MediaType.application("vnd.ms-package.xps");
        }
        // Turn that into the type of the overall document
        String docType = coreType.substring(0, coreType.lastIndexOf('.'));

        // The Macro Enabled formats are a little special
        if (docType.toLowerCase(Locale.ROOT).endsWith("macroenabled")) {
            docType = docType.toLowerCase(Locale.ROOT) + ".12";
        }

        if (docType.toLowerCase(Locale.ROOT).endsWith("macroenabledtemplate")) {
            docType = MACRO_TEMPLATE_PATTERN.matcher(docType).replaceAll("macroenabled.12");
        }

        // Build the MediaType object and return
        return MediaType.parse(docType);
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

    private static void closeQuietly(ZipFile zipFile) {
        if (zipFile == null) {
            return;
        }
        try {
            zipFile.close();
        } catch (IOException e) {

        }
    }

    private static void closeQuietly(ZipEntrySource zipEntrySource) {
        if (zipEntrySource == null) {
            return;
        }
        try {
            zipEntrySource.close();
        } catch (IOException e) {
            //swallow
        }
    }
}
