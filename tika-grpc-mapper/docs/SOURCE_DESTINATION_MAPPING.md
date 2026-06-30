# Source to Destination Field Mapping

## 🎯 Purpose
This document provides **exact** mappings from Tika interface properties to our protobuf fields. Use this as the definitive reference when implementing metadata builders.

---

## 📋 PDF Documents

### Source: `org.apache.tika.metadata.PDF.java`
### Destination: `io.pipeline.parsed.data.pdf.v1.PdfMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `PDF.DOC_INFO_TITLE` | `doc_info_title` | string | PDF document title |
| `PDF.DOC_INFO_AUTHOR` | `doc_info_author` | string | PDF document author |
| `PDF.DOC_INFO_SUBJECT` | `doc_info_subject` | string | PDF document subject |
| `PDF.DOC_INFO_KEYWORDS` | `doc_info_keywords` | string | PDF document keywords |
| `PDF.DOC_INFO_CREATOR` | `doc_info_creator` | string | PDF document creator |
| `PDF.DOC_INFO_PRODUCER` | `doc_info_producer` | string | PDF document producer |
| `PDF.DOC_INFO_CREATED` | `doc_info_created` | Timestamp | PDF creation date |
| `PDF.DOC_INFO_MODIFICATION_DATE` | `doc_info_modification_date` | Timestamp | PDF modification date |
| `PDF.DOC_INFO_TRAPPED` | `doc_info_trapped` | string | PDF trapped status |
| `PDF.PDF_VERSION` | `pdf_version` | string | PDF version |
| `PDF.PDFA_VERSION` | `pdfa_version` | string | PDF/A version |
| `PDF.PDF_EXTENSION_VERSION` | `pdf_extension_version` | string | PDF extension version |
| `PDF.PDFAID_CONFORMANCE` | `pdfaid_conformance` | string | PDF/A ID conformance |
| `PDF.PDFAID_PART` | `pdfaid_part` | int32 | PDF/A ID part |
| `PDF.PDFUAID_PART` | `pdfuaid_part` | int32 | PDF/UA ID part |
| `PDF.PDFVT_VERSION` | `pdfvt_version` | string | PDF/VT version |
| `PDF.PDFVT_MODIFIED` | `pdfvt_modified` | Timestamp | PDF/VT modified date |
| `PDF.PDFXID_VERSION` | `pdfxid_version` | string | PDF/X ID version |
| `PDF.PDFX_VERSION` | `pdfx_version` | string | PDF/X version |
| `PDF.PDFX_CONFORMANCE` | `pdfx_conformance` | string | PDF/X conformance |
| `PDF.IS_ENCRYPTED` | `is_encrypted` | bool | PDF encryption status |
| `PDF.PRODUCER` | `producer` | string | PDF producer |
| `PDF.HAS_XFA` | `has_xfa` | bool | Has XFA forms |
| `PDF.HAS_XMP` | `has_xmp` | bool | Has XMP metadata |
| `PDF.XMP_LOCATION` | `xmp_location` | string | XMP metadata location |
| `PDF.HAS_ACROFORM_FIELDS` | `has_acroform_fields` | bool | Has AcroForm fields |
| `PDF.HAS_MARKED_CONTENT` | `has_marked_content` | bool | Has marked content |
| `PDF.HAS_COLLECTION` | `has_collection` | bool | Has collection (portfolio) |
| `PDF.HAS_3D` | `has_3d` | bool | Has 3D annotations |
| `PDF.NUM_3D_ANNOTATIONS` | `num_3d_annotations` | int32 | Number of 3D annotations |
| `PDF.ACTION_TRIGGER` | `action_trigger` | string | Action trigger location |
| `PDF.ACTION_TRIGGERS` | `action_triggers` | repeated string | All action triggers |
| `PDF.ACTION_TYPES` | `action_types` | repeated string | Action types |
| `PDF.CHARACTERS_PER_PAGE` | `characters_per_page` | repeated int32 | Characters per page |
| `PDF.UNMAPPED_UNICODE_CHARS_PER_PAGE` | `unmapped_unicode_chars_per_page` | repeated int32 | Unmapped unicode chars per page |
| `PDF.TOTAL_UNMAPPED_UNICODE_CHARS` | `total_unmapped_unicode_chars` | int32 | Total unmapped unicode chars |
| `PDF.OVERALL_PERCENTAGE_UNMAPPED_UNICODE_CHARS` | `overall_percentage_unmapped_unicode_chars` | double | Percentage unmapped unicode |
| `PDF.CONTAINS_DAMAGED_FONT` | `contains_damaged_font` | bool | Contains damaged fonts |
| `PDF.CONTAINS_NON_EMBEDDED_FONT` | `contains_non_embedded_font` | bool | Contains non-embedded fonts |
| `PDF.EMBEDDED_FILE_DESCRIPTION` | `embedded_file_description` | string | Embedded file description |
| `PDF.EMBEDDED_FILE_ANNOTATION_TYPE` | `embedded_file_annotation_type` | string | Embedded file annotation type |
| `PDF.EMBEDDED_FILE_SUBTYPE` | `embedded_file_subtype` | string | Embedded file subtype |
| `PDF.ANNOTATION_TYPES` | `annotation_types` | repeated string | Annotation types |
| `PDF.ANNOTATION_SUBTYPES` | `annotation_subtypes` | repeated string | Annotation subtypes |
| `PDF.ASSOCIATED_FILE_RELATIONSHIP` | `associated_file_relationship` | string | Associated file relationship |
| `PDF.INCREMENTAL_UPDATE_NUMBER` | `incremental_update_number` | int32 | Incremental update number |
| `PDF.PDF_INCREMENTAL_UPDATE_COUNT` | `pdf_incremental_update_count` | int32 | Incremental update count |
| `PDF.OCR_PAGE_COUNT` | `ocr_page_count` | int32 | OCR page count |
| `PDF.EOF_OFFSETS` | `eof_offsets` | repeated double | EOF offsets |

### Source: `org.apache.tika.metadata.XMPPDF.java`
### Destination: `io.pipeline.parsed.data.pdf.v1.PdfMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `XMPPDF.KEY_WORDS` | `xmp_keywords` | string | XMP PDF keywords (constant is KEY_WORDS in Tika 2.x snapshot) |
| `XMPPDF.PDF_VERSION` | `pdf_version` | string | XMP PDF version (normalized to `pdf_version`) |
| `XMPPDF.PRODUCER` | `producer` | string | XMP PDF producer (normalized to `producer`) |

### Source: `org.apache.tika.metadata.AccessPermissions.java`
### Destination: `io.pipeline.parsed.data.pdf.v1.PdfMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `AccessPermissions.ASSEMBLE_DOCUMENT` | `can_assemble_document` | bool | Can assemble document |
| `AccessPermissions.EXTRACT_CONTENT` | `can_extract_content` | bool | Can extract content |
| `AccessPermissions.EXTRACT_FOR_ACCESSIBILITY` | `can_extract_for_accessibility` | bool | Can extract for accessibility |
| `AccessPermissions.FILL_IN_FORM` | `can_fill_in_form` | bool | Can fill in forms |
| `AccessPermissions.CAN_MODIFY_ANNOTATIONS` | `can_modify_annotations` | bool | Can modify annotations |
| `AccessPermissions.CAN_MODIFY` | `can_modify_document` | bool | Can modify document |
| `AccessPermissions.CAN_PRINT` | `can_print` | bool | Can print |
| `AccessPermissions.CAN_PRINT_FAITHFUL` | `can_print_faithful` | bool | Can print faithful |

Notes:
- We normalize to expressive `can_*` boolean fields and align names/types with Tika snapshot.
### Additional PDF-related fields
### Sources: `org.apache.tika.metadata.PagedText.java`, PDF parser literals
### Destination: `io.pipeline.parsed.data.pdf.v1.PdfMetadata`

| Source | Protobuf Field | Type | Notes |
|--------|----------------|------|-------|
| `PagedText.N_PAGES` | `n_pages` | int32 | Total number of pages |
| `"X-TIKA:pdf:metadata-xmp-parse-failed"` | `xmp_parse_failed` | repeated string | XMP parse failure messages |
| `PDF.ILLUSTRATOR_TYPE` | `illustrator_type` | string | Illustrator type if present |
| `"pdf:foundNonAdobeExtensionName"` | — | — | Kept in `additional_metadata` (literal key) |

---

## 📋 Office Documents

### Source: `org.apache.tika.metadata.Office.java`
### Destination: `io.pipeline.parsed.data.office.v1.OfficeMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `Office.CHARACTER_COUNT` | `character_count` | int32 | Character count |
| `Office.CHARACTER_COUNT_WITH_SPACES` | `character_count_with_spaces` | int32 | Character count with spaces |
| `Office.LINE_COUNT` | `line_count` | int32 | Line count |
| `Office.PAGE_COUNT` | `page_count` | int32 | Page count |
| `Office.PARAGRAPH_COUNT` | `paragraph_count` | int32 | Paragraph count |
| `Office.WORD_COUNT` | `word_count` | int32 | Word count |
| `Office.SLIDE_COUNT` | `slide_count` | int32 | Slide count |
| `Office.NOTES_COUNT` | `notes_count` | int32 | Notes count |
| `Office.HIDDEN_COUNT` | `hidden_count` | int32 | Hidden slide count |
| `Office.MULTIMEDIA_OBJECT_COUNT` | `multimedia_object_count` | int32 | Multimedia object count |

### Source: `org.apache.tika.metadata.OfficeOpenXMLCore.java`
### Destination: `io.pipeline.parsed.data.office.v1.OfficeMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `OfficeOpenXMLCore.CATEGORY` | `category` | string | Document category |
| `OfficeOpenXMLCore.CONTENT_STATUS` | `content_status` | string | Content status |
| `OfficeOpenXMLCore.CREATED` | `created` | Timestamp | Creation date |
| `OfficeOpenXMLCore.CREATOR` | `creator` | string | Creator |
| `OfficeOpenXMLCore.DESCRIPTION` | `description` | string | Description |
| `OfficeOpenXMLCore.IDENTIFIER` | `identifier` | string | Identifier |
| `OfficeOpenXMLCore.KEYWORDS` | `keywords` | string | Keywords |
| `OfficeOpenXMLCore.LANGUAGE` | `language` | string | Language |
| `OfficeOpenXMLCore.LAST_MODIFIED_BY` | `last_modified_by` | string | Last modified by |
| `OfficeOpenXMLCore.LAST_PRINTED` | `last_printed` | Timestamp | Last printed |
| `OfficeOpenXMLCore.MODIFIED` | `modified` | Timestamp | Modified date |
| `OfficeOpenXMLCore.REVISION` | `revision` | string | Revision |
| `OfficeOpenXMLCore.SUBJECT` | `subject` | string | Subject |
| `OfficeOpenXMLCore.TITLE` | `title` | string | Title |
| `OfficeOpenXMLCore.VERSION` | `version` | string | Version |

### Source: `org.apache.tika.metadata.OfficeOpenXMLExtended.java`
### Destination: `io.pipeline.parsed.data.office.v1.OfficeMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `OfficeOpenXMLExtended.APPLICATION` | `application` | string | Application name |
| `OfficeOpenXMLExtended.APPLICATION_VERSION` | `application_version` | string | Application version |
| `OfficeOpenXMLExtended.CHARACTERS` | `characters` | int64 | Character count |
| `OfficeOpenXMLExtended.CHARACTERS_WITH_SPACES` | `characters_with_spaces` | int64 | Characters with spaces |
| `OfficeOpenXMLExtended.COMPANY` | `company` | string | Company |
| `OfficeOpenXMLExtended.DOC_SECURITY` | `doc_security` | int32 | Document security |
| `OfficeOpenXMLExtended.HIDDEN_SLIDES` | `hidden_slides` | int32 | Hidden slides |
| `OfficeOpenXMLExtended.LINES` | `lines` | int32 | Lines |
| `OfficeOpenXMLExtended.MANAGER` | `manager` | string | Manager |
| `OfficeOpenXMLExtended.MULTIMEDIA_CLIPS` | `multimedia_clips` | int32 | Multimedia clips |
| `OfficeOpenXMLExtended.NOTES` | `notes` | int32 | Notes |
| `OfficeOpenXMLExtended.PAGES` | `pages` | int32 | Pages |
| `OfficeOpenXMLExtended.PARAGRAPHS` | `paragraphs` | int32 | Paragraphs |
| `OfficeOpenXMLExtended.PRESENTATION_FORMAT` | `presentation_format` | string | Presentation format |
| `OfficeOpenXMLExtended.SLIDES` | `slides` | int32 | Slides |
| `OfficeOpenXMLExtended.TEMPLATE` | `template` | string | Template |
| `OfficeOpenXMLExtended.TOTAL_TIME` | `total_time_raw` / `total_time_seconds` | string / int32 | Raw duration and parsed seconds |
| `OfficeOpenXMLExtended.WORDS` | `words` | int32 | Words |

---

## 📋 Image Documents

### Source: `org.apache.tika.metadata.TIFF.java`
### Destination: `io.pipeline.parsed.data.image.v1.ImageMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `TIFF.IMAGE_WIDTH` | `image_width` | int32 | Image width |
| `TIFF.IMAGE_LENGTH` | `image_length` | int32 | Image height |
| `TIFF.BITS_PER_SAMPLE` | `bits_per_sample` | repeated int32 | Bits per sample |
| `TIFF.COMPRESSION` | `compression` | int32 | Compression type |
| `TIFF.PHOTOMETRIC_INTERPRETATION` | `photometric_interpretation` | int32 | Photometric interpretation |
| `TIFF.SAMPLES_PER_PIXEL` | `samples_per_pixel` | int32 | Samples per pixel |
| `TIFF.PLANAR_CONFIGURATION` | `planar_configuration` | int32 | Planar configuration |
| `TIFF.RESOLUTION_UNIT` | `resolution_unit` | int32 | Resolution unit |
| `TIFF.X_RESOLUTION` | `x_resolution` | double | X resolution |
| `TIFF.Y_RESOLUTION` | `y_resolution` | double | Y resolution |
| `TIFF.ORIENTATION` | `orientation` | int32 | Image orientation |

### Source: `org.apache.tika.metadata.IPTC.java`
### Destination: `io.pipeline.parsed.data.image.v1.ImageMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `IPTC.KEYWORDS` | `iptc_keywords` | repeated string | IPTC keywords |
| `IPTC.CAPTION_ABSTRACT` | `iptc_caption` | string | IPTC caption |
| `IPTC.HEADLINE` | `iptc_headline` | string | IPTC headline |
| `IPTC.CREDIT` | `iptc_credit` | string | IPTC credit |
| `IPTC.SOURCE` | `iptc_source` | string | IPTC source |
| `IPTC.COPYRIGHT_NOTICE` | `iptc_copyright` | string | IPTC copyright |
| `IPTC.CATEGORY` | `iptc_category` | string | IPTC category |
| `IPTC.SUPPLEMENTAL_CATEGORIES` | `iptc_supplemental_categories` | repeated string | IPTC supplemental categories |

---

## 📋 Email Documents

### Source: `org.apache.tika.metadata.Message.java`
### Destination: `io.pipeline.parsed.data.email.v1.EmailMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `Message.MESSAGE_FROM` | `from_address` | string | From address |
| `Message.MESSAGE_TO` | `to_addresses` | repeated string | To addresses |
| `Message.MESSAGE_CC` | `cc_addresses` | repeated string | CC addresses |
| `Message.MESSAGE_BCC` | `bcc_addresses` | repeated string | BCC addresses |
| `Message.MESSAGE_SUBJECT` | `subject` | string | Email subject |
| `Message.MESSAGE_DATE` | `message_date` | Timestamp | Message date |
| `Message.MESSAGE_ID` | `message_id` | string | Message ID |
| `Message.MESSAGE_IN_REPLY_TO` | `in_reply_to` | string | In reply to |
| `Message.MESSAGE_REFERENCES` | `references` | repeated string | References |

### Source: `org.apache.tika.metadata.MAPI.java`
### Destination: `io.pipeline.parsed.data.email.v1.EmailMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `MAPI.MESSAGE_CLASS` | `mapi_message_class` | string | MAPI message class |
| `MAPI.CONVERSATION_TOPIC` | `mapi_conversation_topic` | string | MAPI conversation topic |

---

## 📋 Media Documents

### Source: `org.apache.tika.metadata.XMPDM.java`
### Destination: `io.pipeline.parsed.data.media.v1.MediaMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `XMPDM.DURATION` | `duration_seconds` | double | Duration in seconds |
| `XMPDM.AUDIO_SAMPLE_RATE` | `audio_sample_rate` | int32 | Audio sample rate |
| `XMPDM.AUDIO_CHANNELS` | `audio_channels` | int32 | Audio channels |
| `XMPDM.VIDEO_FRAME_RATE` | `video_frame_rate` | double | Video frame rate |
| `XMPDM.VIDEO_WIDTH` | `video_width` | int32 | Video width |
| `XMPDM.VIDEO_HEIGHT` | `video_height` | int32 | Video height |
| `XMPDM.ARTIST` | `artist` | string | Artist |
| `XMPDM.ALBUM` | `album` | string | Album |
| `XMPDM.TRACK_NUMBER` | `track_number` | int32 | Track number |
| `XMPDM.GENRE` | `genre` | string | Genre |
| `XMPDM.COMPOSER` | `composer` | string | Composer |

---

## 📋 HTML Documents

### Source: `org.apache.tika.metadata.HTML.java`
### Destination: `io.pipeline.parsed.data.html.v1.HtmlMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `HTML.DESCRIPTION` | `description` | string | HTML description |
| `HTML.KEYWORDS` | `keywords` | repeated string | HTML keywords |
| `HTML.REFRESH` | `refresh` | string | HTML refresh |

---

## 📋 Creative Commons Documents

### Source: `org.apache.tika.metadata.CreativeCommons.java`
### Destination: `io.pipeline.parsed.data.creative_commons.v1.CreativeCommonsMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `CreativeCommons.LICENSE_URL` | `license_url` | string | License URL |
| `CreativeCommons.LICENSE_LOCATION` | `license_location` | string | License location |
| `CreativeCommons.WORK_TYPE` | `work_type` | string | Work type |

### Source: `org.apache.tika.metadata.XMPRights.java`
### Destination: `io.pipeline.parsed.data.creative_commons.v1.CreativeCommonsMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `XMPRights.CERTIFICATE` | `rights_certificate` | string | Rights certificate |
| `XMPRights.MARKED` | `rights_marked` | bool | Rights marked |
| `XMPRights.OWNER` | `rights_owners` | repeated string | Rights owners |
| `XMPRights.USAGE_TERMS` | `usage_terms` | string | Usage terms |
| `XMPRights.WEB_STATEMENT` | `web_statement` | string | Web statement |

---

## 📋 Font Documents

### Source: `org.apache.tika.metadata.Font.java`
### Destination: `io.pipeline.parsed.data.tika.font.v1.FontMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `Font.FONT_NAME` | `font_name` | string | Font name |

---

## 📋 EPUB Documents

### Source: `org.apache.tika.metadata.Epub.java`
### Destination: `io.pipeline.parsed.data.epub.v1.EpubMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `Epub.RENDITION_LAYOUT` | `rendition_layout` | string | Rendition layout |
| `Epub.VERSION` | `version` | string | EPUB version |

---

## 📋 WARC Documents

### Source: `org.apache.tika.metadata.WARC.java`
### Destination: `io.pipeline.parsed.data.warc.v1.WarcMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `WARC.WARC_RECORD_ID` | `warc_record_id` | string | WARC record ID |
| `WARC.WARC_RECORD_CONTENT_TYPE` | `warc_record_content_type` | string | WARC record content type |
| `WARC.WARC_RECORD_DATE` | `warc_record_date` | Timestamp | WARC record date |
| `WARC.WARC_RECORD_TYPE` | `warc_record_type` | string | WARC record type |

---

## 📋 Climate Forecast Documents

### Source: `org.apache.tika.metadata.ClimateForcast.java`
### Destination: `io.pipeline.parsed.data.climate.v1.ClimateForcastMetadata`

| Tika Property | Protobuf Field | Type | Notes |
|---------------|----------------|------|-------|
| `ClimateForcast.PROGRAM_ID` | `program_id` | string | Program ID |
| `ClimateForcast.INSTITUTION` | `institution` | string | Institution |
| `ClimateForcast.EXPERIMENT_ID` | `experiment_id` | string | Experiment ID |
| `ClimateForcast.MODEL_ID` | `model_id` | string | Model ID |
| `ClimateForcast.REALIZATION` | `realization` | string | Realization |
| `ClimateForcast.FREQUENCY` | `frequency` | string | Frequency |
| `ClimateForcast.REALM` | `realm` | string | Realm |
| `ClimateForcast.VARIABLE_ID` | `variable_id` | string | Variable ID |
| `ClimateForcast.ENSEMBLE` | `ensemble` | string | Ensemble |
| `ClimateForcast.GRID_LABEL` | `grid_label` | string | Grid label |
| `ClimateForcast.NOMINAL_RESOLUTION` | `nominal_resolution` | string | Nominal resolution |
| `ClimateForcast.SOURCE_ID` | `source_id` | string | Source ID |
| `ClimateForcast.SUB_EXPERIMENT_ID` | `sub_experiment_id` | string | Sub experiment ID |
| `ClimateForcast.TABLE_ID` | `table_id` | string | Table ID |
| `ClimateForcast.VARIANT_LABEL` | `variant_label` | string | Variant label |
| `ClimateForcast.ACTIVITY_ID` | `activity_id` | string | Activity ID |

---

## 🔧 Usage Instructions

### For Implementing Metadata Builders:

1. **Find your document type** in this mapping
2. **Use the exact Tika Property names** from the "Tika Property" column
3. **Map to the exact protobuf field names** from the "Protobuf Field" column
4. **Use the correct data types** as specified in the "Type" column
5. **Any unmapped fields** go into the `additional_metadata` struct

### Example Implementation:
```java
// For PDF metadata builder
MetadataUtils.mapStringField(tikaMetadata, PDF.DOC_INFO_TITLE, builder::setDocInfoTitle, mappedFields);
MetadataUtils.mapTimestampField(tikaMetadata, PDF.DOC_INFO_CREATED, builder::setDocInfoCreated, mappedFields);
MetadataUtils.mapBooleanField(tikaMetadata, PDF.IS_ENCRYPTED, builder::setIsEncrypted, mappedFields);
```

This mapping ensures **exact** correspondence between what Tika extracts and what our protobuf captures!
