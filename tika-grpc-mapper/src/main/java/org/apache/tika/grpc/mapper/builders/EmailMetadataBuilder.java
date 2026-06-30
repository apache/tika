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
package org.apache.tika.grpc.mapper.builders;

import java.util.HashSet;
import java.util.Set;

import com.google.protobuf.Struct;

import org.apache.tika.grpc.v1.BaseFields;
import org.apache.tika.grpc.v1.EmailMetadata;
import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Builds strongly-typed {@link EmailMetadata} from Tika metadata, mapping core
 * Dublin Core fields, RFC 822 message headers, MAPI (Outlook) properties and
 * content/security fields, with anything unmapped preserved in additional metadata.
 */
public class EmailMetadataBuilder {

    /**
     * Utility class; not meant to be instantiated.
     */
    private EmailMetadataBuilder() { }

    /**
     * Builds an {@link EmailMetadata} message from the given Tika metadata.
     * <p>
     * Maps core document properties, RFC 822 message headers, MAPI (Outlook)
     * properties and content/security fields, preserving any remaining keys in
     * {@code additional_metadata}.
     *
     * @param md the Tika metadata extracted from the document
     * @param parserClass the fully qualified class name of the Tika parser used
     * @param tikaVersion the version of Tika that produced the metadata
     * @param excludedKeys metadata keys to exclude from the additional-metadata dump
     * @return the populated {@link EmailMetadata} message
     */
    public static EmailMetadata build(Metadata md, String parserClass, String tikaVersion, Set<String> excludedKeys) {
        EmailMetadata.Builder b = EmailMetadata.newBuilder();
        Set<String> mapped = new HashSet<>(excludedKeys);

        mapCore(md, b, mapped);
        mapMessage(md, b, mapped);
        mapMAPI(md, b, mapped);
        mapContentAndSecurity(md, b, mapped);

        Struct additional = MetadataUtils.buildAdditionalMetadata(md, mapped);
        b.setAdditionalMetadata(additional);

        BaseFields base = MetadataUtils.buildBaseFields(parserClass, tikaVersion, md);
        b.setBaseFields(base);

        return b.build();
    }

    private static void mapCore(Metadata md, EmailMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, TikaCoreProperties.TITLE, b::setTitle, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR, b::setCreator, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DESCRIPTION, b::setDescription, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SUBJECT, b::setSubject, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.LANGUAGE, b::setLanguage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.PUBLISHER, b::setPublisher, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RIGHTS, b::setRights, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.FORMAT, b::setFormat, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.IDENTIFIER, b::setIdentifier, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CONTRIBUTOR, b::setContributor, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COVERAGE, b::setCoverage, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RELATION, b::setRelation, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.SOURCE, b::setSource, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TYPE, b::setType, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.CREATED, b::setCreated, mapped);
        MetadataUtils.mapTimestampField(md, TikaCoreProperties.MODIFIED, b::setModified, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.CREATOR_TOOL, b::setCreatorTool, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.COMMENTS, b::setComments, mapped);
    }

    private static void mapMessage(Metadata md, EmailMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, Message.MESSAGE_FROM, b::setMessageFrom, mapped);
        MetadataUtils.mapStringField(md, Message.MESSAGE_TO, b::setMessageTo, mapped);
        MetadataUtils.mapStringField(md, Message.MESSAGE_CC, b::setMessageCc, mapped);
        MetadataUtils.mapStringField(md, Message.MESSAGE_BCC, b::setMessageBcc, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_FROM_NAME, b::addAllMessageFromName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_FROM_EMAIL, b::addAllMessageFromEmail, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_TO_NAME, b::addAllMessageToName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_TO_DISPLAY_NAME, b::addAllMessageToDisplayName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_TO_EMAIL, b::addAllMessageToEmail, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_CC_NAME, b::addAllMessageCcName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_CC_DISPLAY_NAME, b::addAllMessageCcDisplayName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_CC_EMAIL, b::addAllMessageCcEmail, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_BCC_NAME, b::addAllMessageBccName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_BCC_DISPLAY_NAME, b::addAllMessageBccDisplayName, mapped);
        MetadataUtils.mapRepeatedStringField(md, Message.MESSAGE_BCC_EMAIL, b::addAllMessageBccEmail, mapped);
        MetadataUtils.mapStringField(md, Message.MULTIPART_SUBTYPE, b::setMultipartSubtype, mapped);
        MetadataUtils.mapStringField(md, Message.MULTIPART_BOUNDARY, b::setMultipartBoundary, mapped);
    }

    private static void mapMAPI(Metadata md, EmailMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, MAPI.MESSAGE_CLASS, b::setMessageClass, mapped);
        MetadataUtils.mapStringField(md, MAPI.MESSAGE_CLASS_RAW, b::setMessageClassRaw, mapped);
        MetadataUtils.mapStringField(md, MAPI.SENT_BY_SERVER_TYPE, b::setSentByServerType, mapped);
        MetadataUtils.mapStringField(md, MAPI.FROM_REPRESENTING_NAME, b::setFromRepresentingName, mapped);
        MetadataUtils.mapStringField(md, MAPI.FROM_REPRESENTING_EMAIL, b::setFromRepresentingEmail, mapped);
        MetadataUtils.mapTimestampField(md, MAPI.SUBMISSION_ACCEPTED_AT_TIME, b::setSubmissionAcceptedAtTime, mapped);
        MetadataUtils.mapStringField(md, MAPI.SUBMISSION_ID, b::setSubmissionId, mapped);
        MetadataUtils.mapStringField(md, MAPI.INTERNET_MESSAGE_ID, b::setInternetMessageId, mapped);
        MetadataUtils.mapRepeatedStringField(md, MAPI.INTERNET_REFERENCES, b::addAllInternetReferences, mapped);
        MetadataUtils.mapStringField(md, MAPI.CONVERSATION_TOPIC, b::setConversationTopic, mapped);
        MetadataUtils.mapStringField(md, MAPI.CONVERSATION_INDEX, b::setConversationIndex, mapped);
        MetadataUtils.mapStringField(md, MAPI.IN_REPLY_TO_ID, b::setInReplyToId, mapped);
        MetadataUtils.mapStringField(md, MAPI.RECIPIENTS_STRING, b::setRecipientsString, mapped);
        MetadataUtils.mapIntField(md, MAPI.IMPORTANCE, b::setImportance, mapped);
        MetadataUtils.mapIntField(md, MAPI.PRIORTY, b::setPriority, mapped);
        MetadataUtils.mapBooleanField(md, MAPI.IS_FLAGGED, b::setIsFlagged, mapped);
        MetadataUtils.mapRepeatedStringField(md, MAPI.BODY_TYPES_PROCESSED, b::addAllBodyTypesProcessed, mapped);

        MetadataUtils.mapStringField(md, MAPI.ATTACH_LONG_PATH_NAME, b::setAttachLongPathName, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_LONG_FILE_NAME, b::setAttachLongFileName, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_FILE_NAME, b::setAttachFileName, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_CONTENT_ID, b::setAttachContentId, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_CONTENT_LOCATION, b::setAttachContentLocation, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_DISPLAY_NAME, b::setAttachDisplayName, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_EXTENSION, b::setAttachExtension, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_MIME, b::setAttachMime, mapped);
        MetadataUtils.mapStringField(md, MAPI.ATTACH_LANGUAGE, b::setAttachLanguage, mapped);
    }

    private static void mapContentAndSecurity(Metadata md, EmailMetadata.Builder b, Set<String> mapped) {
        MetadataUtils.mapStringField(md, "Content-Type", b::setContentType, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ENCODING_DETECTOR, b::setEncodingDetector, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.DETECTED_ENCODING, b::setDetectedEncoding, mapped);
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.HAS_SIGNATURE, b::setHasSignature, mapped);
        MetadataUtils.mapBooleanField(md, TikaCoreProperties.IS_ENCRYPTED, b::setIsEncrypted, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.SIGNATURE_NAME, b::addAllSignatureName, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.SIGNATURE_LOCATION, b::addAllSignatureLocation, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.SIGNATURE_REASON, b::addAllSignatureReason, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.SIGNATURE_FILTER, b::addAllSignatureFilter, mapped);
        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.SIGNATURE_CONTACT_INFO, b::addAllSignatureContactInfo, mapped);
        // signature_date can be dates or millis
        String[] dates = md.getValues(TikaCoreProperties.SIGNATURE_DATE.getName());
        if (dates != null) {
            for (String v : dates) {
                if (v == null || v.trim().isEmpty()) continue;
                com.google.protobuf.Timestamp ts = tryParseTimestamp(v.trim());
                if (ts != null) b.addSignatureDate(ts);
            }
            if (dates.length > 0) mapped.add(TikaCoreProperties.SIGNATURE_DATE.getName());
        }

        MetadataUtils.mapRepeatedStringField(md, TikaCoreProperties.TIKA_PARSED_BY, b::addAllParsedBy, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE, b::setDetectedLanguage, mapped);
        MetadataUtils.mapDoubleField(md, TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, b::setDetectedLanguageConfidence, mapped);

        MetadataUtils.mapStringField(md, TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, b::setEmbeddedResourceType, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.RESOURCE_NAME_KEY, b::setResourceName, mapped);
        MetadataUtils.mapStringField(md, TikaCoreProperties.ORIGINAL_RESOURCE_NAME, b::setOriginalResourceName, mapped);
    }

    private static com.google.protobuf.Timestamp tryParseTimestamp(String value) {
        try {
            long millis = Long.parseLong(value);
            java.time.Instant instant = java.time.Instant.ofEpochMilli(millis);
            return com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
        } catch (NumberFormatException ignore) { }
        try {
            java.time.Instant instant = java.time.Instant.parse(value);
            return com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
