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
package org.apache.tika.parser.mp4;

import java.io.IOException;
import java.io.InputStream;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.lang.StreamReader;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.Mp4MediaHandler;

/**
 * A size-bounded reimplementation of com.drew.imaging.mp4.Mp4Reader.
 * <p>
 * The metadata-extractor reader eagerly does {@code new byte[(int) boxSize - 8]}
 * for every box a handler accepts, with {@code boxSize} attacker-controlled and
 * capped only at {@code Integer.MAX_VALUE} (~2GB), and {@code StreamReader.getBytes}
 * allocates before checking how much data is actually present. A single crafted
 * box header therefore forces a multi-GB allocation. This reader is identical to
 * the library's box walk except that an accepted box whose payload exceeds
 * {@code maxBoxSize} is skipped (a lazy stream advance, no allocation) instead of
 * being read. Boxes the handler does not accept were already skipped by the
 * library, so this only bounds the boxes we opt into. See TIKA-4812.
 */
final class TikaMp4Reader {

    private TikaMp4Reader() {
    }

    static void extract(InputStream inputStream, Mp4BoxHandler handler, long maxBoxSize) {
        StreamReader reader = new StreamReader(inputStream);
        reader.setMotorolaByteOrder(true);
        processBoxes(reader, -1, handler, new Mp4Context(), maxBoxSize);
    }

    private static void processBoxes(StreamReader reader, long atomEnd, Mp4Handler<?> handler,
                                     Mp4Context context, long maxBoxSize) {
        try {
            while (atomEnd == -1 || reader.getPosition() < atomEnd) {
                long boxSize = reader.getUInt32();
                String boxType = reader.getString(4);
                boolean isLargeSize = boxSize == 1;
                if (isLargeSize) {
                    boxSize = reader.getInt64();
                }
                if (boxSize > Integer.MAX_VALUE) {
                    handler.addError("Box size too large.");
                    break;
                }
                if (boxSize < 8) {
                    handler.addError("Box size too small.");
                    break;
                }

                if (acceptContainer(handler, boxType)) {
                    processBoxes(reader, boxSize + reader.getPosition() - 8,
                            processBox(handler, boxType, null, boxSize, context), context,
                            maxBoxSize);
                } else if (acceptBox(handler, boxType)) {
                    long payloadLength = boxSize - 8;
                    if (payloadLength > maxBoxSize) {
                        handler.addError("MP4 box '" + boxType + "' payload (" + payloadLength
                                + " bytes) exceeds the maximum of " + maxBoxSize
                                + " bytes; skipping.");
                        reader.skip(payloadLength);
                    } else {
                        handler = processBox(handler, boxType,
                                reader.getBytes((int) payloadLength), boxSize, context);
                    }
                } else if (isLargeSize) {
                    if (boxSize < 16) {
                        break;
                    }
                    reader.skip(boxSize - 16);
                } else {
                    reader.skip(boxSize - 8);
                }
            }
        } catch (IOException e) {
            handler.addError(e.getMessage() == null ? "IOException reading MP4 boxes"
                    : e.getMessage());
        }
    }

    //the box walk holds handlers as Mp4Handler, whose accept/process methods are
    //protected; every concrete handler in play (Mp4BoxHandler-rooted, or an
    //Mp4MediaHandler track handler swapped in on 'hdlr') widens them to public,
    //so dispatch through whichever of the two families the instance belongs to.
    //A container's handler is obtained with processBox(type, null, ...), which is
    //exactly what the library's protected processContainer does.

    private static boolean acceptContainer(Mp4Handler<?> handler, String type) {
        return handler instanceof Mp4BoxHandler
                ? ((Mp4BoxHandler) handler).shouldAcceptContainer(type)
                : ((Mp4MediaHandler<?>) handler).shouldAcceptContainer(type);
    }

    private static boolean acceptBox(Mp4Handler<?> handler, String type) {
        return handler instanceof Mp4BoxHandler
                ? ((Mp4BoxHandler) handler).shouldAcceptBox(type)
                : ((Mp4MediaHandler<?>) handler).shouldAcceptBox(type);
    }

    private static Mp4Handler<?> processBox(Mp4Handler<?> handler, String type, byte[] payload,
                                            long boxSize, Mp4Context context) throws IOException {
        return handler instanceof Mp4BoxHandler
                ? ((Mp4BoxHandler) handler).processBox(type, payload, boxSize, context)
                : ((Mp4MediaHandler<?>) handler).processBox(type, payload, boxSize, context);
    }
}
