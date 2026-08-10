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

    //MP4 containers nest (moov/trak/mdia/minf/stbl/udta/meta); cap the recursion so a
    //crafted chain of nested container headers cannot overflow the stack (an uncaught
    //Error, caught by neither the IOException handler below nor CompositeParser). Real
    //files nest well under this.
    private static final int MAX_BOX_DEPTH = 100;

    /**
     * @param inputLength total input length in bytes, or -1 if unknown. When known, a box
     *                    that declares more payload than the input holds is skipped rather
     *                    than allocated (StreamReader.getBytes allocates before reading).
     */
    static void extract(InputStream inputStream, Mp4BoxHandler handler, long maxBoxSize,
                        long inputLength) {
        StreamReader reader = new StreamReader(inputStream);
        reader.setMotorolaByteOrder(true);
        processBoxes(reader, -1, handler, new Mp4Context(), maxBoxSize, inputLength, 0);
    }

    private static void processBoxes(StreamReader reader, long atomEnd, Mp4Handler<?> handler,
                                     Mp4Context context, long maxBoxSize, long inputLength,
                                     int depth) {
        if (depth > MAX_BOX_DEPTH) {
            handler.addError("MP4 box nesting exceeds the maximum depth of " + MAX_BOX_DEPTH);
            return;
        }
        try {
            while (atomEnd == -1 || reader.getPosition() < atomEnd) {
                long boxSize = reader.getUInt32();
                String boxType = reader.getString(4);
                //4 bytes size + 4 bytes type, plus 8 more when a 64-bit largesize follows
                int headerSize = boxSize == 1 ? 16 : 8;
                if (headerSize == 16) {
                    boxSize = reader.getInt64();
                }
                if (boxSize > Integer.MAX_VALUE) {
                    handler.addError("Box size too large.");
                    break;
                }
                if (boxSize < headerSize) {
                    handler.addError("Box size too small.");
                    break;
                }

                long payloadLength = boxSize - headerSize;
                if (acceptContainer(handler, boxType)) {
                    processBoxes(reader, reader.getPosition() + payloadLength,
                            processBox(handler, boxType, null, boxSize, context), context,
                            maxBoxSize, inputLength, depth + 1);
                } else if (acceptBox(handler, boxType)) {
                    //StreamReader.getBytes allocates the whole payload up front, so skip
                    //(a lazy stream advance) any box over the cap, or one that claims more
                    //than the input holds, instead of allocating it. Skip-and-continue is
                    //deliberate: unlike the TikaMemoryLimitException other parsers throw,
                    //this keeps the remaining boxes' metadata; the skip is recorded as a
                    //warning via the directory's error list.
                    boolean tooLarge = payloadLength > maxBoxSize;
                    boolean beyondInput = inputLength >= 0
                            && reader.getPosition() + payloadLength > inputLength;
                    if (tooLarge || beyondInput) {
                        handler.addError("MP4 box '" + boxType + "' payload (" + payloadLength
                                + " bytes) exceeds the "
                                + (tooLarge ? "maximum of " + maxBoxSize + " bytes" : "input size")
                                + "; skipping.");
                        reader.skip(payloadLength);
                    } else {
                        handler = processBox(handler, boxType,
                                reader.getBytes((int) payloadLength), boxSize, context);
                    }
                } else {
                    reader.skip(payloadLength);
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
