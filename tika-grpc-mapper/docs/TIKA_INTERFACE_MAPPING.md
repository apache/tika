# Tika Interface to Protobuf Mapping

## 🎯 Purpose
This document maps **actual** Tika metadata interfaces to our protobuf structures. Every interface listed here has been verified to exist in the Tika source code.

## 📋 Verified Tika Metadata Interfaces

### Core Document Interfaces
- ✅ `PDF.java` - PDF document properties
- ✅ `Office.java` - Basic Office document properties  
- ✅ `OfficeOpenXMLCore.java` - OOXML core properties
- ✅ `OfficeOpenXMLExtended.java` - OOXML extended properties
- ✅ `Message.java` - Email message properties
- ✅ `HTML.java` - HTML document properties
- ✅ `RTFMetadata.java` - RTF document properties
- ✅ `Database.java` - Database file properties
- ✅ `Font.java` - Font file properties
- ✅ `Epub.java` - EPUB book properties
- ✅ `WARC.java` - Web archive properties
- ✅ `ClimateForcast.java` - NetCDF/Climate data properties
- ✅ `CreativeCommons.java` - Creative Commons licensing

### Image and Media Interfaces
- ✅ `TIFF.java` - TIFF image properties
- ✅ `IPTC.java` - IPTC image metadata
- ✅ `Photoshop.java` - Photoshop-specific metadata
- ✅ `XMPDM.java` - XMP Digital Media properties

### XMP Interfaces
- ✅ `XMP.java` - XMP Basic Schema
- ✅ `XMPDC.java` - XMP Dublin Core
- ✅ `XMPMM.java` - XMP Media Management
- ✅ `XMPPDF.java` - XMP PDF properties
- ✅ `XMPRights.java` - XMP Rights Management
- ✅ `XMPIdq.java` - XMP Identifier Qualifier

### Specialized Interfaces
- ✅ `MAPI.java` - MAPI (Outlook) properties
- ✅ `PST.java` - PST file properties
- ✅ `QuattroPro.java` - QuattroPro spreadsheet
- ✅ `WordPerfect.java` - WordPerfect document
- ✅ `MachineMetadata.java` - Machine/system metadata
- ✅ `ExternalProcess.java` - External process metadata
- ✅ `Geographic.java` - Geographic/location data
- ✅ `AccessPermissions.java` - Document access permissions
- ✅ `FileSystem.java` - File system metadata
- ✅ `HttpHeaders.java` - HTTP header metadata
- ✅ `Rendering.java` - Rendering properties
- ✅ `PagedText.java` - Paged text properties
- ✅ `TikaPagedText.java` - Tika-specific paged text

### Core Framework
- ✅ `DublinCore.java` - Dublin Core standard
- ✅ `TikaCoreProperties.java` - Core Tika properties
- ✅ `TikaMimeKeys.java` - MIME type keys

---

## 🗺️ Interface to Protobuf Mapping

### 1. PDF Documents
**Tika Interfaces:**
- `PDF.java` - 50+ properties (DOC_INFO_*, PDF_VERSION, IS_ENCRYPTED, etc.)
- `XMPPDF.java` - XMP PDF-specific properties
- `AccessPermissions.java` - PDF security permissions

**Protobuf Target:**
- `io.pipeline.parsed.data.pdf.v1.PdfMetadata`

**Key Properties to Map:**
```java
// From PDF.java
PDF.DOC_INFO_TITLE → title
PDF.DOC_INFO_AUTHOR → author  
PDF.DOC_INFO_SUBJECT → subject
PDF.DOC_INFO_KEYWORDS → keywords
PDF.DOC_INFO_CREATOR → creator
PDF.DOC_INFO_PRODUCER → producer
PDF.DOC_INFO_CREATED → creation_date
PDF.DOC_INFO_MODIFICATION_DATE → modification_date
PDF.PDF_VERSION → pdf_version
PDF.PDFA_VERSION → pdfa_version
PDF.IS_ENCRYPTED → is_encrypted
PDF.HAS_XFA → has_xfa
PDF.HAS_ACROFORM_FIELDS → has_acroform_fields
PDF.HAS_MARKED_CONTENT → has_marked_content
PDF.HAS_COLLECTION → has_collection
PDF.HAS_3D → has_3d
// ... and 35+ more actual properties

// From XMPPDF.java (note: constant is KEY_WORDS in snapshot)
XMPPDF.KEY_WORDS → xmp_keywords (string)

// From AccessPermissions.java (booleans)
AccessPermissions.CAN_MODIFY → can_modify_document
AccessPermissions.EXTRACT_CONTENT → can_extract_content
AccessPermissions.EXTRACT_FOR_ACCESSIBILITY → can_extract_for_accessibility
AccessPermissions.ASSEMBLE_DOCUMENT → can_assemble_document
AccessPermissions.FILL_IN_FORM → can_fill_in_form
AccessPermissions.CAN_MODIFY_ANNOTATIONS → can_modify_annotations
AccessPermissions.CAN_PRINT → can_print
AccessPermissions.CAN_PRINT_FAITHFUL → can_print_faithful

// From PagedText.java
PagedText.N_PAGES → n_pages (int32)

// Literal keys from PDF parsers
"X-TIKA:pdf:metadata-xmp-parse-failed" → xmp_parse_failed (repeated string)
"pdf:foundNonAdobeExtensionName" → kept in additional_metadata
```

### 2. Office Documents  
**Tika Interfaces:**
- `Office.java` - Basic Office properties
- `OfficeOpenXMLCore.java` - OOXML core metadata
- `OfficeOpenXMLExtended.java` - OOXML extended metadata

**Protobuf Target:**
- `io.pipeline.parsed.data.office.v1.OfficeMetadata`

### 3. Image Documents
**Tika Interfaces:**
- `TIFF.java` - TIFF-specific properties
- `IPTC.java` - IPTC metadata standard
- `Photoshop.java` - Photoshop metadata
- `XMP.java` - XMP basic properties

**Protobuf Target:**
- `io.pipeline.parsed.data.image.v1.ImageMetadata`

### 4. Email Documents
**Tika Interfaces:**
- `Message.java` - Email message properties
- `MAPI.java` - Outlook/MAPI properties

**Protobuf Target:**
- `io.pipeline.parsed.data.email.v1.EmailMetadata`

### 5. Media Documents
**Tika Interfaces:**
- `XMPDM.java` - XMP Digital Media properties

**Protobuf Target:**
- `io.pipeline.parsed.data.media.v1.MediaMetadata`

### 6. HTML Documents
**Tika Interfaces:**
- `HTML.java` - HTML metadata properties

**Protobuf Target:**
- `io.pipeline.parsed.data.html.v1.HtmlMetadata`

### 7. RTF Documents
**Tika Interfaces:**
- `RTFMetadata.java` - RTF-specific properties

**Protobuf Target:**
- `io.pipeline.parsed.data.rtf.v1.RtfMetadata`

### 8. Database Documents
**Tika Interfaces:**
- `Database.java` - Database file properties

**Protobuf Target:**
- `io.pipeline.parsed.data.database.v1.DatabaseMetadata`

### 9. Font Documents
**Tika Interfaces:**
- `Font.java` - Font file properties

**Protobuf Target:**
- `io.pipeline.parsed.data.tika.font.v1.FontMetadata`

### 10. EPUB Documents
**Tika Interfaces:**
- `Epub.java` - EPUB book properties

**Protobuf Target:**
- `io.pipeline.parsed.data.epub.v1.EpubMetadata`

### 11. WARC Documents
**Tika Interfaces:**
- `WARC.java` - Web archive properties

**Protobuf Target:**
- `io.pipeline.parsed.data.warc.v1.WarcMetadata`

### 12. Climate Forecast Documents
**Tika Interfaces:**
- `ClimateForcast.java` - NetCDF/Climate properties

**Protobuf Target:**
- `io.pipeline.parsed.data.climate.v1.ClimateForcastMetadata`

### 13. Creative Commons Documents
**Tika Interfaces:**
- `CreativeCommons.java` - CC licensing properties
- `XMPRights.java` - XMP rights management

**Protobuf Target:**
- `io.pipeline.parsed.data.creative_commons.v1.CreativeCommonsMetadata`

### 14. Generic Documents
**Tika Interfaces:**
- `PST.java` - PST file properties
- `QuattroPro.java` - QuattroPro spreadsheet
- `WordPerfect.java` - WordPerfect document
- `MachineMetadata.java` - Machine metadata
- `ExternalProcess.java` - External process metadata
- `Geographic.java` - Geographic data
- `FileSystem.java` - File system metadata
- `HttpHeaders.java` - HTTP headers
- `Rendering.java` - Rendering properties
- `PagedText.java` - Paged text properties

**Protobuf Target:**
- `io.pipeline.parsed.data.generic.v1.GenericMetadata`

---

## 🔧 Implementation Strategy

### Phase 1: Verify Protobuf Fields Match Tika Interfaces
For each document type, ensure our protobuf has fields that correspond to the **actual** Tika interface properties.

### Phase 2: Create Accurate Builders
Build metadata extractors that map **only** the properties that actually exist in the Tika interfaces.

### Phase 3: Handle Unmapped Fields
Use `google.protobuf.Struct` to capture any metadata that doesn't have strongly-typed fields.

---

## ⚠️ Important Notes

1. **Only use properties that actually exist** in the Tika interfaces (or clearly documented literal keys from parser code)
2. **Check property types** - some are `Property.internalText()`, others are `Property.internalInteger()`, etc.
3. **Handle multiple interfaces** - some document types (like Office) have multiple related interfaces
4. **Use proper Property constants** - don't use string literals, use the actual Property constants
5. **Fallback to struct** - anything not mapped goes to the flexible struct

---

## 🎯 Next Steps

1. **Verify our protobuf fields** match the actual Tika interface properties. If we intentionally rename for clarity, document it in SOURCE_DESTINATION_MAPPING.md.
2. **Create simple, accurate builders** that map only what exists
3. **Test with real documents** to ensure we capture everything Tika extracts
4. **Follow the principle**: "Whatever Tika extracts, we save - strongly-typed if we recognize it, struct if we don't"

This mapping ensures we build **accurate** metadata extractors based on what Tika actually provides, not what we assume it should provide.
