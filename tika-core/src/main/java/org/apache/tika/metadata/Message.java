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
 * <p>
 * See also {@link Office}'s MAPI-specific properties.
 */
public interface Message {
    String MESSAGE_PREFIX = "message" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    String MULTIPART_PREFIX = "multipart" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    String MESSAGE_RAW_HEADER_PREFIX =
            MESSAGE_PREFIX + "raw-header" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    KeyPrefix RAW_HEADER = KeyPrefix.file(MESSAGE_RAW_HEADER_PREFIX,
            "RFC822 / Outlook raw email header names");

    Property MESSAGE_RECIPIENT_ADDRESS =
            Property.internalTextBag(MESSAGE_PREFIX + "recipient-address");

    Property MESSAGE_FROM = Property.internalTextBag(MESSAGE_PREFIX + "from");

    Property MESSAGE_TO = Property.internalTextBag(MESSAGE_PREFIX + "to");

    Property MESSAGE_CC = Property.internalTextBag(MESSAGE_PREFIX + "cc");

    Property MESSAGE_BCC = Property.internalTextBag(MESSAGE_PREFIX + "bcc");

    Property MULTIPART_SUBTYPE = Property.internalText(MULTIPART_PREFIX + "subtype");

    Property MULTIPART_BOUNDARY = Property.internalText(MULTIPART_PREFIX + "boundary");

    /**
     * Where possible, this records the value from the name field.
     * Even in MAPI messages, though, this can be an email address.
     */
    Property MESSAGE_FROM_NAME = Property.internalTextBag(MESSAGE_PREFIX + "from-name");

    /**
     * Where possible, this records the value from the name field.
     * Even in MAPI messages, though, this can be a name.
     * <p/>
     * Note that the value may also be an X400/x500 Exchange format:
     * /o=ExchangeLabs/ou=Exchange Administrative Group/cn=Recipients/cn=someone.or.other
     */
    Property MESSAGE_FROM_EMAIL = Property.internalTextBag(MESSAGE_PREFIX + "from-email");

    /**
     * In Outlook messages, there are sometimes separate fields for "to-name" and
     * "to-display-name" name.
     */
    Property MESSAGE_TO_NAME = Property.internalTextBag(MESSAGE_PREFIX + "to-name");

    Property MESSAGE_TO_DISPLAY_NAME = Property.internalTextBag(MESSAGE_PREFIX + "to-display-name");

    /**
     * Where possible, this records the email value in the to field.
     * Even in MAPI messages, though, this can be a name.
     * <p/>
     * Note that the value may also be an X400/x500 Exchange format:
     * /o=ExchangeLabs/ou=Exchange Administrative Group/cn=Recipients/cn=someone.or.other
     */
    Property MESSAGE_TO_EMAIL = Property.internalTextBag(MESSAGE_PREFIX + "to-email");

    /**
     * In Outlook messages, there are sometimes separate fields for "cc-name" and
     * "cc-display-name" name.
     */
    Property MESSAGE_CC_NAME = Property.internalTextBag(MESSAGE_PREFIX + "cc-name");

    Property MESSAGE_CC_DISPLAY_NAME = Property.internalTextBag(MESSAGE_PREFIX + "cc-display-name");

    /**
     * Where possible, this records the email value in the cc field.
     * Even in MAPI messages, though, this can be a name.
     * <p/>
     * Note that the value may also be an X400/x500 Exchange format:
     * /o=ExchangeLabs/ou=Exchange Administrative Group/cn=Recipients/cn=someone.or.other
     */
    Property MESSAGE_CC_EMAIL = Property.internalTextBag(MESSAGE_PREFIX + "cc-email");

    /**
     * In Outlook messages, there are sometimes separate fields for "bcc-name" and
     * "bcc-display-name" name.
     */
    Property MESSAGE_BCC_NAME = Property.internalTextBag(MESSAGE_PREFIX + "bcc-name");

    Property MESSAGE_BCC_DISPLAY_NAME =
            Property.internalTextBag(MESSAGE_PREFIX + "bcc-display-name");

    /**
     * Where possible, this records the email value in the bcc field.
     * Even in MAPI messages, though, this can be a name.
     * <p/>
     * Note that the value may also be an X400/x500 Exchange format:
     * /o=ExchangeLabs/ou=Exchange Administrative Group/cn=Recipients/cn=someone.or.other
     */
    Property MESSAGE_BCC_EMAIL = Property.internalTextBag(MESSAGE_PREFIX + "bcc-email");

}
