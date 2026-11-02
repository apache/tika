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
 * A size-bounded copy of metadata-extractor 2.22.0's com.drew.imaging.mp4.Mp4Reader.
 * <p>
 * The library reader does {@code new byte[(int) boxSize - 8]} for every box a handler
 * accepts, with {@code boxSize} taken from the file and capped only at
 * {@code Integer.MAX_VALUE}, and {@code StreamReader.getBytes} allocates before checking
 * how much data is present, so one crafted box header forces a multi-GB allocation.
 * This walk is the library's except that an accepted box whose payload exceeds
 * {@code maxBoxSize}, or the input, is skipped instead of read. See TIKA-4812.
 */
final class TikaMp4Reader {

    //same limit as the library's walk
    private static final int MAX_BOX_DEPTH = 100;

    private TikaMp4Reader() {
    }

    /**
     * @param inputLength total input length in bytes, or -1 if unknown
     */
    static void extract(InputStream inputStream, Mp4BoxHandler handler, long maxBoxSize,
                        long inputLength) {
        StreamReader reader = new StreamReader(inputStream);
        reader.setMotorolaByteOrder(true);
        processBoxes(reader, -1, 0, handler, new Mp4Context(), maxBoxSize, inputLength);
    }

    private static void processBoxes(StreamReader reader, long atomEnd, int depth,
                                     Mp4Handler<?> handler, Mp4Context context,
                                     long maxBoxSize, long inputLength) {
        try {
            if (depth > MAX_BOX_DEPTH) {
                handler.addError("Box nesting too deep.");
                reader.skip(atomEnd - reader.getPosition());
                return;
            }
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
                    processBoxes(reader, reader.getPosition() + payloadLength, depth + 1,
                            processBox(handler, boxType, null, boxSize, context), context,
                            maxBoxSize, inputLength);
                } else if (acceptBox(handler, boxType)) {
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
            handler.addError(e.getMessage());
        }
    }

    //Mp4Handler's accept/process methods are protected; Mp4BoxHandler and the Mp4MediaHandler
    //track handlers swapped in on 'hdlr' widen them to public. Any other handler type accepts
    //nothing. processBox(type, null, ...) is what the library's processContainer does.

    private static boolean acceptContainer(Mp4Handler<?> handler, String type) {
        if (handler instanceof Mp4BoxHandler) {
            return ((Mp4BoxHandler) handler).shouldAcceptContainer(type);
        }
        return handler instanceof Mp4MediaHandler
                && ((Mp4MediaHandler<?>) handler).shouldAcceptContainer(type);
    }

    private static boolean acceptBox(Mp4Handler<?> handler, String type) {
        if (handler instanceof Mp4BoxHandler) {
            return ((Mp4BoxHandler) handler).shouldAcceptBox(type);
        }
        return handler instanceof Mp4MediaHandler
                && ((Mp4MediaHandler<?>) handler).shouldAcceptBox(type);
    }

    private static Mp4Handler<?> processBox(Mp4Handler<?> handler, String type, byte[] payload,
                                            long boxSize, Mp4Context context) throws IOException {
        return handler instanceof Mp4BoxHandler
                ? ((Mp4BoxHandler) handler).processBox(type, payload, boxSize, context)
                : ((Mp4MediaHandler<?>) handler).processBox(type, payload, boxSize, context);
    }
}
