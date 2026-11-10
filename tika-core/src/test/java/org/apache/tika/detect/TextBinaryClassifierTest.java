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
package org.apache.tika.detect;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

public class TextBinaryClassifierTest {

    private static final String RUSSIAN = "Банк России сообщает, что с 1 июля изменяются правила расчёта "
            + "обязательных резервов для кредитных организаций.\r\nПросим довести настоящее письмо до "
            + "сведения всех заинтересованных лиц и обеспечить его исполнение в установленный срок.\r\n";
    private static final String CHINESE = "关于做好二〇一九年度预算执行情况审计工作的通知\r\n各区县人民政府，"
            + "市政府各部门：为进一步加强财政资金管理，提高资金使用效益，现将有关事项通知如下。\r\n"
            + "一、高度重视审计工作，切实落实主体责任。二、严格按照规定时限报送材料。\r\n";
    private static final String JAPANESE = "第三十回全国大会のご案内\n日時：二〇一八年十月十三日（土）午前十時より\n"
            + "会場：東京国際フォーラム　ホールＢ\n参加をご希望の方は、事務局までお申し込みください。\n"
            + "詳しくは当会ホームページをご覧ください。皆様のご参加をお待ちしております。\n";
    private static final String ENGLISH = "Release notes for version 2.4.1\n\n"
            + "Fixed a crash when the configuration file contains an empty section.\n"
            + "The installer now keeps existing settings when upgrading in place.\n"
            + "Thanks to everyone who reported problems on the mailing list.\n";

    private static byte[] repeat(byte[] unit, int times) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < times; i++) {
            out.writeBytes(unit);
        }
        return out.toByteArray();
    }

    private static void assertText(byte[] data) {
        assertTrue(TextBinaryClassifier.isText(data), "p=" + TextBinaryClassifier.probability(data));
    }

    private static void assertBinary(byte[] data) {
        assertFalse(TextBinaryClassifier.isText(data), "p=" + TextBinaryClassifier.probability(data));
    }

    @Test
    public void legacyAndUnicodeText() {
        assertText(repeat(RUSSIAN.getBytes(Charset.forName("windows-1251")), 4));
        assertText(repeat(CHINESE.getBytes(Charset.forName("GB18030")), 4));
        assertText(repeat(JAPANESE.getBytes(Charset.forName("EUC-JP")), 4));
        assertText(repeat(JAPANESE.getBytes(Charset.forName("Shift_JIS")), 4));
        assertText(repeat((RUSSIAN + CHINESE + ENGLISH).getBytes(UTF_8), 3));
    }

    @Test
    public void textWithControls() {
        // a stray bell and form feed in an otherwise plain log
        String log = "2019-03-04 10:15:02 INFO  starting worker pool size=8\n"
                + "2019-03-04 10:15:03 WARN  disk usage at 91% on /var\u0007\n"
                + "2019-03-04 10:15:09 INFO  checkpoint written in 412 ms\n\f"
                + "2019-03-04 10:16:00 INFO  shutting down\n";
        assertText(repeat(log.getBytes(US_ASCII), 6));

        // a preallocated log whose middle block was never written
        byte[] hole = repeat(log.getBytes(US_ASCII), 12);
        Arrays.fill(hole, hole.length / 3, hole.length / 3 + 600, (byte) 0);
        assertText(hole);

        // an installer log with a backspace-driven progress counter
        StringBuilder sb = new StringBuilder("Extracting files to C:\\Program Files\\Example\r\n");
        for (int pct = 0; pct <= 100; pct += 5) {
            sb.append(String.format(java.util.Locale.ROOT, "%3d%%", pct)).append("\b\b\b\b");
        }
        sb.append("\r\nInstallation finished. Restart the computer to complete setup.\r\n");
        assertText(repeat(sb.toString().getBytes(US_ASCII), 4));
    }

    @Test
    public void binary() {
        byte[] random = new byte[8192];
        new Random(5029).nextBytes(random);
        assertBinary(random);

        byte[] fill = repeat(new byte[] {(byte) 0xC7, (byte) 0xC7, (byte) 0xC7, (byte) 0xC7, (byte) 0xC7,
                (byte) 0xC7, (byte) 0xC7, (byte) 0xC7, (byte) 0xC7, (byte) 0xC7, '\n'}, 40);
        assertBinary(fill);

        byte[] pcm = new byte[4000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) (128 + 90 * Math.sin(2 * Math.PI * 440 * i / 8000.0));
        }
        assertBinary(pcm);

        // Elf64_Sym records: name, info, other, shndx, value, size
        ByteBuffer sym = ByteBuffer.allocate(24 * 64).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 64; i++) {
            sym.putInt(1 + 17 * i).put((byte) 0x12).put((byte) 0).putShort((short) 14)
                    .putLong(0x401000L + 0x40L * i).putLong(32 + i);
        }
        assertBinary(sym.array());
    }

    @Test
    public void printableAsciiIsText() {
        // a low-diversity payload the model alone scores binary (p 0.79)
        byte[] runs = "XXXXXXXXXXYYYYYYYYYY".getBytes(US_ASCII);
        assertTrue(TextBinaryClassifier.probability(runs) < 0.837);
        assertText(runs);
    }

    @Test
    public void shortInputIsNotClassified() {
        assertFalse(TextBinaryClassifier.isText("fifteen bytes!!".getBytes(US_ASCII)));
    }

    @Test
    public void mimeTypesUsesClassifier() throws Exception {
        MimeTypes types = MimeTypes.getDefaultMimeTypes();
        byte[] russian = repeat(RUSSIAN.getBytes(Charset.forName("windows-1251")), 4);
        assertEquals(MediaType.TEXT_PLAIN, detect(types, russian));
        byte[] fill = repeat(new byte[] {(byte) 0xC7, (byte) 0xC7, (byte) 0xC7, (byte) 0xC7, '\n'}, 60);
        assertEquals(MediaType.OCTET_STREAM, detect(types, fill));
        // 15 bytes stay on the control-byte check
        assertEquals(MediaType.TEXT_PLAIN, detect(types, "fifteen bytes!!".getBytes(US_ASCII)));
        assertEquals(MediaType.OCTET_STREAM, detect(types, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}));
    }

    private static MediaType detect(MimeTypes types, byte[] data) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            return types.detect(tis, new Metadata(), new ParseContext());
        }
    }
}
