package org.apache.tika.server;
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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.poi.hwpf.OldWordFileFormatException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;

@Provider
public class TikaServerParseExceptionMapper implements ExceptionMapper<TikaServerParseException> {

    private final boolean returnStack;

    public TikaServerParseExceptionMapper(boolean returnStack) {
        this.returnStack = returnStack;
    }

    public Response toResponse(TikaServerParseException e) {
        if (e.getMessage().equals(Response.Status.UNSUPPORTED_MEDIA_TYPE.toString())) {
            return buildResponse(e, 415);
        }
        Throwable cause = e.getCause();
        if (cause == null) {
            return buildResponse(e, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        } else {
            if (cause instanceof EncryptedDocumentException) {
                return buildResponse(cause, 422);
            } else if (cause instanceof TikaException) {
                //unsupported media type
                Throwable causeOfCause = cause.getCause();
                if (causeOfCause instanceof WebApplicationException) {
                    return ((WebApplicationException) causeOfCause).getResponse();
                }
                return buildResponse(cause, 422);
            } else if (cause instanceof IllegalStateException) {
                return buildResponse(cause, 422);
            } else if (cause instanceof OldWordFileFormatException) {
                return buildResponse(cause, 422);
            } else if (cause instanceof WebApplicationException) {
                return ((WebApplicationException) e.getCause()).getResponse();
            } else {
                return buildResponse(e, 500);
            }
        }
    }

    private Response buildResponse(Throwable cause, int i) {
        if (returnStack && cause != null) {
            Writer result = new StringWriter();
            PrintWriter writer = new PrintWriter(result);
            cause.printStackTrace(writer);
            writer.flush();
            try {
                result.flush();
            } catch (IOException e) {
                //something went seriously wrong
                return Response.status(500).build();
            }
            return Response.status(i).entity(result.toString()).type("text/plain").build();
        } else {
            return Response.status(i).build();
        }
    }
}
