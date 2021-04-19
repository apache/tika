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

package org.apache.tika.server;

import org.apache.cxf.jaxrs.ext.ResourceComparator;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;

/**
 * Resource comparator based to produce type.
 * In an ambiguous call, request handler will be chosen based on the type of data it returns.
 */
public class ProduceTypeResourceComparator implements ResourceComparator {

    /**
     * The logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProduceTypeResourceComparator.class);

    /**
     * The prioritized MediaType list.
     * The lower in list (higher index value), the higher priority it has.
     * In case of no matching in this list, it will be treated as media type all.
     */
    public static final List<MediaType> PRIORITIZED_MEDIA_LIST =
            Arrays.asList(
                    MediaType.TEXT_PLAIN_TYPE,
                    MediaType.APPLICATION_JSON_TYPE,
                    MediaType.TEXT_HTML_TYPE,
                    MediaType.TEXT_XML_TYPE
            );

    /**
     * Initiates the comparator.
     * Currently, no op.
     */
    public ProduceTypeResourceComparator() {
    }

    /**
     * Compares the class to handle.
     * Nothing is handled here, and this is handed over to CXF default logic.
     * @param cri1 the first class resource info.
     * @param cri2 the second class resource info.
     * @param message the message, for comparison context.
     * @return value based on chosen class. (Always 0 is returned)
     */
    @Override
    public int compare(ClassResourceInfo cri1, ClassResourceInfo cri2, Message message) {
        return 0;
    }

    /**
     * Compares the method to handle.
     * Gets the maximum priority match for both handlers,
     * and chooses the handler that has the maximum priority match.
     * @param oper1 the first resource handler info.
     * @param oper2 the second resource handler info.
     * @param message the message, for comparison context.
     * @return value based on chosen handler. Returns -1 if first, 1 if second and 0 if no decision.
     */
    @Override
    public int compare(OperationResourceInfo oper1, OperationResourceInfo oper2, Message message) {
        // getting all message context data
        final String httpMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        final MediaType contentType = JAXRSUtils.toMediaType((String) message
                .get(Message.CONTENT_TYPE));
        final List<MediaType> acceptTypes = JAXRSUtils.parseMediaTypes((String) message.get(
                Message.ACCEPT_CONTENT_TYPE));

        LOG.debug("Message Method : " + httpMethod + ", ContentType : " + contentType + ", "
                + "Accept Types : " + acceptTypes);

        int result = compareProduceTypes(oper1, oper2, acceptTypes);

        String m1Name =
                oper1.getClassResourceInfo().getServiceClass().getName() + "#"
                        + oper1.getMethodToInvoke().getName();
        String m2Name =
                oper2.getClassResourceInfo().getServiceClass().getName() + "#"
                        + oper2.getMethodToInvoke().getName();

        if (result != 0) {
            String chosen = result == -1 ? m1Name : m2Name;
            LOG.debug("Between " + m1Name + " and " + m2Name + ", "
                    + chosen + " is chosen for handling the current request");
        }

        return result;
    }

    /**
     * Compares the method to handle.
     * Gets the maximum priority match for both handlers,
     * and chooses the handler that has the maximum priority match.
     * @param oper1 the first resource handler info.
     * @param oper2 the second resource handler info.
     * @param acceptTypes the list acceptable response mime type, for the message.
     * @return value based on chosen handler. Returns -1 if first, 1 if second and 0 if no decision.
     */
    private int compareProduceTypes(OperationResourceInfo oper1, OperationResourceInfo oper2,
                        final List<MediaType> acceptTypes) {
        // getting matched produce type for both handlers.
        // this is required if a method can produce multiple types.
        List<MediaType> op1Matched = JAXRSUtils.intersectMimeTypes(acceptTypes,
                oper1.getProduceTypes(), true);
        List<MediaType> op2Matched = JAXRSUtils.intersectMimeTypes(acceptTypes,
                oper2.getProduceTypes(), true);

        // calculate the max priority for both handlers
        int oper1Priority = op1Matched.stream()
                .mapToInt(PRIORITIZED_MEDIA_LIST::indexOf)
                .max().getAsInt();
        int oper2Priority = op2Matched.stream()
                .mapToInt(PRIORITIZED_MEDIA_LIST::indexOf)
                .max().getAsInt();

        // final calculation
        return oper1Priority == oper2Priority ? 0 : (oper1Priority > oper2Priority ? -1 : 1);
    }

}