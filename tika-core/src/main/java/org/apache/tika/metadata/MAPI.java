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
 * Office Document properties collection. These properties apply to
 * Office / Productivity Documents of all forms, including (but not limited
 * to) MS Office and OpenDocument formats.
 * This is a logical collection of properties, which may be drawn from a
 * few different external definitions.
 *
 * @since Apache Tika 1.2
 */
public interface MAPI {

    String PREFIX_MAPI_META = "mapi" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * MAPI message class.  What type of .msg/MAPI file is it?
     * This is normalized via "mapi_message_classes.properties
     */
    Property MESSAGE_CLASS =
            Property.internalText(PREFIX_MAPI_META + "message-class");

    /**
     * MAPI message class.  What type of .msg/MAPI file is it?
     * This is the raw value that is retrieved from the underlying chunk
     */
    Property MESSAGE_CLASS_RAW =
            Property.internalText(PREFIX_MAPI_META + "message-class-raw");

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
}
