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

package org.apache.tika.parser.microsoft.ooxml;


import org.xml.sax.helpers.DefaultHandler;

public class AbstractDocumentXMLBodyHandler extends DefaultHandler {

    protected final static String R = "r";
    protected final static String FLD = "fld";
    protected final static String RPR = "rPr";
    protected final static String P = "p";
    protected static String P_STYLE = "pStyle";
    protected final static String PPR = "pPr";
    protected static String T = "t";
    protected final static String TAB = "tab";
    protected final static String B = "b";
    protected final static String ILVL = "ilvl";
    protected final static String NUM_ID = "numId";
    protected final static String TC = "tc";
    protected final static String TR = "tr";
    protected final static String I = "i";
    protected final static String NUM_PR = "numPr";
    protected final static String BR = "br";
    protected final static String HYPERLINK = "hyperlink";
    protected final static String TBL = "tbl";
    protected final static String PIC = "pic";
    protected final static String PICT = "pict";
    protected final static String IMAGEDATA = "imagedata";
    protected final static String BLIP = "blip";
    protected final static String CHOICE = "Choice";
    protected final static String FALLBACK = "Fallback";
    protected final static String OLE_OBJECT = "OLEObject";
    protected final static String CR = "cr";

    public final static String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    protected final static String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    protected final static String O_NS = "urn:schemas-microsoft-com:office:office";
    protected final static String PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    protected final static String DRAWING_MAIN_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    protected final static String V_NS = "urn:schemas-microsoft-com:vml";

    protected final static String OFFICE_DOC_RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    protected final static char[] TAB_CHAR = new char[1];
    protected final static char NEWLINE = '\n';

    static {
        TAB_CHAR[0] = '\t';
    }

    protected boolean inR = false;//in run or in field
    protected boolean inT = false;
    protected boolean inRPr = false;
    protected boolean inNumPr = false;

    protected boolean inPic = false;
    boolean inPict = false;
    protected String picDescription = null;
    protected String picRId = null;
    String picFilename = null;

    //mechanism used to determine when to
    //signal the start of the p, and still
    //handle p with pPr and those without
    protected boolean lastStartElementWasP = false;
    //have we signaled the start of a p?
    //pPr can happen multiple times within a p
    //<p><pPr/><r><t>text</t></r><pPr></p>
    protected boolean pStarted = false;

    //alternate content can be embedded in itself.
    //need to track depth.
    //if in alternate, choose fallback, maybe make this configurable?
    protected int inACChoiceDepth = 0;
    protected int inACFallbackDepth = 0;

    protected RunProperties currRunProperties = new RunProperties();
    protected ParagraphProperties currPProperties = new ParagraphProperties();

    protected final StringBuilder runBuffer = new StringBuilder();

}
