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
 * A collection of Message related property names.
 *
 * See also {@link Office}'s MAPI-specific properties.
 */
public interface Message {
    String MESSAGE_PREFIX = "Message"+ Metadata.NAMESPACE_PREFIX_DELIMITER;

    String MESSAGE_RAW_HEADER_PREFIX = MESSAGE_PREFIX+"Raw-Header"+Metadata.NAMESPACE_PREFIX_DELIMITER;

    String MESSAGE_RECIPIENT_ADDRESS = "Message-Recipient-Address";
    
    String MESSAGE_FROM = "Message-From";
    
    String MESSAGE_TO = "Message-To";
    
    String MESSAGE_CC = "Message-Cc";
    
    String MESSAGE_BCC = "Message-Bcc";

    /**
     * Where possible, we try to separate the name from the email address
     * in Message files.  This is multivalued for cases where an email is sent
     * "on behalf of" someone...this is still to be implemented, though.
     * The name may be an organization name.
     */
    Property MESSAGE_FROM_NAME = Property.internalTextBag(MESSAGE_PREFIX+"From-Name");

    /**
     * Where possible, we try to separate the name from the email address
     * in Message files.  This is multivalued for cases where an email is sent
     * "on behalf of" someone...this is still to be implemented, though.
     */
    Property MESSAGE_FROM_EMAIL = Property.internalTextBag(MESSAGE_PREFIX+"From-Email");

    Property MESSAGE_TO_NAME = Property.internalTextBag(MESSAGE_PREFIX+"To-Name");

    Property MESSAGE_TO_EMAIL = Property.internalTextBag(MESSAGE_PREFIX+"To-Name");

    Property MESSAGE_CC_NAME = Property.internalTextBag(MESSAGE_PREFIX+"CC-Name");

    Property MESSAGE_CC_EMAIL = Property.internalTextBag(MESSAGE_PREFIX+"CC-Name");

    Property MESSAGE_BCC_NAME = Property.internalTextBag(MESSAGE_PREFIX+"CC-Name");

    Property MESSAGE_BCC_EMAIL = Property.internalTextBag(MESSAGE_PREFIX+"CC-Name");

}
