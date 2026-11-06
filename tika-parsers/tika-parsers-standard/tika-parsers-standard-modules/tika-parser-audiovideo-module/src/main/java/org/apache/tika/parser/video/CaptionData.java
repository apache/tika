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
package org.apache.tika.parser.video;

import java.util.Arrays;

/**
 * CEA-608 byte pairs from ATSC A/53 {@code cc_data}, as carried in an H.264 or HEVC SEI
 * message ({@code user_data_registered_itu_t_t35}, "GA94"). CEA-708 (DTVCC) packets in the
 * same structure are skipped.
 */
final class CaptionData {

    /** Receives each valid CEA-608 pair; field 0 is CC1/CC2, 1 is CC3/CC4. */
    interface PairSink {
        void pair(int field, int b1, int b2);
    }

    private static final int SEI_USER_DATA_REGISTERED = 4;

    private CaptionData() {
    }

    /**
     * Pairs from one SEI NAL unit.
     *
     * @param nal         the NAL unit including its header, emulation-prevention bytes intact
     * @param headerBytes 1 for H.264, 2 for HEVC
     * @return number of valid CEA-608 pairs sent, padding included
     */
    static int fromSei(byte[] nal, int headerBytes, PairSink sink) {
        byte[] rbsp = unescape(nal, headerBytes);
        int count = 0;
        // rbsp_trailing_bits: the last non-zero byte; 0x80 elsewhere is HEVC payloadType 128
        int trailing = rbsp.length - 1;
        while (trailing >= 0 && rbsp[trailing] == 0) {
            trailing--;
        }
        int i = 0;
        while (i < rbsp.length && i != trailing) {
            long type = 0;
            while (i < rbsp.length && (rbsp[i] & 0xFF) == 0xFF) {
                type += 255;
                i++;
            }
            if (i >= rbsp.length) {
                break;
            }
            type += rbsp[i++] & 0xFF;
            long size = 0;
            while (i < rbsp.length && (rbsp[i] & 0xFF) == 0xFF) {
                size += 255;
                i++;
            }
            if (i >= rbsp.length) {
                break;
            }
            size += rbsp[i++] & 0xFF;
            int end = (int) Math.min(rbsp.length, i + size);
            if (type == SEI_USER_DATA_REGISTERED) {
                count += t35(rbsp, i, end, sink);
            }
            i = end;
        }
        return count;
    }

    // country 0xB5 (US), provider 0x0031 (ATSC), "GA94", type code 3
    private static int t35(byte[] b, int i, int end, PairSink sink) {
        if (end - i < 8 || (b[i] & 0xFF) != 0xB5 || b[i + 1] != 0x00 || b[i + 2] != 0x31) {
            return 0;
        }
        int p = i + 3;
        if (b[p] != 'G' || b[p + 1] != 'A' || b[p + 2] != '9' || b[p + 3] != '4') {
            return 0;
        }
        p += 4;
        if (p >= end || (b[p] & 0xFF) != 0x03) {
            return 0;
        }
        return ccData(b, p + 1, end, sink);
    }

    /**
     * {@code cc_data()}: flags and cc_count, em_data, then cc_count triplets of marker, valid
     * flag and type, and two bytes. Returns the number of valid CEA-608 pairs sent.
     */
    static int ccData(byte[] b, int i, int end, PairSink sink) {
        if (end - i < 2) {
            return 0;
        }
        int flags = b[i] & 0xFF;
        if ((flags & 0x40) == 0) {
            return 0;
        }
        int count = flags & 0x1F;
        int p = i + 2;
        int sent = 0;
        for (int n = 0; n < count && p + 3 <= end; n++, p += 3) {
            int head = b[p] & 0xFF;
            boolean valid = (head & 0x04) != 0;
            int type = head & 0x03;
            if (valid && type < 2) {
                sink.pair(type, b[p + 1] & 0xFF, b[p + 2] & 0xFF);
                sent++;
            }
        }
        return sent;
    }

    /** The NAL payload after its header with emulation-prevention bytes (00 00 03) removed. */
    static byte[] unescape(byte[] nal, int from) {
        byte[] out = new byte[Math.max(0, nal.length - from)];
        int n = 0;
        int zeros = 0;
        for (int i = from; i < nal.length; i++) {
            int v = nal[i] & 0xFF;
            if (zeros >= 2 && v == 0x03) {
                zeros = 0;
                continue;
            }
            zeros = v == 0 ? zeros + 1 : 0;
            out[n++] = (byte) v;
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }
}
