/**
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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.poi.hdf.extractor.Utils;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.StringUtil;

class PowerPointExtractor {

    static Logger LOG = Logger.getRootLogger();

    /** Buffer holding the content of the file */
    private final Appendable builder;

    /**
     * Constructs Listener to get content of PowerPoint file.
     */
    public PowerPointExtractor(Appendable builder) {
        this.builder = builder;
    }

    /**
     * Reads the internal PowerPoint document stream.
     */
    public void extract(InputStream dis) {
        try {
            final byte pptdata[] = new byte[dis.available()];
            dis.read(pptdata, 0, dis.available());
            int offset = 0;
            long offsetPD = 0;

            /*
             * Traverse Bytearray to get CurrentUserEditAtom Call to extract the
             * Text in all PlaceHolders to hold PPTClientTextBox objects for
             * mapping into Slide Objects
             */
            Hashtable/* <Long, TextBox> */containerTextBox = new Hashtable/*
                                                                             * <Long,
                                                                             * TextBox>
                                                                             */();
            // Traverse ByteArray to identiy edit paths of ClientTextBoxes
            long n = pptdata.length - 20;
            for (long i = 0; i < n; i++) {

                final long type = LittleEndian.getUShort(pptdata, (int) i + 2);
                // final long size = LittleEndian.getUInt(pptdata, (int) i + 4);

                if (PPTConstants.PPT_ATOM_USEREDIT == type) {
                    /*
                     * Checking the Record Header (UserEditAtom)
                     */
                    // final long lastSlideID = LittleEndian.getInt(pptdata,
                    // (int) i + 8);
                    // final long version = LittleEndian.getUInt(pptdata, (int)
                    // i + 12);
                    offset = (int) LittleEndian.getUInt(pptdata, (int) i + 16);
                    offsetPD = LittleEndian.getUInt(pptdata, (int) i + 20);

                    /*
                     * Call to extract ClientTextBox text in each UserEditAtom
                     */
                    containerTextBox = extractTextBoxes(containerTextBox,
                            offset, pptdata, offsetPD);
                } else if (PPTConstants.PPT_ATOM_DRAWINGGROUP == type) {
                    // if (LOG.isTraceEnabled()) {
                    // LOG.trace("PPT_DRAWINGGROUP_ATOM ignored: " + type);
                    // }
                } else if (PPTConstants.PPT_ATOM_TEXTBYTE == type) {
                    // if (LOG.isTraceEnabled()) {
                    // LOG.trace("PPT_TEXTBYTE_ATOM ignored: " + type);
                    // }
                } else if (PPTConstants.PPT_ATOM_TEXTCHAR == type) {
                    // if (LOG.isTraceEnabled()) {
                    // LOG.trace("PPT_TEXTCHAR_ATOM ignored: " + type);
                    // }
                } else {
                    // no action
                    // if (LOG.isTraceEnabled()) {
                    // LOG.trace("type not handled: " + type);
                    // }
                }
            }

            final List/* <PPTSlide> */slides = extractSlides(offset, pptdata,
                    offsetPD);

            if (slides.size() == 0) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("No slides extracted!");
                }

            } else {
                Slide slide = (Slide) slides.get(slides.size() - 1);

                for (Enumeration enumeration = containerTextBox.elements(); enumeration
                        .hasMoreElements();) {
                    final TextBox textBox = (TextBox) enumeration.nextElement();
                    slide.addContent(textBox.getContent());
                }

                /*
                 * Merging TextBox data with Slide Data Printing the text from
                 * Slides vector object.
                 */
                List scontent;
                for (int i = 0; i < slides.size(); i++) {
                    slide = (Slide) slides.get(i);
                    scontent = slide.getContent();
                    String contentText;

                    for (int j = 0; j < scontent.size(); j++) {
                        contentText = scontent.get(j).toString();
                        builder.append(contentText);

                        // to avoid concatinated words we add a blank additional
                        if (contentText.length() > 0
                                && !(contentText.endsWith("\r") || contentText
                                        .endsWith("\n"))) {
                            builder.append(" ");
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            // because of not killing complete crawling all Throwables are
            // catched.

            LOG.error("processPOIFSReaderEvent", ex);
        }
    }

    /**
     * Extracts the client text boxes of a slide.
     * 
     * @param containerTextBox
     * @param offset
     * @param pptdata
     * @param offsetPD
     * @return Hashtable
     * @see TextBox
     */
    protected Hashtable/* <Long, TextBox> */extractTextBoxes(
            final Hashtable/* <Long, TextBox> */containerTextBox,
            final int offset, final byte[] pptdata, final long offsetPD) {

        // To hold temporary data
        FilteredStringWriter outStream = new FilteredStringWriter();

        TextBox textBox;

        // Traversing the bytearray up to Presist directory position
        for (int i = offset; i < offsetPD - 20; i++) {
            try {
                // Record info
                // final long rinfo = LittleEndian.getUShort(pptdata, (int) i);
                // Record Type
                final long recordType = LittleEndian.getUShort(pptdata, i + 2);
                // Record Size
                final long recordSize = LittleEndian.getUInt(pptdata, i + 4);

                if (recordType == PPTConstants.PPT_ATOM_DRAWINGGROUP) {
                    /*
                     * Record type is of Drawing Group
                     */

                    // Total number of objects
                    // final long objectCount = LittleEndian.getUInt(pptdata,
                    // (int) i +
                    // 8);
                    // currentID = Group ID+number of objects
                    long currentID = LittleEndian.getInt(pptdata, i + 12);
                    currentID = ((int) (currentID / 1024)) * 1024;

                    if (currentID == PPTConstants.PPT_MASTERSLIDE) {
                        // Ignore Master Slide objects
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Ignore master slide.");
                        }
                        i++;
                        continue;
                    }

                    // Check for the ClientTextBox GroupID existence
                    if (containerTextBox.containsKey(new Long(currentID))) {
                        // If exists get Client Textbox Group
                        textBox = (TextBox) containerTextBox.get(new Long(
                                currentID));
                        textBox.setContent("");

                    } else {
                        textBox = new TextBox(currentID);
                        containerTextBox.put(new Long(currentID), textBox);
                    }

                    /*
                     * Iterating the bytearray for TextCharAtoms and
                     * TextBytesAtom
                     */
                    if ((offsetPD - 20) != recordSize) {
                        // TODO something wrong? Probably an OLE-Object, which
                        // we ignore.
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("offsetPD - 20=" + (offsetPD - 20)
                                    + " recordsize=" + recordSize);
                        }
                    } else {
                        for (int startPos = i + 8; startPos < offsetPD - 20
                                && startPos < recordSize; startPos++) { // &&
                            // startPos
                            // <
                            // recordSize??
                            try {

                                // Record info
                                // final long nrinfo =
                                // LittleEndian.getUShort(pptdata, (int) j);

                                // Record Type
                                final long ntype = LittleEndian.getUShort(
                                        pptdata, startPos + 2);

                                // Record size
                                // Note that the size doesn't include the 8 byte
                                // atom header
                                final long nsize = LittleEndian.getUInt(
                                        pptdata, startPos + 4);

                                if (ntype == PPTConstants.PPT_ATOM_DRAWINGGROUP) {
                                    /*
                                     * Break the loop if next GroupID found
                                     */
                                    i = startPos - 1;
                                    break;
                                } else if (ntype == PPTConstants.PPT_ATOM_TEXTBYTE) {
                                    // TextByteAtom record
                                    outStream = new FilteredStringWriter();
                                    long ii = 0;
                                    for (ii = startPos + 6; ii <= startPos + 6
                                            + nsize; ii++) {
                                        // For loop to changed to a function
                                        // if ((ii + 2) >= pptdata.length)
                                        // break; // FIXME
                                        outStream
                                                .write((char) (pptdata[(int) ii + 2]));
                                    }

                                    // Setting the identified text for Current
                                    // groupID
                                    textBox.setContent(textBox.getContent()
                                            + outStream.toString());

                                } else if (ntype == PPTConstants.PPT_ATOM_TEXTCHAR) {
                                    // TextCharAtom record

                                    final String strTempContent = new String(
                                            pptdata, startPos + 6,
                                            (int) (nsize) + 2);
                                    final byte bytes[] = strTempContent
                                            .getBytes();
                                    if (true) {
                                        outStream = new FilteredStringWriter();
                                        for (int ii = 0; ii < bytes.length - 1; ii += 2) {
                                            // For loop to changed to a function
                                            outStream
                                                    .write((char) (pptdata[ii + 2]));
                                        }
                                        textBox.setContent(textBox.getContent()
                                                + outStream.toString());
                                    } else {
                                        // this version is used within POI
                                        String text = StringUtil
                                                .getFromCompressedUnicode(
                                                        bytes, 0, bytes.length);
                                        textBox.setContent(textBox.getContent()
                                                + text);
                                    }

                                } else {
                                    // ignored
                                    // if (LOG.isTraceEnabled()) {
                                    // LOG.trace("Ignored atom type: " + type);
                                    // }
                                }
                            } catch (Throwable e) {

                                LOG.error("extractTextBoxes", e);

                                break;
                            }
                        }
                    }
                } else {
                    // Record type is ignored
                    // if (LOG.isTraceEnabled()) {
                    // LOG.trace("Ignored record type: " + type);
                    // }
                }
            } catch (Throwable ee) {

                LOG.error("extractClientTextBoxes", ee);

                break;
            }
        }
        return containerTextBox;
    }

    /**
     * Returns the Powerpoint <code>Slide</code> s of document as vector.
     * 
     * @param offset
     * @param pptdata
     * @param offsetPD
     * @return Vector of the powerpoint slides. Contains
     *         <code>{@link Slide Slide}</code>
     * @see Slide
     */
    protected List /* <Slide> */extractSlides(final long offset,
            final byte[] pptdata, final long offsetPD) {

        int sNum = 0;

        // List of all slides found
        final List/* <Slide> */slides = new Vector/* <Slide> */();

        // current slide data
        Slide currentSlide = null;

        // To store data found in TextCharAtoms and TextBytesAtoms
        FilteredStringWriter outStream;

        for (long i = offset; i < pptdata.length - 20; i++) {

            final long recordInfo = LittleEndian.getUShort(pptdata, (int) i);
            final long atomType = LittleEndian.getUShort(pptdata, (int) i + 2);
            final long atomSize = LittleEndian.getUInt(pptdata, (int) i + 4);

            if (atomType == PPTConstants.PPT_ATOM_TEXTBYTE) {
                /*
                 * TextByteAtom record
                 */
                outStream = new FilteredStringWriter();

                for (long ii = i + 6; (ii <= i + 6 + atomSize)
                        && (ii + 2 < pptdata.length); ii++) {
                    try {
                        // if(ii+2 >= pptdata.length) break; //FIXME
                        byte value = pptdata[(int) ii + 2];
                        outStream.write(value);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("size=" + pptdata.length);
                        }

                        LOG.error("extractSlides", ex);

                    }
                }

                // Setting the identified text for Current Slide
                if (currentSlide != null) {
                    currentSlide.addContent(outStream.toString());
                }

            } else if (atomType == PPTConstants.PPT_ATOM_TEXTCHAR) {
                /*
                 * TextCharAtom record
                 */
                outStream = new FilteredStringWriter();
                final String strTempContent = new String(pptdata, (int) i + 6,
                        (int) (atomSize) + 2);
                final byte bytes[] = strTempContent.getBytes();

                for (int ii = 0; ii < bytes.length - 1; ii += 2) {
                    outStream.write(Utils.getUnicodeCharacter(bytes, ii));
                }

                // Setting the identified text for Current Slide
                if (currentSlide != null) {
                    currentSlide.addContent(outStream.toString());
                }

            } else if (atomType == PPTConstants.PPT_ATOM_SLIDEPERSISTANT) {
                /*
                 * SlidePresistAtom Record
                 */
                if (sNum != 0) {
                    outStream = new FilteredStringWriter();

                    final long slideID = LittleEndian.getUInt(pptdata,
                            (int) i + 20);

                    currentSlide = new Slide(slideID);
                    // currentSlide.addContent(outStream.toString());
                    slides.add(currentSlide);
                }
                sNum++;
            } else if (atomType == PPTConstants.PPT_ATOM_DRAWINGGROUP) {
                /*
                 * Diagram records are ignored
                 */
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Drawing Groups are ignored.");
                }
                break;
            } else {
                // ignored
                // if (LOG.isTraceEnabled()) {
                // LOG.trace("Unhandled atomType: " + atomType);
                // }
            }
        }

        return slides;
    }

}
