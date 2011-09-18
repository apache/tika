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
package org.apache.tika.parser.chm.lzx;

import java.math.BigInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmCommons.IntelState;
import org.apache.tika.parser.chm.core.ChmCommons.LzxState;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * Decompresses a chm block. Depending on chm block type chooses most relevant
 * decompressing method. A chm block type can be as follows:</br> <li>UNDEFINED
 * - no action taken, i.e. skipping the block <li>VERBATIM <li>ALIGNED_OFFSET
 * <li>UNCOMPRESSED the most simplest In addition there are unknown types (4-7).
 * Currently relying on previous chm block these types changing according to the
 * previous chm block type. We need to invent more appropriate way to handle
 * such types.
 * 
 */
public class ChmLzxBlock {
    private int block_number;
    private long block_length;
    private ChmLzxState state;
    private byte[] content = null;
    private ChmSection chmSection = null;
    private int contentLength = 0;

    // trying to find solution for bad blocks ...
    private int previousBlockType = -1;

    public ChmLzxBlock(int blockNumber, byte[] dataSegment, long blockLength,
            ChmLzxBlock prevBlock) {
        try {
            if (validateConstructorParams(blockNumber, dataSegment, blockLength)) {
                setBlockNumber(blockNumber);

                if (prevBlock != null
                        && prevBlock.getState().getBlockLength() > prevBlock
                                .getState().getBlockRemaining())
                    setChmSection(new ChmSection(prevBlock.getContent()));
                else
                    setChmSection(new ChmSection(dataSegment));

                setBlockLength(blockLength);

                // ============================================
                // we need to take care of previous context
                // ============================================
                checkLzxBlock(prevBlock);
                setContent((int) blockLength);
                if (prevBlock == null
                        || getContent().length < (int) getBlockLength()) {
                    setContent((int) getBlockLength());
                }

                if (prevBlock != null && prevBlock.getState() != null)
                    previousBlockType = prevBlock.getState().getBlockType();

                extractContent();
            } else
                throw new TikaException("Check your chm lzx block parameters");
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    protected int getContentLength() {
        return contentLength;
    }

    protected void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    private ChmSection getChmSection() {
        return chmSection;
    }

    private void setChmSection(ChmSection chmSection) {
        this.chmSection = chmSection;
    }

    private void assertStateNotNull() throws TikaException {
        if (getState() == null)
            throw new ChmParsingException("state is null");
    }

    private void extractContent() throws TikaException {
        assertStateNotNull();
        if (getChmSection().getData() != null) {
            while (getContentLength() < getBlockLength()) {// && tempStopLoop
                if (getState() != null && getState().getBlockRemaining() == 0) {
                    if (getState().getHadStarted() == LzxState.NOT_STARTED_DECODING) {
                        getState().setHadStarted(LzxState.STARTED_DECODING);
                        if (getChmSection().getSyncBits(1) == 1) {
                            int intelSizeTemp = (getChmSection()
                                    .getSyncBits(16) << 16)
                                    + getChmSection().getSyncBits(16);
                            if (intelSizeTemp >= 0)
                                getState().setIntelFileSize(intelSizeTemp);
                            else
                                getState().setIntelFileSize(0);
                        }
                    }
                    getState().setBlockType(getChmSection().getSyncBits(3));
                    getState().setBlockLength(
                            (getChmSection().getSyncBits(16) << 8)
                                    + getChmSection().getSyncBits(8));
                    getState().setBlockRemaining(getState().getBlockLength());

                    // ----------------------------------------
                    // Trying to handle 3 - 7 block types
                    // ----------------------------------------
                    if (getState().getBlockType() > 3) {
                        if (previousBlockType >= 0 && previousBlockType < 3)
                            getState().setBlockType(previousBlockType);
                    }

                    switch (getState().getBlockType()) {
                    case ChmCommons.ALIGNED_OFFSET:
                        createAlignedTreeTable();
                    case ChmCommons.VERBATIM:
                        /* Creates mainTreeTable */
                        createMainTreeTable();
                        createLengthTreeTable();
                        if (getState().getMainTreeLengtsTable()[0xe8] != 0)
                            getState().setIntelState(IntelState.STARTED);
                        break;
                    case ChmCommons.UNCOMPRESSED:
                        getState().setIntelState(IntelState.STARTED);
                        if (getChmSection().getTotal() > 16)
                            getChmSection().setSwath(
                                    getChmSection().getSwath() - 1);
                        getState().setR0(
                                (new BigInteger(getChmSection()
                                        .reverseByteOrder(
                                                getChmSection().unmarshalBytes(
                                                        4))).longValue()));
                        getState().setR1(
                                (new BigInteger(getChmSection()
                                        .reverseByteOrder(
                                                getChmSection().unmarshalBytes(
                                                        4))).longValue()));
                        getState().setR2(
                                (new BigInteger(getChmSection()
                                        .reverseByteOrder(
                                                getChmSection().unmarshalBytes(
                                                        4))).longValue()));
                        break;
                    default:
                        break;
                    }
                }

                int tempLen;

                if (getContentLength() + getState().getBlockRemaining() > getBlockLength()) {
                    getState().setBlockRemaining(
                            getContentLength() + getState().getBlockRemaining()
                                    - (int) getBlockLength());
                    tempLen = (int) getBlockLength();
                } else {
                    tempLen = getContentLength()
                            + getState().getBlockRemaining();
                    getState().setBlockRemaining(0);
                }

                switch (getState().getBlockType()) {
                case ChmCommons.ALIGNED_OFFSET:
                    // if(prevblock.lzxState.length>prevblock.lzxState.remaining)
                    decompressAlignedBlock(tempLen, getChmSection().getData());// prevcontext
                    break;
                case ChmCommons.VERBATIM:
                    decompressVerbatimBlock(tempLen, getChmSection().getData());
                    break;
                case ChmCommons.UNCOMPRESSED:
                    decompressUncompressedBlock(tempLen, getChmSection()
                            .getData());
                    break;
                }
                getState().increaseFramesRead();
                if ((getState().getFramesRead() < 32768)
                        && getState().getIntelFileSize() != 0)
                    intelE8Decoding();
            }
        }
    }

    protected void intelE8Decoding() {
        if (getBlockLength() <= ChmConstants.LZX_PRETREE_TABLEBITS
                || (getState().getIntelState() == IntelState.NOT_STARTED)) {
            getState().setBlockRemaining(
                    getState().getBlockRemaining() - (int) getBlockLength());
        } else {
            long curpos = getState().getBlockRemaining();
            getState().setBlockRemaining(
                    getState().getBlockRemaining() - (int) getBlockLength());
            int i = 0;
            while (i < getBlockLength() - 10) {
                if (content[i] != 0xe8) {
                    i++;
                    continue;
                }
                byte[] b = new byte[4];
                b[0] = getContent()[i + 3];
                b[1] = getContent()[i + 2];
                b[2] = getContent()[i + 1];
                b[3] = getContent()[i + 0];
                long absoff = (new BigInteger(b)).longValue();
                if ((absoff >= -curpos)
                        && (absoff < getState().getIntelFileSize())) {
                    long reloff = (absoff >= 0) ? absoff - curpos : absoff
                            + getState().getIntelFileSize();
                    getContent()[i + 0] = (byte) reloff;
                    getContent()[i + 1] = (byte) (reloff >>> 8);
                    getContent()[i + 2] = (byte) (reloff >>> 16);
                    getContent()[i + 3] = (byte) (reloff >>> 24);
                }
                i += 4;
                curpos += 5;
            }
        }
    }

    private short[] createPreLenTable() {
        short[] tmp = new short[ChmConstants.LZX_PRETREE_MAXSYMBOLS];
        for (int i = 0; i < ChmConstants.LZX_PRETREE_MAXSYMBOLS; i++) {
            tmp[i] = (short) getChmSection().getSyncBits(
                    ChmConstants.LZX_PRETREE_NUM_ELEMENTS_BITS);
        }
        return tmp;
    }

    private void createLengthTreeTable() throws TikaException {
        short[] prelentable = createPreLenTable();

        if (prelentable == null) {
            throw new ChmParsingException("pretreetable is null");
        }

        short[] pretreetable = createTreeTable2(prelentable,
                (1 << ChmConstants.LZX_PRETREE_TABLEBITS)
                        + (ChmConstants.LZX_PRETREE_MAXSYMBOLS << 1),
                ChmConstants.LZX_PRETREE_TABLEBITS,
                ChmConstants.LZX_PRETREE_MAXSYMBOLS);

        if (pretreetable == null) {
            throw new ChmParsingException("pretreetable is null");
        }

        createLengthTreeLenTable(0, ChmConstants.LZX_NUM_SECONDARY_LENGTHS,
                pretreetable, prelentable);

        getState().setLengthTreeTable(
                createTreeTable2(getState().getLengthTreeLengtsTable(),
                        (1 << ChmConstants.LZX_MAINTREE_TABLEBITS)
                                + (ChmConstants.LZX_LENGTH_MAXSYMBOLS << 1),
                        ChmConstants.LZX_MAINTREE_TABLEBITS,
                        ChmConstants.LZX_NUM_SECONDARY_LENGTHS));
    }

    public void decompressUncompressedBlock(int len, byte[] prevcontent) {
        if (getContentLength() + getState().getBlockRemaining() <= getBlockLength()) {
            for (int i = getContentLength(); i < (getContentLength() + getState()
                    .getBlockRemaining()); i++)
                content[i] = getChmSection().getByte();

            setContentLength(getContentLength()
                    + getState().getBlockRemaining());
            getState().setBlockRemaining(0);
        } else {
            for (int i = getContentLength(); i < getBlockLength(); i++)
                content[i] = getChmSection().getByte();
            getState().setBlockRemaining(
                    (int) getBlockLength() - getContentLength());// = blockLen -
                                                                 // contentlen;
            setContentLength((int) getBlockLength());
        }
    }

    public void decompressAlignedBlock(int len, byte[] prevcontent) throws TikaException {

        if ((getChmSection() == null) || (getState() == null)
                || (getState().getMainTreeTable() == null))
            throw new ChmParsingException("chm section is null");

        short s;
        int x, i, border;
        int matchlen = 0, matchfooter = 0, extra, rundest, runsrc;
        int matchoffset = 0;
        for (i = getContentLength(); i < len; i++) {
            /* new code */
            border = getChmSection().getDesyncBits(
                    ChmConstants.LZX_MAINTREE_TABLEBITS, 0);
            if (border >= getState().mainTreeTable.length)
                break;
            /* end new code */
            s = getState().mainTreeTable[getChmSection().getDesyncBits(
                    ChmConstants.LZX_MAINTREE_TABLEBITS, 0)];
            if (s >= getState().getMainTreeElements()) {
                x = ChmConstants.LZX_MAINTREE_TABLEBITS;
                do {
                    x++;
                    s <<= 1;
                    s += getChmSection().checkBit(x);
                } while ((s = getState().mainTreeTable[s]) >= getState()
                        .getMainTreeElements());
            }
            getChmSection().getSyncBits(getState().mainTreeTable[s]);
            if (s < ChmConstants.LZX_NUM_CHARS) {
                content[i] = (byte) s;
            } else {
                s -= ChmConstants.LZX_NUM_CHARS;
                matchlen = s & ChmConstants.LZX_NUM_PRIMARY_LENGTHS;
                if (matchlen == ChmConstants.LZX_NUM_PRIMARY_LENGTHS) {
                    matchfooter = getState().lengthTreeTable[getChmSection()
                            .getDesyncBits(ChmConstants.LZX_MAINTREE_TABLEBITS,
                                    0)];
                    if (matchfooter >= ChmConstants.LZX_MAINTREE_TABLEBITS) {
                        x = ChmConstants.LZX_MAINTREE_TABLEBITS;
                        do {
                            x++;
                            matchfooter <<= 1;
                            matchfooter += getChmSection().checkBit(x);
                        } while ((matchfooter = getState().lengthTreeTable[matchfooter]) >= ChmConstants.LZX_NUM_SECONDARY_LENGTHS);
                    }
                    getChmSection().getSyncBits(
                            getState().lengthTreeLengtsTable[matchfooter]);
                    matchlen += matchfooter;
                }
                matchlen += ChmConstants.LZX_MIN_MATCH;
                matchoffset = s >>> 3;
                if (matchoffset > 2) {
                    extra = ChmConstants.EXTRA_BITS[matchoffset];
                    matchoffset = (ChmConstants.POSITION_BASE[matchoffset] - 2);
                    if (extra > 3) {
                        extra -= 3;
                        long l = getChmSection().getSyncBits(extra);
                        matchoffset += (l << 3);
                        int g = getChmSection().getDesyncBits(
                                ChmConstants.LZX_NUM_PRIMARY_LENGTHS, 0);
                        int t = getState().getAlignedTreeTable()[g];
                        if (t >= getState().getMainTreeElements()) {
                            x = ChmConstants.LZX_MAINTREE_TABLEBITS;
                            do {
                                x++;
                                t <<= 1;
                                t += getChmSection().checkBit(x);
                            } while ((t = getState().getAlignedTreeTable()[t]) >= getState()
                                    .getMainTreeElements());
                        }
                        getChmSection().getSyncBits(
                                getState().getAlignedTreeTable()[t]);
                        matchoffset += t;
                    } else if (extra == 3) {
                        int g = (int) getChmSection().getDesyncBits(
                                ChmConstants.LZX_NUM_PRIMARY_LENGTHS, 0);
                        int t = getState().getAlignedTreeTable()[g];
                        if (t >= getState().getMainTreeElements()) {
                            x = ChmConstants.LZX_MAINTREE_TABLEBITS;
                            do {
                                x++;
                                t <<= 1;
                                t += getChmSection().checkBit(x);
                            } while ((t = getState().getAlignedTreeTable()[t]) >= getState()
                                    .getMainTreeElements());
                        }
                        getChmSection().getSyncBits(
                                getState().getAlignedTreeTable()[t]);
                        matchoffset += t;
                    } else if (extra > 0) {
                        long l = getChmSection().getSyncBits(extra);
                        matchoffset += l;
                    } else
                        matchoffset = 1;
                    getState().setR2(getState().getR1());
                    getState().setR1(getState().getR0());
                    getState().setR0(matchoffset);
                } else if (matchoffset == 0) {
                    matchoffset = (int) getState().getR0();
                } else if (matchoffset == 1) {
                    matchoffset = (int) getState().getR1();
                    getState().setR1(getState().getR0());
                    getState().setR0(matchoffset);
                } else /** match_offset == 2 */
                {
                    matchoffset = (int) getState().getR2();
                    getState().setR2(getState().getR0());
                    getState().setR0(matchoffset);
                }
                rundest = i;
                runsrc = rundest - matchoffset;
                i += (matchlen - 1);
                if (i > len)
                    break;

                if (runsrc < 0) {
                    if (matchlen + runsrc <= 0) {
                        runsrc = prevcontent.length + runsrc;
                        while (matchlen-- > 0)
                            content[rundest++] = prevcontent[runsrc++];
                    } else {
                        runsrc = prevcontent.length + runsrc;
                        while (runsrc < prevcontent.length)
                            content[rundest++] = prevcontent[runsrc++];
                        matchlen = matchlen + runsrc - prevcontent.length;
                        runsrc = 0;
                        while (matchlen-- > 0)
                            content[rundest++] = content[runsrc++];
                    }

                } else {
                    /* copies any wrappes around source data */
                    while ((runsrc < 0) && (matchlen-- > 0)) {
                        content[rundest++] = content[(int) (runsrc + getBlockLength())];
                        runsrc++;
                    }
                    /* copies match data - no worries about destination wraps */
                    while (matchlen-- > 0)
                        content[rundest++] = content[runsrc++];
                }
            }
        }
        setContentLength(len);
    }

    private void assertShortArrayNotNull(short[] array) throws TikaException {
        if (array == null)
            throw new ChmParsingException("short[] is null");
    }

    private void decompressVerbatimBlock(int len, byte[] prevcontent) throws TikaException {
        short s;
        int x, i;
        int matchlen = 0, matchfooter = 0, extra, rundest, runsrc;
        int matchoffset = 0;
        for (i = getContentLength(); i < len; i++) {
            int f = (int) getChmSection().getDesyncBits(
                    ChmConstants.LZX_MAINTREE_TABLEBITS, 0);
            assertShortArrayNotNull(getState().getMainTreeTable());
            s = getState().getMainTreeTable()[f];
            if (s >= ChmConstants.LZX_MAIN_MAXSYMBOLS) {
                x = ChmConstants.LZX_MAINTREE_TABLEBITS;
                do {
                    x++;
                    s <<= 1;
                    s += getChmSection().checkBit(x);
                } while ((s = getState().getMainTreeTable()[s]) >= ChmConstants.LZX_MAIN_MAXSYMBOLS);
            }
            getChmSection().getSyncBits(getState().getMainTreeLengtsTable()[s]);
            if (s < ChmConstants.LZX_NUM_CHARS) {
                content[i] = (byte) s;
            } else {
                s -= ChmConstants.LZX_NUM_CHARS;
                matchlen = s & ChmConstants.LZX_NUM_PRIMARY_LENGTHS;
                if (matchlen == ChmConstants.LZX_NUM_PRIMARY_LENGTHS) {
                    matchfooter = getState().getLengthTreeTable()[(int) getChmSection()
                            .getDesyncBits(ChmConstants.LZX_LENGTH_TABLEBITS, 0)];
                    if (matchfooter >= ChmConstants.LZX_NUM_SECONDARY_LENGTHS) {
                        x = ChmConstants.LZX_LENGTH_TABLEBITS;
                        do {
                            x++;
                            matchfooter <<= 1;
                            matchfooter += getChmSection().checkBit(x);
                        } while ((matchfooter = getState().getLengthTreeTable()[matchfooter]) >= ChmConstants.LZX_NUM_SECONDARY_LENGTHS);
                    }
                    getChmSection().getSyncBits(
                            getState().getLengthTreeLengtsTable()[matchfooter]);
                    matchlen += matchfooter;
                }
                matchlen += ChmConstants.LZX_MIN_MATCH;
                // shorter than 2
                matchoffset = s >>> 3;
                if (matchoffset > 2) {
                    if (matchoffset != 3) { // should get other bits to retrieve
                                            // offset
                        extra = ChmConstants.EXTRA_BITS[matchoffset];
                        long l = getChmSection().getSyncBits(extra);
                        matchoffset = (int) (ChmConstants.POSITION_BASE[matchoffset] - 2 + l);
                    } else {
                        matchoffset = 1;
                    }
                    getState().setR2(getState().getR1());
                    getState().setR1(getState().getR0());
                    getState().setR0(matchoffset);
                } else if (matchoffset == 0) {
                    matchoffset = (int) getState().getR0();
                } else if (matchoffset == 1) {
                    matchoffset = (int) getState().getR1();
                    getState().setR1(getState().getR0());
                    getState().setR0(matchoffset);
                } else /* match_offset == 2 */
                {
                    matchoffset = (int) getState().getR2();
                    getState().setR2(getState().getR0());
                    getState().setR0(matchoffset);
                }
                rundest = i;
                runsrc = rundest - matchoffset;
                i += (matchlen - 1);
                if (i > len)
                    break;
                if (runsrc < 0) {
                    if (matchlen + runsrc <= 0) {
                        runsrc = prevcontent.length + runsrc;
                        while ((matchlen-- > 0) && (prevcontent != null)
                                && ((runsrc + 1) > 0))
                            if ((rundest < content.length)
                                    && (runsrc < content.length))
                                content[rundest++] = prevcontent[runsrc++];
                    } else {
                        runsrc = prevcontent.length + runsrc;
                        while (runsrc < prevcontent.length)
                            if ((rundest < content.length)
                                    && (runsrc < content.length))
                                content[rundest++] = prevcontent[runsrc++];
                        matchlen = matchlen + runsrc - prevcontent.length;
                        runsrc = 0;
                        while (matchlen-- > 0)
                            content[rundest++] = content[runsrc++];
                    }

                } else {
                    /* copies any wrapped source data */
                    while ((runsrc < 0) && (matchlen-- > 0)) {
                        content[rundest++] = content[(int) (runsrc + getBlockLength())];
                        runsrc++;
                    }
                    /* copies match data - no worries about destination wraps */
                    while (matchlen-- > 0) {
                        if ((rundest < content.length)
                                && (runsrc < content.length))
                            content[rundest++] = content[runsrc++];
                    }
                }
            }
        }
        setContentLength(len);
    }

    private void createLengthTreeLenTable(int offset, int tablelen,
            short[] pretreetable, short[] prelentable) throws TikaException {
        if (prelentable == null || getChmSection() == null
                || pretreetable == null || prelentable == null)
            throw new ChmParsingException("is null");

        int i = offset; // represents offset
        int z, y, x;// local counters
        while (i < tablelen) {
            z = pretreetable[(int) getChmSection().getDesyncBits(
                    ChmConstants.LZX_PRETREE_TABLEBITS, 0)];
            if (z >= ChmConstants.LZX_PRETREE_NUM_ELEMENTS) {// 1 bug, should be
                                                             // 20
                x = ChmConstants.LZX_PRETREE_TABLEBITS;
                do {
                    x++;
                    z <<= 1;
                    z += getChmSection().checkBit(x);
                } while ((z = pretreetable[z]) >= ChmConstants.LZX_PRETREE_NUM_ELEMENTS);
            }
            getChmSection().getSyncBits(prelentable[z]);
            if (z < 17) {
                z = getState().getLengthTreeLengtsTable()[i] - z;
                if (z < 0)
                    z = z + 17;
                getState().getLengthTreeLengtsTable()[i] = (short) z;
                i++;
            } else if (z == 17) {
                y = (int) getChmSection().getSyncBits(4);
                y += 4;
                for (int j = 0; j < y; j++)
                    if (i < getState().getLengthTreeLengtsTable().length)
                        getState().getLengthTreeLengtsTable()[i++] = 0;
            } else if (z == 18) {
                y = (int) getChmSection().getSyncBits(5);
                y += 20;
                for (int j = 0; j < y; j++)
                    if (i < getState().getLengthTreeLengtsTable().length)
                        getState().getLengthTreeLengtsTable()[i++] = 0;
            } else if (z == 19) {
                y = getChmSection().getSyncBits(1);
                y += 4;
                z = pretreetable[(int) getChmSection().getDesyncBits(
                        ChmConstants.LZX_PRETREE_TABLEBITS, 0)];
                if (z >= ChmConstants.LZX_PRETREE_NUM_ELEMENTS) {// 20
                    x = ChmConstants.LZX_PRETREE_TABLEBITS;// 6
                    do {
                        x++;
                        z <<= 1;
                        z += getChmSection().checkBit(x);
                    } while ((z = pretreetable[z]) >= ChmConstants.LZX_MAINTREE_TABLEBITS);
                }
                getChmSection().getSyncBits(prelentable[z]);
                z = getState().getLengthTreeLengtsTable()[i] - z;
                if (z < 0)
                    z = z + 17;
                for (int j = 0; j < y; j++)
                    getState().getLengthTreeLengtsTable()[i++] = (short) z;
            }
        }
    }

    private void createMainTreeTable() throws TikaException {
        short[] prelentable = createPreLenTable();
        short[] pretreetable = createTreeTable2(prelentable,
                (1 << ChmConstants.LZX_PRETREE_TABLEBITS)
                        + (ChmConstants.LZX_PRETREE_MAXSYMBOLS << 1),
                ChmConstants.LZX_PRETREE_TABLEBITS,
                ChmConstants.LZX_PRETREE_MAXSYMBOLS);
        createMainTreeLenTable(0, ChmConstants.LZX_NUM_CHARS, pretreetable,
                prelentable);
        prelentable = createPreLenTable();
        pretreetable = createTreeTable2(prelentable,
                (1 << ChmConstants.LZX_PRETREE_TABLEBITS)
                        + (ChmConstants.LZX_PRETREE_MAXSYMBOLS << 1),
                ChmConstants.LZX_PRETREE_TABLEBITS,
                ChmConstants.LZX_PRETREE_MAXSYMBOLS);
        createMainTreeLenTable(ChmConstants.LZX_NUM_CHARS,
                getState().mainTreeLengtsTable.length, pretreetable,
                prelentable);

        getState().setMainTreeTable(
                createTreeTable2(getState().mainTreeLengtsTable,
                        (1 << ChmConstants.LZX_MAINTREE_TABLEBITS)
                                + (ChmConstants.LZX_MAINTREE_MAXSYMBOLS << 1),
                        ChmConstants.LZX_MAINTREE_TABLEBITS, getState()
                                .getMainTreeElements()));

    }

    private void createMainTreeLenTable(int offset, int tablelen,
            short[] pretreetable, short[] prelentable) throws TikaException {
        if (pretreetable == null)
            throw new ChmParsingException("pretreetable is null");
        int i = offset;
        int z, y, x;
        while (i < tablelen) {
            int f = getChmSection().getDesyncBits(
                    ChmConstants.LZX_PRETREE_TABLEBITS, 0);
            z = pretreetable[f];
            if (z >= ChmConstants.LZX_PRETREE_MAXSYMBOLS) {
                x = ChmConstants.LZX_PRETREE_TABLEBITS;
                do {
                    x++;
                    z <<= 1;
                    z += getChmSection().checkBit(x);
                } while ((z = pretreetable[z]) >= ChmConstants.LZX_PRETREE_MAXSYMBOLS);
            }
            getChmSection().getSyncBits(prelentable[z]);
            if (z < 17) {
                z = getState().getMainTreeLengtsTable()[i] - z;
                if (z < 0)
                    z = z + 17;
                getState().mainTreeLengtsTable[i] = (short) z;
                i++;
            } else if (z == 17) {
                y = getChmSection().getSyncBits(4);
                y += 4;
                for (int j = 0; j < y; j++) {
                    assertInRange(getState().getMainTreeLengtsTable(), i);
                    getState().mainTreeLengtsTable[i++] = 0;
                }
            } else if (z == 18) {
                y = getChmSection().getSyncBits(5);
                y += 20;
                for (int j = 0; j < y; j++) {
                    assertInRange(getState().getMainTreeLengtsTable(), i);
                    getState().mainTreeLengtsTable[i++] = 0;
                }
            } else if (z == 19) {
                y = getChmSection().getSyncBits(1);
                y += 4;
                z = pretreetable[getChmSection().getDesyncBits(
                        ChmConstants.LZX_PRETREE_TABLEBITS, 0)];
                if (z >= ChmConstants.LZX_PRETREE_MAXSYMBOLS) {
                    x = ChmConstants.LZX_PRETREE_TABLEBITS;
                    do {
                        x++;
                        z <<= 1;
                        z += getChmSection().checkBit(x);
                    } while ((z = pretreetable[z]) >= ChmConstants.LZX_PRETREE_MAXSYMBOLS);
                }
                getChmSection().getSyncBits(prelentable[z]);
                z = getState().mainTreeLengtsTable[i] - z;
                if (z < 0)
                    z = z + 17;
                for (int j = 0; j < y; j++)
                    if (i < getState().getMainTreeLengtsTable().length)
                        getState().mainTreeLengtsTable[i++] = (short) z;
            }
        }
    }

    private void assertInRange(short[] array, int index) throws ChmParsingException {
        if (index >= array.length)
            throw new ChmParsingException(index + " is bigger than "
                    + array.length);
    }

    private short[] createAlignedLenTable() {
        int tablelen = ChmConstants.LZX_BLOCKTYPE_UNCOMPRESSED;
        int bits = ChmConstants.LZX_BLOCKTYPE_UNCOMPRESSED;
        short[] tmp = new short[tablelen];
        for (int i = 0; i < tablelen; i++) {
            tmp[i] = (short) getChmSection().getSyncBits(bits);
        }
        return tmp;
    }

    private void createAlignedTreeTable() {
        getState().setAlignedLenTable(createAlignedLenTable());
        getState().setAlignedLenTable(
                createTreeTable2(getState().getAlignedLenTable(),
                        (1 << ChmConstants.LZX_NUM_PRIMARY_LENGTHS)
                                + (ChmConstants.LZX_ALIGNED_MAXSYMBOLS << 1),
                        ChmConstants.LZX_NUM_PRIMARY_LENGTHS,
                        ChmConstants.LZX_ALIGNED_MAXSYMBOLS));
    }

    private short[] createTreeTable2(short[] lentable, int tablelen, int bits,
            int maxsymbol) {
        short[] tmp = new short[tablelen];
        short sym;
        int leaf;
        int bit_num = 1;
        long fill;
        int pos = 0;
        /* the current position in the decode table */
        long table_mask = (1 << bits);
        long bit_mask = (table_mask >> 1);
        long next_symbol = bit_mask;

        /* fills entries for short codes for a direct mapping */
        while (bit_num <= bits) {
            for (sym = 0; sym < maxsymbol; sym++) {
                if (lentable.length > sym && lentable[sym] == bit_num) {
                    leaf = pos;// pos=0

                    if ((pos += bit_mask) > table_mask)
                        return null;

                    fill = bit_mask;
                    while (fill-- > 0)
                        tmp[leaf++] = sym;
                }
            }
            bit_mask >>= 1;
            bit_num++;
        }

        /* if there are any codes longer than nbits */
        if (pos != table_mask) {
            /* clears the remainder of the table */
            for (leaf = pos; leaf < table_mask; leaf++)
                tmp[leaf] = 0;

            /* gives ourselves room for codes to grow by up to 16 more bits */
            pos <<= 16;
            table_mask <<= 16;
            bit_mask = 1 << 15;

            while (bit_num <= 16) {
                for (sym = 0; sym < maxsymbol; sym++) {
                    if ((lentable.length > sym) && (lentable[sym] == bit_num)) {
                        leaf = pos >> 16;
                        for (fill = 0; fill < bit_num - bits; fill++) {
                            /*
                             * if this path hasn't been taken yet, 'allocate'
                             * two entries
                             */
                            if (tmp[leaf] == 0) {
                                if (((next_symbol << 1) + 1) < tmp.length) {
                                    tmp[(int) (next_symbol << 1)] = 0;
                                    tmp[(int) (next_symbol << 1) + 1] = 0;
                                    tmp[leaf] = (short) next_symbol++;
                                }

                            }
                            /*
                             * follows the path and select either left or right
                             * for next bit
                             */
                            leaf = tmp[leaf] << 1;
                            if (((pos >> (15 - fill)) & 1) != 0)
                                leaf++;
                        }
                        tmp[leaf] = sym;

                        if ((pos += bit_mask) > table_mask)
                            return null;
                        /* table overflow */
                    } else {
                        // return null;
                    }
                }
                bit_mask >>= 1;
                bit_num++;
            }
        }

        /* is it full table? */
        if (pos == table_mask)
            return tmp;

        return tmp;
    }

    public byte[] getContent() {
        return content;
    }

    public byte[] getContent(int startOffset, int endOffset) {
        int length = endOffset - startOffset;
        // return (getContent() != null) ? Arrays.copyOfRange(getContent(),
        // startOffset, (startOffset + length)) : new byte[1];
        return (getContent() != null) ? ChmCommons.copyOfRange(getContent(),
                startOffset, (startOffset + length)) : new byte[1];
    }

    public byte[] getContent(int start) {
        // return (getContent() != null) ? Arrays.copyOfRange(getContent(),
        // start, (getContent().length + start)) : new byte[1];
        return (getContent() != null) ? ChmCommons.copyOfRange(getContent(),
                start, (getContent().length + start)) : new byte[1];
    }

    private void setContent(int contentLength) {
        this.content = new byte[contentLength];
    }

    private void checkLzxBlock(ChmLzxBlock chmPrevLzxBlock) throws TikaException {
        if (chmPrevLzxBlock == null && getBlockLength() < Integer.MAX_VALUE)
            setState(new ChmLzxState((int) getBlockLength()));
        else
            setState(chmPrevLzxBlock.getState());
    }

    private boolean validateConstructorParams(int blockNumber,
            byte[] dataSegment, long blockLength) throws TikaException {
        int goodParameter = 0;
        if (blockNumber >= 0)
            ++goodParameter;
        else
            throw new ChmParsingException("block number should be possitive");
        if (dataSegment != null && dataSegment.length > 0)
            ++goodParameter;
        else
            throw new ChmParsingException("data segment should not be null");
        if (blockLength > 0)
            ++goodParameter;
        else
            throw new ChmParsingException(
                    "block length should be more than zero");
        return (goodParameter == 3);
    }

    public int getBlockNumber() {
        return block_number;
    }

    private void setBlockNumber(int block_number) {
        this.block_number = block_number;
    }

    private long getBlockLength() {
        return block_length;
    }

    private void setBlockLength(long block_length) {
        this.block_length = block_length;
    }

    public ChmLzxState getState() {
        return state;
    }

    private void setState(ChmLzxState state) {
        this.state = state;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub

    }
}
