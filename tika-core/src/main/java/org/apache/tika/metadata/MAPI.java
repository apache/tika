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
package org.apache.tika.metadata;

/**
 *
 * Properties that typically appear in MSG/PST message format files.
 *
 * @since Apache Tika 4.0
 */
public interface MAPI {

    String PREFIX_MAPI_META = "mapi" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;
    String PREFIX_MAPI_ATTACH_META = "mapi:attach" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;
    String PREFIX_MAPI_PROPERTY = PREFIX_MAPI_META + "property" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * MAPI message class.  What type of .msg/MAPI file is it?
     * This is normalized via "mapi_message_classes.properties
     */
    Property MESSAGE_CLASS = Property.internalText(PREFIX_MAPI_META + "message-class");

    /**
     * MAPI message class.  What type of .msg/MAPI file is it?
     * This is the raw value that is retrieved from the underlying chunk
     */
    Property MESSAGE_CLASS_RAW = Property.internalText(PREFIX_MAPI_META + "message-class-raw");

    Property SENT_BY_SERVER_TYPE = Property.internalText(PREFIX_MAPI_META + "sent-by-server-type");

    Property FROM_REPRESENTING_NAME = Property.internalText(PREFIX_MAPI_META + "from-representing-name");

    Property FROM_REPRESENTING_EMAIL = Property.internalText(PREFIX_MAPI_META + "from-representing-email");

    Property SUBMISSION_ACCEPTED_AT_TIME = Property.internalDate(PREFIX_MAPI_META + "msg-submission-accepted-at-time");

    Property SUBMISSION_ID = Property.internalText(PREFIX_MAPI_META + "msg-submission-id");

    Property INTERNET_MESSAGE_ID = Property.internalText(PREFIX_MAPI_META + "internet-message-id");

    Property INTERNET_REFERENCES = Property.internalTextBag(PREFIX_MAPI_META + "internet-references");


    Property CONVERSATION_TOPIC = Property.internalText(PREFIX_MAPI_META + "conversation-topic");

    Property CONVERSATION_INDEX = Property.internalText(PREFIX_MAPI_META + "conversation-index");
    Property IN_REPLY_TO_ID = Property.internalText(PREFIX_MAPI_META + "in-reply-to-id");

    Property RECIPIENTS_STRING = Property.internalText(PREFIX_MAPI_META + "recipients-string");
    Property IMPORTANCE = Property.internalInteger(PREFIX_MAPI_META + "importance");
    Property PRIORTY = Property.internalInteger(PREFIX_MAPI_META + "priority");
    Property IS_FLAGGED = Property.internalBoolean(PREFIX_MAPI_META + "is-flagged");

    Property BODY_TYPES_PROCESSED = Property.internalTextBag(PREFIX_MAPI_META + "body-types-processed");

    Property ATTACH_LONG_PATH_NAME = Property.internalText(PREFIX_MAPI_ATTACH_META + "long-path-name");
    Property ATTACH_LONG_FILE_NAME = Property.internalText(PREFIX_MAPI_ATTACH_META + "long-file-name");
    Property ATTACH_FILE_NAME = Property.internalText(PREFIX_MAPI_ATTACH_META + "file-name");
    Property ATTACH_CONTENT_ID = Property.internalText(PREFIX_MAPI_ATTACH_META + "content-id");
    Property ATTACH_CONTENT_LOCATION = Property.internalText(PREFIX_MAPI_ATTACH_META + "content-location");
    Property ATTACH_DISPLAY_NAME = Property.internalText(PREFIX_MAPI_ATTACH_META + "display-name");
    Property ATTACH_EXTENSION = Property.internalText(PREFIX_MAPI_ATTACH_META + "extension");
    Property ATTACH_MIME = Property.internalText(PREFIX_MAPI_ATTACH_META + "mime");
    Property ATTACH_LANGUAGE = Property.internalText(PREFIX_MAPI_ATTACH_META + "language");

}

