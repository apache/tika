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
package org.apache.tika.parser.microsoft.ooxml;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HeaderFooter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.helpers.HeaderFooterHelper;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PageAnchoring;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.TikaExcelDataFormatter;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

public class XSSFExcelExtractorDecorator extends AbstractOOXMLExtractor {

    // Relationship types for external data sources
    private static final String EXTERNAL_LINK_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink";
    private static final String CONNECTIONS_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/connections";
    private static final String QUERY_TABLE_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/queryTable";
    private static final String PIVOT_CACHE_DEFINITION_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotCacheDefinition";
    // Power Query stores data in customData parts
    private static final String POWER_QUERY_CONTENT_TYPE =
            "application/vnd.ms-excel.customDataProperties+xml";
    private static final String RELATION_DRAWING =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing";
    private static final String RELATION_CHART =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart";
    private static final String RELATION_HYPERLINK =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";
    private static final String NS_DRAWING_ML =
            "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String NS_RELATIONSHIPS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String RELATION_VML_DRAWING =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/vmlDrawing";
    private static final String RELATION_COMMENTS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments";

    /**
     * Allows access to headers/footers from raw xml strings
     */
    protected static HeaderFooterHelper hfHelper = new HeaderFooterHelper();
    protected final DataFormatter formatter;
    protected final List<PackagePart> sheetParts = new ArrayList<>();
    /**
     * Pre-pass index of embedded-image absolute part name (e.g.
     * {@code /xl/media/image1.png}) → set of 1-based sheet numbers
     * referencing that image.  In XLSX, sheets reference images
     * indirectly via drawing parts (sheet → drawing → image), so the
     * pre-pass walks both hops.  Populated by
     * {@link #getMainDocumentParts()} so that
     * {@link #applyEmbeddedAnchorMetadata} can answer per-target
     * lookups even after {@code AbstractOOXMLExtractor.handleEmbeddedParts}
     * has deduped on second-and-later references.
     */
    private final Map<String, Set<Integer>> picturePages = new HashMap<>();
    protected final Map<String, String> drawingHyperlinks = new HashMap<>();
    protected Metadata metadata;
    protected ParseContext parseContext;

    public XSSFExcelExtractorDecorator(ParseContext context, OPCPackage pkg,
                                       Locale locale) {
        super(context, pkg);

        this.parseContext = context;

        if (locale == null) {
            formatter = new TikaExcelDataFormatter();
        } else {
            formatter = new TikaExcelDataFormatter(locale);
        }
        OfficeParserConfig officeParserConfig = context.get(OfficeParserConfig.class);
        if (officeParserConfig != null) {
            ((TikaExcelDataFormatter) formatter)
                    .setDateFormatOverride(officeParserConfig.getDateFormatOverride());
        }
    }

    @Override
    public MetadataExtractor getMetadataExtractor() {
        return new SAXBasedMetadataExtractor(opcPackage, parseContext);
    }

    @Override
    public void getXHTML(ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, IOException, TikaException {

        this.metadata = metadata;
        this.parseContext = context;
        metadata.set(Office.PROTECTED_WORKSHEET, "false");

        super.getXHTML(handler, metadata, context);
    }

    /**
     * @see org.apache.poi.xssf.extractor.XSSFExcelExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        OPCPackage container = opcPackage;

        XSSFSharedStringsShim stringsShim = null;
        XSSFReader.SheetIterator iter;
        XSSFReader xssfReader;
        XSSFStylesShim stylesShim = null;
        try {
            xssfReader = new XSSFReader(container);
            iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
        } catch (OpenXML4JException | RuntimeException e) {
            throw new IOException(e);
        }
        // Styles and shared strings are optional — if either part is missing or
        // unreadable, log to metadata and continue with degraded extraction.
        try {
            stylesShim = new XSSFStylesShim(xssfReader.getStylesData(), parseContext);
        } catch (Exception e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }
        try {
            stringsShim = new XSSFSharedStringsShim(xssfReader.getSharedStringsData(),
                    config.isConcatenatePhoneticRuns(), parseContext);
        } catch (Exception e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }
        while (true) {
            try {
                if (!iter.hasNext()) {
                    break;
                }
            } catch (RuntimeException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                break;
            }
            SheetTextAsHTML sheetExtractor = new SheetTextAsHTML(config, xhtml);
            PackagePart sheetPart = null;
            InputStream nextStream;
            try {
                nextStream = iter.next();
            } catch (RuntimeException e) {
                // POI can throw POIXMLException for missing sheet parts (e.g.,
                // truncated workbook references a sheet that isn't in the zip).
                // Break rather than continue — POI's iterator state may not have
                // advanced, which would cause an infinite loop.
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                break;
            }
            try (InputStream stream = nextStream) {
                sheetPart = iter.getSheetPart();

                addDrawingHyperLinks(sheetPart);
                sheetParts.add(sheetPart);

                XSSFCommentsShim commentsShim = parseSheetComments(sheetPart);
                if (commentsShim != null && commentsShim.getNumberOfComments() > 0) {
                    metadata.set(Office.HAS_COMMENTS, true);
                }

                // Start, and output the sheet name
                xhtml.startElement("div", "class", "sheet");
                xhtml.element("h1", iter.getSheetName());

                // Extract the main sheet contents
                xhtml.startElement("table");
                xhtml.startElement("tbody");

                try {
                    processSheet(sheetExtractor, commentsShim, stylesShim, stringsShim, stream);
                } catch (SAXException e) {
                    // Truncated/malformed sheet XML — keep prior sheets and
                    // record the failure as a warning.
                    WriteLimitReachedException.throwIfWriteLimitReached(e);
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            ExceptionUtils.getStackTrace(e));
                    // Balance any <tr>/<td> left open by the partial parse so
                    // the </tbody></table></div> emitted below land in the
                    // right place.
                    sheetExtractor.closeAnyPending();
                }
                try {
                    getThreadedComments(container, sheetPart, xhtml);
                } catch (InvalidFormatException | TikaException | IOException e) {
                    //swallow
                }
                xhtml.endElement("tbody");
                xhtml.endElement("table");
            }

            // Output any headers and footers
            // (Need to process the sheet to get them, so we can't
            //  do the headers before the contents)
            for (String header : sheetExtractor.headers) {
                extractHeaderFooter(header, xhtml);
            }
            for (String footer : sheetExtractor.footers) {
                extractHeaderFooter(footer, xhtml);
            }

            // Do text held in shapes, if required
            if (config.isIncludeShapeBasedContent()) {
                processDrawings(sheetPart, xhtml);
            }

            //for now dump sheet hyperlinks at bottom of page
            //consider a double-pass of the inputstream to reunite hyperlinks with cells/textboxes
            //step 1: extract hyperlink info from bottom of page
            //step 2: process as we do now, but with cached hyperlink relationship info
            extractHyperLinks(sheetPart, xhtml);
            // All done with this sheet
            xhtml.endElement("div");
        }

        //consider adding this back to POI
        try (InputStream wbData = xssfReader.getWorkbookData()) {
            XMLReaderUtils
                    .parseSAX(wbData, new WorkbookMetadataHandler(),
                            parseContext);
        } catch (InvalidFormatException | TikaException e) {
            //swallow
        }
        try {
            getPersons(container, metadata);
        } catch (InvalidFormatException | TikaException | IOException | SAXException e) {
            //swallow
        }

        // Extract external data sources (HIGH security risk - can hide malicious URLs)
        try {
            extractExternalDataSources(container, xhtml);
        } catch (InvalidFormatException | TikaException | IOException | SAXException e) {
            //swallow
        }

    }

    /**
     * Extracts external data sources from the workbook including:
     * - External workbook links
     * - Data connections (database, web queries)
     * - Query tables
     */
    private void extractExternalDataSources(OPCPackage container, XHTMLContentHandler xhtml)
            throws InvalidFormatException, TikaException, IOException, SAXException {

        PackageRelationship coreDocRelationship = container.getRelationshipsByType(
                PackageRelationshipTypes.CORE_DOCUMENT).getRelationship(0);
        if (coreDocRelationship == null) {
            return;
        }
        PackagePart workbookPart = container.getPart(coreDocRelationship);
        if (workbookPart == null) {
            return;
        }

        // Extract external workbook links
        extractExternalLinks(workbookPart, xhtml);

        // Extract connections (database, ODBC, web queries)
        extractConnections(workbookPart, xhtml);

        // Extract query tables from each sheet
        for (PackagePart sheetPart : sheetParts) {
            extractQueryTables(sheetPart, xhtml);
        }

        // Detect pivot cache with external data sources
        extractPivotCacheExternalData(workbookPart, xhtml);

        // Detect Power Query / Data Mashup
        detectPowerQuery(container);
    }

    /**
     * Detects pivot cache definitions with external data sources (OLAP, databases).
     */
    private void extractPivotCacheExternalData(PackagePart workbookPart, XHTMLContentHandler xhtml)
            throws InvalidFormatException {
        PackageRelationshipCollection coll = workbookPart.getRelationshipsByType(PIVOT_CACHE_DEFINITION_RELATION);
        if (coll == null || coll.isEmpty()) {
            return;
        }
        for (PackageRelationship rel : coll) {
            try {
                PackagePart pivotCachePart = workbookPart.getRelatedPart(rel);
                if (pivotCachePart != null) {
                    PivotCacheHandler handler = new PivotCacheHandler(xhtml);
                    try (InputStream is = pivotCachePart.getInputStream()) {
                        XMLReaderUtils.parseSAX(is, handler, parseContext);
                    }
                    if (handler.hasExternalData()) {
                        metadata.set(Office.HAS_EXTERNAL_PIVOT_DATA, true);
                    }
                }
            } catch (IOException | TikaException | SAXException | IllegalArgumentException e) {
                // swallow -- POI throws IllegalArgumentException when a
                // relationship references a part missing from the package
                // (e.g. truncated files)
            }
        }
    }

    /**
     * Detects Power Query / Data Mashup presence.
     */
    private void detectPowerQuery(OPCPackage container) {
        // Power Query data is stored in customData parts with specific content type
        // or in xl/customData/ folder
        try {
            List<PackagePart> customDataParts = container.getPartsByContentType(POWER_QUERY_CONTENT_TYPE);
            if (customDataParts != null && !customDataParts.isEmpty()) {
                metadata.set(Office.HAS_POWER_QUERY, true);
            }
            // Also check for customData folder parts
            for (PackagePart part : container.getParts()) {
                String partName = part.getPartName().getName();
                if (partName.contains("/customData/") || partName.contains("/dataMashup")) {
                    metadata.set(Office.HAS_POWER_QUERY, true);
                    break;
                }
            }
        } catch (InvalidFormatException e) {
            // swallow
        }
    }

    /**
     * Extracts external workbook links from externalLink parts.
     */
    private void extractExternalLinks(PackagePart workbookPart, XHTMLContentHandler xhtml)
            throws InvalidFormatException, SAXException {
        PackageRelationshipCollection coll = workbookPart.getRelationshipsByType(EXTERNAL_LINK_RELATION);
        if (coll == null || coll.isEmpty()) {
            return;
        }
        // If we have any external link relationships, set the metadata flag
        if (coll.size() > 0) {
            metadata.set(Office.HAS_EXTERNAL_LINKS, true);
        }
        for (PackageRelationship rel : coll) {
            if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                // Direct external reference
                emitExternalRef(xhtml, "externalLink", rel.getTargetURI().toString());
            } else {
                // Internal part that contains external reference - parse it
                try {
                    PackagePart externalLinkPart = workbookPart.getRelatedPart(rel);
                    if (externalLinkPart != null) {
                        ExternalLinkHandler handler = new ExternalLinkHandler(xhtml);
                        try (InputStream is = externalLinkPart.getInputStream()) {
                            XMLReaderUtils.parseSAX(is, handler, parseContext);
                        }
                        if (handler.hasDdeLink()) {
                            metadata.set(Office.HAS_DDE_LINKS, true);
                        }
                    }
                } catch (IOException | TikaException | IllegalArgumentException e) {
                    // swallow -- POI can throw IllegalArgumentException
                    // for malformed relationships
                }
            }
        }
    }

    /**
     * Extracts data connections from connections.xml.
     */
    private void extractConnections(PackagePart workbookPart, XHTMLContentHandler xhtml)
            throws InvalidFormatException, SAXException {
        PackageRelationshipCollection coll = workbookPart.getRelationshipsByType(CONNECTIONS_RELATION);
        if (coll == null || coll.isEmpty()) {
            return;
        }
        for (PackageRelationship rel : coll) {
            try {
                PackagePart connectionsPart = workbookPart.getRelatedPart(rel);
                if (connectionsPart != null) {
                    ConnectionsHandler handler = new ConnectionsHandler(xhtml);
                    try (InputStream is = connectionsPart.getInputStream()) {
                        XMLReaderUtils.parseSAX(is, handler, parseContext);
                    }
                    if (handler.hasConnections()) {
                        metadata.set(Office.HAS_DATA_CONNECTIONS, true);
                    }
                    if (handler.hasWebQueries()) {
                        metadata.set(Office.HAS_WEB_QUERIES, true);
                    }
                }
            } catch (IOException | TikaException | IllegalArgumentException e) {
                // swallow -- POI throws IllegalArgumentException when a
                // relationship references a part missing from the package
                // (e.g. truncated files)
            }
        }
    }

    /**
     * Extracts query table external sources.
     */
    private void extractQueryTables(PackagePart sheetPart, XHTMLContentHandler xhtml)
            throws InvalidFormatException, SAXException {
        PackageRelationshipCollection coll = sheetPart.getRelationshipsByType(QUERY_TABLE_RELATION);
        if (coll == null || coll.isEmpty()) {
            return;
        }
        for (PackageRelationship rel : coll) {
            try {
                PackagePart queryTablePart = sheetPart.getRelatedPart(rel);
                if (queryTablePart != null) {
                    try (InputStream is = queryTablePart.getInputStream()) {
                        XMLReaderUtils.parseSAX(is, new QueryTableHandler(xhtml), parseContext);
                    }
                }
            } catch (IOException | TikaException | IllegalArgumentException e) {
                // swallow -- POI throws IllegalArgumentException when a
                // relationship references a part missing from the package
                // (e.g. truncated files)
            }
        }
    }

    /**
     * Emits an external reference as an anchor element with appropriate class.
     */
    private void emitExternalRef(XHTMLContentHandler xhtml, String refType, String url)
            throws SAXException {
        if (url == null || url.isEmpty()) {
            return;
        }
        org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "external-ref-" + refType);
        attrs.addAttribute("", "href", "href", "CDATA", url);
        xhtml.startElement("a", attrs);
        xhtml.endElement("a");
    }

    /**
     * Handler for parsing externalLink XML to extract external workbook references.
     */
    private class ExternalLinkHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        private boolean foundDdeLink = false;

        ExternalLinkHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            // Look for externalBook element with r:id attribute
            if ("externalBook".equals(localName)) {
                String rId = atts.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                // The actual URL is in the relationship, not directly in the XML
                // For now, we note that there's an external book reference
            }
            // Look for file element with href attribute (older format)
            if ("file".equals(localName)) {
                String href = atts.getValue("href");
                if (href != null && !href.isEmpty()) {
                    emitExternalRef(xhtml, "externalWorkbook", href);
                }
            }
            // Look for oleLink with r:id (OLE links to external files)
            if ("oleLink".equals(localName)) {
                String rId = atts.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                if (rId != null) {
                    emitExternalRef(xhtml, "oleLink", "relationship:" + rId);
                }
            }
            // DDE links - security risk: can execute commands
            if ("ddeLink".equals(localName)) {
                foundDdeLink = true;
                String ddeService = atts.getValue("ddeService");
                String ddeTopic = atts.getValue("ddeTopic");
                if (ddeService != null || ddeTopic != null) {
                    String ddeRef = (ddeService != null ? ddeService : "") + "|" +
                            (ddeTopic != null ? ddeTopic : "");
                    emitExternalRef(xhtml, "ddeLink", ddeRef);
                }
            }
        }

        boolean hasDdeLink() {
            return foundDdeLink;
        }
    }

    /**
     * Handler for parsing connections.xml to extract external data connections.
     */
    private class ConnectionsHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        private boolean foundConnection = false;
        private boolean foundWebQuery = false;

        ConnectionsHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if ("connection".equals(localName)) {
                foundConnection = true;
            }
            // Database connection string
            if ("dbPr".equals(localName)) {
                String connection = atts.getValue("connection");
                if (connection != null && !connection.isEmpty()) {
                    emitExternalRef(xhtml, "dbConnection", connection);
                }
            }
            // Web query
            if ("webPr".equals(localName)) {
                foundWebQuery = true;
                String url = atts.getValue("url");
                if (url != null && !url.isEmpty()) {
                    emitExternalRef(xhtml, "webQuery", url);
                }
            }
            // ODBC connection
            if ("olapPr".equals(localName)) {
                String connection = atts.getValue("connection");
                if (connection != null && !connection.isEmpty()) {
                    emitExternalRef(xhtml, "olapConnection", connection);
                }
            }
            // Text file import
            if ("textPr".equals(localName)) {
                String sourceFile = atts.getValue("sourceFile");
                if (sourceFile != null && !sourceFile.isEmpty()) {
                    emitExternalRef(xhtml, "textFileImport", sourceFile);
                }
            }
        }

        boolean hasConnections() {
            return foundConnection;
        }

        boolean hasWebQueries() {
            return foundWebQuery;
        }
    }

    /**
     * Handler for parsing queryTable XML to extract web query sources.
     */
    private class QueryTableHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;

        QueryTableHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if ("queryTable".equals(localName)) {
                String connectionId = atts.getValue("connectionId");
                // Connection details are in connections.xml
            }
            // Web query table refresh
            if ("queryTableRefresh".equals(localName)) {
                // Contains refresh settings
            }
        }
    }

    /**
     * Handler for parsing pivotCacheDefinition XML to detect external data sources.
     */
    private class PivotCacheHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        private boolean hasExternalData = false;

        PivotCacheHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            // cacheSource with type="external" indicates external data
            if ("cacheSource".equals(localName)) {
                String type = atts.getValue("type");
                if ("external".equals(type) || "consolidation".equals(type)) {
                    hasExternalData = true;
                }
            }
            // worksheetSource can have external references
            if ("worksheetSource".equals(localName)) {
                String ref = atts.getValue("ref");
                String sheet = atts.getValue("sheet");
                String rId = atts.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                // If there's a relationship ID, it likely points to external workbook
                if (rId != null) {
                    hasExternalData = true;
                }
            }
            // consolidation source (multiple ranges, possibly external)
            if ("consolidation".equals(localName) || "rangeSets".equals(localName)) {
                hasExternalData = true;
            }
        }

        boolean hasExternalData() {
            return hasExternalData;
        }
    }

    private void getThreadedComments(OPCPackage container, PackagePart sheetPart, XHTMLContentHandler xhtml) throws TikaException,
            InvalidFormatException, SAXException, IOException {
        //consider caching the person id -> person names in getPersons and injecting that into the xhtml per comment?
        PackageRelationshipCollection coll = sheetPart.getRelationshipsByType(OPCPackageWrapper.THREADED_COMMENT_RELATION);
        if (coll == null || coll.isEmpty()) {
            return;
        }
        for (PackageRelationship rel : coll) {
            PackagePart threadedCommentPart = sheetPart.getRelatedPart(rel);
            if (threadedCommentPart == null) {
                continue;
            }
            try (InputStream is = threadedCommentPart.getInputStream()) {
                XMLReaderUtils.parseSAX(is, new ThreadedCommentHandler(xhtml), parseContext);
            }
        }
    }

    private void getPersons(OPCPackage container, Metadata metadata) throws TikaException, InvalidFormatException,
            IOException, SAXException {
        PackageRelationship coreDocRelationship = container.getRelationshipsByType(
                PackageRelationshipTypes.CORE_DOCUMENT).getRelationship(0);
        if (coreDocRelationship == null) {
            return;
        }
        // Get the part that holds the workbook
        PackagePart workbookPart = container.getPart(coreDocRelationship);
        if (workbookPart == null) {
            return;
        }
        PackageRelationshipCollection coll = workbookPart.getRelationshipsByType(OPCPackageWrapper.PERSON_RELATION);
        if (coll == null) {
            return;
        }
        for (PackageRelationship rel : coll) {
            PackagePart personsPart = workbookPart.getRelatedPart(rel);
            if (personsPart == null) {
                continue;
            }
            try (InputStream is = personsPart.getInputStream()) {
                XMLReaderUtils.parseSAX(is, new CommentPersonHandler(metadata), parseContext);
            }
        }
    }

    protected void addDrawingHyperLinks(PackagePart sheetPart) {
        try {
            for (PackageRelationship rel : sheetPart
                    .getRelationshipsByType(RELATION_DRAWING)) {
                if (rel.getTargetMode() == TargetMode.INTERNAL) {
                    PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                    PackagePart part = rel.getPackage().getPart(relName);
                    //parts can go missing, and Excel quietly ignores missing images -- TIKA-2134
                    if (part == null) {
                        continue;
                    }
                    for (PackageRelationship drawRel : part
                            .getRelationshipsByType(RELATION_HYPERLINK)) {
                        drawingHyperlinks.put(drawRel.getId(), drawRel.getTargetURI().toString());
                    }
                }
            }
        } catch (InvalidFormatException e) {
            //swallow
            //an exception trying to extract
            //hyperlinks on drawings should not cause a parse failure
        }

    }


    protected void extractHyperLinks(PackagePart sheetPart, XHTMLContentHandler xhtml)
            throws SAXException {
        try {
            boolean first = true;
            for (PackageRelationship rel : sheetPart
                    .getRelationshipsByType(RELATION_HYPERLINK)) {
                if (!first) {
                    xhtml.characters(" ");
                }
                first = false;
                xhtml.startElement("a", "href", rel.getTargetURI().toString());
                xhtml.characters(rel.getTargetURI().toString());
                xhtml.endElement("a");
            }
        } catch (InvalidFormatException e) {
            //swallow
        }
    }

    protected void extractHeaderFooter(String hf, XHTMLContentHandler xhtml) throws SAXException {
        String content = ExcelExtractor._extractHeaderFooter(new HeaderFooterFromString(hf));
        if (content.length() > 0) {
            xhtml.element("p", content);
        }
    }

    protected void processDrawings(PackagePart sheetPart, XHTMLContentHandler xhtml)
            throws SAXException {
        try {
            for (PackageRelationship rel : sheetPart
                    .getRelationshipsByType(RELATION_DRAWING)) {
                if (rel.getTargetMode() != TargetMode.INTERNAL) {
                    continue;
                }
                PackagePartName relName =
                        PackagingURIHelper.createPartName(rel.getTargetURI());
                PackagePart drawingPart = rel.getPackage().getPart(relName);
                if (drawingPart == null) {
                    continue;
                }
                // SAX-parse drawing XML for shape text and hyperlinks
                try (InputStream is = drawingPart.getInputStream()) {
                    XMLReaderUtils.parseSAX(is,
                            new DrawingShapeHandler(xhtml, drawingHyperlinks),
                            parseContext);
                } catch (IOException | TikaException e) {
                    //swallow
                }
                // Process diagram and chart data through drawing part relationships
                handleGeneralTextContainingPart(
                        AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                        "diagram-data", drawingPart, metadata,
                        new OOXMLWordAndPowerPointTextHandler(
                                new OOXMLTikaBodyPartHandler(xhtml),
                                new HashMap<>()));
                handleGeneralTextContainingPart(RELATION_CHART, "chart",
                        drawingPart, metadata,
                        new OOXMLWordAndPowerPointTextHandler(
                                new OOXMLTikaBodyPartHandler(xhtml),
                                new HashMap<>()));
            }
        } catch (InvalidFormatException e) {
            //swallow
        }
    }

    /**
     * SAX handler for drawing XML that extracts shape text and hyperlinks
     * without requiring XMLBeans or the POI usermodel (XSSFShape, etc.).
     */
    private static class DrawingShapeHandler extends DefaultHandler {

        private final XHTMLContentHandler xhtml;
        private final Map<String, String> hyperlinks;

        private boolean inTxBody;
        private boolean inT;
        private final StringBuilder textBuffer = new StringBuilder();
        private final StringBuilder shapeText = new StringBuilder();

        DrawingShapeHandler(XHTMLContentHandler xhtml, Map<String, String> hyperlinks) {
            this.xhtml = xhtml;
            this.hyperlinks = hyperlinks;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) throws SAXException {
            if ("txBody".equals(localName)) {
                inTxBody = true;
                shapeText.setLength(0);
            } else if ("t".equals(localName) && inTxBody) {
                inT = true;
                textBuffer.setLength(0);
            } else if ("hlinkClick".equals(localName) || "hlinkHover".equals(localName)) {
                String rId = atts.getValue(NS_RELATIONSHIPS, "id");
                if (rId == null) {
                    // try non-namespace-aware fallback
                    rId = atts.getValue("r:id");
                }
                if (rId != null) {
                    String url = hyperlinks.get(rId);
                    if (url != null) {
                        xhtml.startElement("a", "href", url);
                        xhtml.characters(url);
                        xhtml.endElement("a");
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if ("t".equals(localName) && inT) {
                inT = false;
                shapeText.append(textBuffer);
            } else if ("p".equals(localName) && inTxBody &&
                    shapeText.length() > 0) {
                shapeText.append('\n');
            } else if ("txBody".equals(localName)) {
                inTxBody = false;
                String text = shapeText.toString().trim();
                if (!text.isEmpty()) {
                    xhtml.element("p", text);
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inT) {
                textBuffer.append(ch, start, length);
            }
        }
    }

    public void processSheet(TikaSheetContentsHandler sheetContentsHandler,
                             XSSFCommentsShim commentsShim,
                             XSSFStylesShim stylesShim, XSSFSharedStringsShim stringsShim,
                             InputStream sheetInputStream) throws IOException, SAXException {
        try {
            XSSFSheetInterestingPartsCapturer handler = new XSSFSheetInterestingPartsCapturer(
                    new TikaSheetXMLHandler(stylesShim, commentsShim, stringsShim,
                            sheetContentsHandler, formatter, false));
            XMLReaderUtils.parseSAX(sheetInputStream, handler, parseContext);
            sheetInputStream.close();

            if (handler.hasProtection) {
                metadata.set(Office.PROTECTED_WORKSHEET, true);
            }
            if (handler.hasHiddenColumn) {
                metadata.set(Office.HAS_HIDDEN_COLUMNS, true);
            }
            if (handler.hasHiddenRow) {
                metadata.set(Office.HAS_HIDDEN_ROWS, true);
            }
        } catch (TikaException e) {
            throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
        }
    }

    /**
     * Parse the comments XML for a sheet part via SAX, avoiding XMLBeans.
     */
    private XSSFCommentsShim parseSheetComments(PackagePart sheetPart) {
        try {
            PackageRelationshipCollection rels =
                    sheetPart.getRelationshipsByType(RELATION_COMMENTS);
            if (rels.isEmpty()) {
                return null;
            }
            PackageRelationship rel = rels.getRelationship(0);
            PackagePartName partName =
                    PackagingURIHelper.createPartName(rel.getTargetURI());
            PackagePart commentsPart = rel.getPackage().getPart(partName);
            if (commentsPart == null) {
                return null;
            }
            try (InputStream is = commentsPart.getInputStream()) {
                return new XSSFCommentsShim(is, parseContext);
            }
        } catch (InvalidFormatException | IOException | TikaException | SAXException e) {
            //swallow — comments are not critical
            return null;
        }
    }


    /**
     * In Excel files, sheets have things embedded in them,
     * and sheet drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
        List<PackagePart> parts = new ArrayList<>();
        // The sheet order in sheetParts mirrors the workbook's sheet
        // ordering (populated in buildXHTML), so the index here is the
        // 1-based sheet number.
        int sheetNumber = 0;
        for (PackagePart part : sheetParts) {
            sheetNumber++;
            // Add the sheet
            parts.add(part);

            // If it has drawings, return those too
            try {
                for (PackageRelationship rel : part
                        .getRelationshipsByType(RELATION_DRAWING)) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName =
                                PackagingURIHelper.createPartName(rel.getTargetURI());
                        PackagePart drawingPart = rel.getPackage().getPart(relName);
                        parts.add(drawingPart);
                        recordImagesOnSheet(drawingPart, sheetNumber);
                    }
                }
                for (PackageRelationship rel : part
                        .getRelationshipsByType(RELATION_VML_DRAWING)) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName =
                                PackagingURIHelper.createPartName(rel.getTargetURI());
                        PackagePart vmlPart = rel.getPackage().getPart(relName);
                        parts.add(vmlPart);
                        recordImagesOnSheet(vmlPart, sheetNumber);
                    }
                }
            } catch (InvalidFormatException e) {
                throw new TikaException("Broken OOXML file", e);
            }
        }

        //add main document so that macros can be extracted
        //by AbstractOOXMLExtractor
        parts.addAll(opcPackage
                .getPartsByRelationshipType(PackageRelationshipTypes.CORE_DOCUMENT));

        return parts;
    }

    /**
     * Turns formatted sheet events into HTML
     */
    protected static class SheetTextAsHTML
            implements TikaSheetContentsHandler, SheetContentsHandler {
        private final boolean includeHeadersFooters;
        private final boolean includeMissingRows;
        protected List<String> headers;
        protected List<String> footers;
        private XHTMLContentHandler xhtml;
        private int lastSeenRow = -1;
        private int lastSeenCol = -1;
        // Track open <tr>/<td> so the outer catch can emit balanced closes
        // when processSheet throws part-way through a row (e.g., a malformed
        // sheet XML). Without this, the outer code would emit </tbody></table>
        // while <tr> (or <td>) was still on the stack, producing malformed XHTML.
        private boolean rowOpen;
        private boolean cellOpen;

        protected SheetTextAsHTML(OfficeParserConfig config, XHTMLContentHandler xhtml) {
            this.includeHeadersFooters = config.isIncludeHeadersAndFooters();
            this.includeMissingRows = config.isIncludeMissingRows();
            this.xhtml = xhtml;
            headers = new ArrayList<>();
            footers = new ArrayList<>();
        }

        public void startRow(int rowNum) {
            try {
                // Missing rows, if desired, with a single empty row
                if (includeMissingRows && rowNum > (lastSeenRow + 1)) {
                    for (int rn = lastSeenRow + 1; rn < rowNum; rn++) {
                        xhtml.startElement("tr");
                        rowOpen = true;
                        xhtml.startElement("td");
                        cellOpen = true;
                        xhtml.endElement("td");
                        cellOpen = false;
                        xhtml.endElement("tr");
                        rowOpen = false;
                    }
                }

                // Start the new row
                xhtml.startElement("tr");
                rowOpen = true;
                lastSeenCol = -1;
            } catch (SAXException e) {
                //swallow
                throw new RuntimeSAXException(e);
            }

        }

        public void endRow(int rowNum) {
            try {
                xhtml.endElement("tr");
                rowOpen = false;
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        /**
         * Closes any pending {@code <tr>} or {@code <td>} that was opened
         * before a {@link SAXException} interrupted sheet processing. Safe to
         * call when nothing is open.
         */
        void closeAnyPending() throws SAXException {
            if (cellOpen) {
                xhtml.endElement("td");
                cellOpen = false;
            }
            if (rowOpen) {
                xhtml.endElement("tr");
                rowOpen = false;
            }
        }

        public void cell(String cellRef, String formattedValue,
                          XSSFCommentsShim.CommentData comment) {
            try {
                // Handle any missing cells
                int colNum =
                        (cellRef == null) ? lastSeenCol + 1 : (new CellReference(cellRef)).getCol();
                for (int cn = lastSeenCol + 1; cn < colNum; cn++) {
                    xhtml.startElement("td");
                    cellOpen = true;
                    xhtml.endElement("td");
                    cellOpen = false;
                }
                lastSeenCol = colNum;

                // Start this cell
                xhtml.startElement("td");
                cellOpen = true;

                // Main cell contents
                if (formattedValue != null) {
                    xhtml.characters(formattedValue);
                }

                // Comments
                if (comment != null) {
                    xhtml.startElement("br");
                    xhtml.endElement("br");
                    xhtml.characters(comment.getAuthor());
                    xhtml.characters(": ");
                    xhtml.characters(comment.getText());
                }

                xhtml.endElement("td");
                cellOpen = false;
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        /**
         * Bridge for POI's {@link SheetContentsHandler} interface, used by the
         * XLSB (binary) path via {@link org.apache.poi.xssf.binary.XSSFBSheetHandler}.
         */
        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
            XSSFCommentsShim.CommentData commentData = null;
            if (comment != null) {
                String text = comment.getString() != null ?
                        comment.getString().getString() : "";
                commentData = new XSSFCommentsShim.CommentData(
                        comment.getAuthor(), text);
            }
            cell(cellRef, formattedValue, commentData);
        }

        public void headerFooter(String text, boolean isHeader, String tagName) {
            if (!includeHeadersFooters) {
                return;
            }
            if (isHeader) {
                headers.add(text);
            } else {
                footers.add(text);
            }
        }

        @Override
        public void endSheet() {
            // no-op — satisfies both TikaSheetContentsHandler and SheetContentsHandler
        }
    }

    protected static class HeaderFooterFromString implements HeaderFooter {
        private String text;

        protected HeaderFooterFromString(String text) {
            this.text = text;
        }

        public String getCenter() {
            return hfHelper.getCenterSection(text);
        }

        public void setCenter(String paramString) {
        }

        public String getLeft() {
            return hfHelper.getLeftSection(text);
        }

        public void setLeft(String paramString) {
        }

        public String getRight() {
            return hfHelper.getRightSection(text);
        }

        public void setRight(String paramString) {
        }
    }

    /**
     * Captures information on interesting tags, whilst
     * delegating the main work to the formatting handler
     */
    protected static class XSSFSheetInterestingPartsCapturer extends DefaultHandler {
        private ContentHandler delegate;
        private boolean hasProtection = false;
        private boolean hasHiddenRow = false;
        private boolean hasHiddenColumn = false;

        protected XSSFSheetInterestingPartsCapturer(ContentHandler delegate) {
            this.delegate = delegate;
        }

        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if ("sheetProtection".equals(qName)) {
                hasProtection = true;
            }
            if (! hasHiddenRow && "row".equals(localName)) {
                String v = atts.getValue("hidden");
                if ("true".equals(v) || "1".equals(v)) {
                    hasHiddenRow = true;
                }
            }
            if (! hasHiddenColumn && "col".equals(localName)) {
                String v = atts.getValue("hidden");
                if ("true".equals(v) || "1".equals(v)) {
                    hasHiddenColumn = true;
                }
            }
            delegate.startElement(uri, localName, qName, atts);
        }

        public void characters(char[] ch, int start, int length) throws SAXException {
            delegate.characters(ch, start, length);
        }

        public void endDocument() throws SAXException {
            delegate.endDocument();
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
            delegate.endElement(uri, localName, qName);
        }

        public void endPrefixMapping(String prefix) throws SAXException {
            delegate.endPrefixMapping(prefix);
        }

        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            delegate.ignorableWhitespace(ch, start, length);
        }

        public void processingInstruction(String target, String data) throws SAXException {
            delegate.processingInstruction(target, data);
        }

        public void setDocumentLocator(Locator locator) {
            delegate.setDocumentLocator(locator);
        }

        public void skippedEntity(String name) throws SAXException {
            delegate.skippedEntity(name);
        }

        public void startDocument() throws SAXException {
            delegate.startDocument();
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            delegate.startPrefixMapping(prefix, uri);
        }
    }

    private class WorkbookMetadataHandler extends DefaultHandler {
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            //require x15ac //http://schemas.microsoft.com/office/spreadsheetml/2010/11/ac ???
            if ("absPath".equals(localName)) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String n = atts.getLocalName(i);
                    if ("url".equals(n)) {
                        String url = atts.getValue(i);
                        metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, url);
                        return;
                    }
                }
            } else if ("sheet".equals(localName)) {
                String n = XMLReaderUtils.getAttrValue("name", atts);
                String state = XMLReaderUtils.getAttrValue("state", atts);
                if ("hidden".equals(state)) {
                    metadata.set(Office.HAS_HIDDEN_SHEETS, true);
                    metadata.add(Office.HIDDEN_SHEET_NAMES, n);
                } else if ("veryHidden".equals(state)) {
                    metadata.set(Office.HAS_VERY_HIDDEN_SHEETS, true);
                    metadata.set(Office.VERY_HIDDEN_SHEET_NAMES, n);
                }
            } else if ("workbookPr".equals(localName)) {
                String codeName = XMLReaderUtils.getAttrValue("codeName", atts);
                if (!StringUtils.isBlank(codeName)) {
                    metadata.set(Office.WORKBOOK_CODENAME, codeName);
                }
            }
            // file version? <fileVersion appName="xl" lastEdited="7" lowestEdited="7" rupBuild="28526"/>
        }
    }

    private static class ThreadedCommentHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        StringBuilder sb = new StringBuilder();
        boolean inText = false;
        public ThreadedCommentHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if ("text".equals(localName)) {
                inText = true;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("text".equals(localName)) {
                xhtml.startElement("div", "class", "threaded-comment");
                xhtml.startElement("p");
                xhtml.characters(sb.toString());
                xhtml.endElement("p");
                xhtml.endElement("div");
                sb.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inText) {
                sb.append(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            if (inText) {
                sb.append(ch, start, length);
            }
        }
    }

    /**
     * Records every image relationship of {@code drawingPart} against the
     * given 1-based {@code sheetNumber}.  Called once per drawing during
     * the pre-pass in {@link #getMainDocumentParts()}.  When the same
     * image is referenced from drawings on multiple sheets, all sheet
     * numbers end up in the set so {@link Office#SHEET_NUMBERS} ends up
     * multi-valued.  Keyed by absolute part name so the lookup matches
     * what {@link AbstractOOXMLExtractor#applyEmbeddedAnchorMetadata}
     * sees &mdash; relative target URIs across drawing parts collide
     * and are not stable lookup keys.
     */
    private void recordImagesOnSheet(PackagePart drawingPart, int sheetNumber) {
        if (drawingPart == null) {
            return;
        }
        PackageRelationshipCollection prc;
        try {
            prc = drawingPart.getRelationshipsByType(PackageRelationshipTypes.IMAGE_PART);
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
            return;
        }
        if (prc == null) {
            return;
        }
        for (PackageRelationship rel : prc) {
            if (rel.getTargetMode() != TargetMode.INTERNAL) {
                continue;
            }
            PackagePart imagePart;
            try {
                imagePart = drawingPart.getRelatedPart(rel);
            } catch (InvalidFormatException | IllegalArgumentException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                continue;
            }
            if (imagePart == null) {
                continue;
            }
            picturePages
                    .computeIfAbsent(imagePart.getPartName().getName(),
                            k -> new LinkedHashSet<>())
                    .add(sheetNumber);
        }
    }

    @Override
    protected void applyEmbeddedAnchorMetadata(PackagePart part, Metadata metadata) {
        PageAnchoring.applySheetMetadata(metadata,
                picturePages.get(part.getPartName().getName()));
    }
}
