// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
/**
 * ******************************************************************************
 * Copyright (C) 2005-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 * ******************************************************************************
 */
package org.apache.tika.parser.txt;

import org.apache.poi.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * <code>CharsetDetector</code> provides a facility for detecting the
 * charset or encoding of character data in an unknown format.
 * The input data can either be from an input stream or an array of bytes.
 * The result of the detection operation is a list of possibly matching
 * charsets, or, for simple use, you can just ask for a Java Reader that
 * will will work over the input data.
 * <p>
 * Character set detection is at best an imprecise operation.  The detection
 * process will attempt to identify the charset that best matches the characteristics
 * of the byte data, but the process is partly statistical in nature, and
 * the results can not be guaranteed to always be correct.
 * <p>
 * For best accuracy in charset detection, the input data should be primarily
 * in a single language, and a minimum of a few hundred bytes worth of plain text
 * in the language are needed.  The detection process will attempt to
 * ignore html or xml style markup that could otherwise obscure the content.
 * <p>
 *
 * @stable ICU 3.4
 */
public class CharsetDetector {

//   Question: Should we have getters corresponding to the setters for input text
//   and declared encoding?

//   A thought: If we were to create our own type of Java Reader, we could defer
//   figuring out an actual charset for data that starts out with too much English
//   only ASCII until the user actually read through to something that didn't look
//   like 7 bit English.  If  nothing else ever appeared, we would never need to
//   actually choose the "real" charset.  All assuming that the application just
//   wants the data, and doesn't care about a char set name.

    private static final int MAX_CONFIDENCE = 100;

    static final int DEFAULT_MARK_LIMIT = 12000; //This is a Tika modification; ICU's is 8000

    /*
     * List of recognizers for all charsets known to the implementation.
     */
    private static final List<CSRecognizerInfo> ALL_CS_RECOGNIZERS;

    static {
        List<CSRecognizerInfo> list = new ArrayList<>();

        list.add(new CSRecognizerInfo(new CharsetRecog_UTF8(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_Unicode.CharsetRecog_UTF_16_BE(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_Unicode.CharsetRecog_UTF_16_LE(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_Unicode.CharsetRecog_UTF_32_BE(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_Unicode.CharsetRecog_UTF_32_LE(), true));

        list.add(new CSRecognizerInfo(new CharsetRecog_mbcs.CharsetRecog_sjis(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_2022.CharsetRecog_2022JP(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_2022.CharsetRecog_2022CN(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_2022.CharsetRecog_2022KR(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_gb_18030(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_jp(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_kr(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_mbcs.CharsetRecog_big5(), true));

        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_1(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_2(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_5_ru(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_6_ar(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_7_el(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_8_I_he(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_8_he(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_windows_1251(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_windows_1256(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_KOI8_R(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_8859_9_tr(), true));

        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_de(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_en(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_es(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_fr(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_it(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_EBCDIC_500_nl(), true));

        // IBM 420/424 recognizers are disabled by default
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_IBM424_he_rtl(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_IBM424_he_ltr(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_IBM420_ar_rtl(), true));
        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_IBM420_ar_ltr(), true));

        list.add(new CSRecognizerInfo(new CharsetRecog_sbcs.CharsetRecog_IBM866_ru(), true));
        ALL_CS_RECOGNIZERS = Collections.unmodifiableList(list);
    }

    /*
     *  The following items are accessed by individual CharsetRecongizers during
     *     the recognition process
     *
     */
    final byte[] fInputBytes;  // The text to be checked.  Markup will have been
                               // removed if appropriate.

    int fInputLen;          // Length of the byte data in fInputBytes.
    short fByteStats[] =      // byte frequency statistics for the input text.
            new short[256];  //   Value is percent, not absolute.
    boolean fC1Bytes =          // True if any bytes in the range 0x80 - 0x9F are in the input;
            false;
    String fDeclaredEncoding;
    byte[] fRawInput;     // Original, untouched input bytes.
    //  If user gave us a byte array, this is it.
    //  If user gave us a stream, it's read to a
    //  buffer here.
    int fRawLength;    // Length of data in fRawInput array.
    InputStream fInputStream;  // User's input stream, or null if the user
    //
    //  Stuff private to CharsetDetector
    //
    private boolean fStripTags =   // If true, setText() will strip tags from input text.
            false;
    private boolean[] fEnabledRecognizers;   // If not null, active set of charset recognizers had

    private final int kBufSize;

    /**
     * Constructor
     *
     * @stable ICU 3.4
     */
    public CharsetDetector() {
        this(DEFAULT_MARK_LIMIT);
    }

    public CharsetDetector(int markLimit) {
        kBufSize = markLimit;
        fInputBytes = new byte[kBufSize];
    }

    /**
     * Get the names of all charsets supported by <code>CharsetDetector</code> class.
     * <p>
     * <b>Note:</b> Multiple different charset encodings in a same family may use
     * a single shared name in this implementation. For example, this method returns
     * an array including "ISO-8859-1" (ISO Latin 1), but not including "windows-1252"
     * (Windows Latin 1). However, actual detection result could be "windows-1252"
     * when the input data matches Latin 1 code points with any points only available
     * in "windows-1252".
     *
     * @return an array of the names of all charsets supported by
     * <code>CharsetDetector</code> class.
     * @stable ICU 3.4
     */
    public static String[] getAllDetectableCharsets() {
        String[] allCharsetNames = new String[ALL_CS_RECOGNIZERS.size()];
        for (int i = 0; i < allCharsetNames.length; i++) {
            allCharsetNames[i] = ALL_CS_RECOGNIZERS.get(i).recognizer.getName();
        }
        return allCharsetNames;
    }

    /**
     * Set the declared encoding for charset detection.
     * The declared encoding of an input text is an encoding obtained
     * from an http header or xml declaration or similar source that
     * can be provided as additional information to the charset detector.
     * A match between a declared encoding and a possible detected encoding
     * will raise the quality of that detected encoding by a small delta,
     * and will also appear as a "reason" for the match.
     * <p>
     * A declared encoding that is incompatible with the input data being
     * analyzed will not be added to the list of possible encodings.
     *
     * @param encoding The declared encoding
     * @stable ICU 3.4
     */
    public CharsetDetector setDeclaredEncoding(String encoding) {
        setCanonicalDeclaredEncoding(encoding);
        return this;
    }
    //   Value is rounded up, so zero really means zero occurences.

    /**
     * Set the input text (byte) data whose charset is to be detected.
     *
     * @param in the input text of unknown encoding
     * @return This CharsetDetector
     * @stable ICU 3.4
     */
    public CharsetDetector setText(byte[] in) {
        return setText(in, in.length);
    }

    private CharsetDetector setText(byte[] in, int length) {
        fRawInput = in;
        fRawLength = length;

        MungeInput();
        return this;
    }

    /**
     * Set the input text (byte) data whose charset is to be detected.
     * <p>
     * The input stream that supplies the character data must have markSupported()
     * == true; the charset detection process will read a small amount of data,
     * then return the stream to its original position via
     * the InputStream.reset() operation.  The exact amount that will
     * be read depends on the characteristics of the data itself.
     *
     * @param in the input text of unknown encoding
     * @return This CharsetDetector
     * @stable ICU 3.4
     */

    public CharsetDetector setText(InputStream in) throws IOException {
        fInputStream = in;
        fInputStream.mark(kBufSize);
        byte[] inputBytes = new byte[kBufSize];   // Always make a new buffer because the
        //   previous one may have come from the caller,
        //   in which case we can't touch it.
        long bytesRead = -1;
        try {
            bytesRead = IOUtils.readFully(fInputStream, inputBytes);
            if (bytesRead >= Integer.MAX_VALUE) {
                throw new IOException("Can't have read > Integer.MAX_VALUE bytes");
            }
        } finally {
            fInputStream.reset();
        }

        if (bytesRead < 1) {
            return setText(new byte[0]);
        } else if ( kBufSize > bytesRead) {
            return setText(inputBytes, (int)bytesRead);
        } else {
            return setText(inputBytes);
        }
    }

    /**
     * Return the charset that best matches the supplied input data.
     * <p>
     * Note though, that because the detection
     * only looks at the start of the input data,
     * there is a possibility that the returned charset will fail to handle
     * the full set of input data.
     * <p>
     * Raise an exception if
     * <ul>
     * <li>no charset appears to match the data.</li>
     * <li>no input text has been provided</li>
     * </ul>
     *
     * @return a CharsetMatch object representing the best matching charset, or
     * <code>null</code> if there are no matches.
     * @stable ICU 3.4
     */
    public CharsetMatch detect() {
//   TODO:  A better implementation would be to copy the detect loop from
//          detectAll(), and cut it short as soon as a match with a high confidence
//          is found.  This is something to be done later, after things are otherwise
//          working.
        CharsetMatch matches[] = detectAll();

        if (matches == null || matches.length == 0) {
            return null;
        }

        return matches[0];
    }

    /**
     * Return an array of all charsets that appear to be plausible
     * matches with the input data.  The array is ordered with the
     * best quality match first.
     * <p>
     * Raise an exception if
     * <ul>
     * <li>no charsets appear to match the input data.</li>
     * <li>no input text has been provided</li>
     * </ul>
     *
     * @return An array of CharsetMatch objects representing possibly matching charsets.
     * @stable ICU 3.4
     */
    public CharsetMatch[] detectAll() {
        CharsetRecognizer csr;
        int i;
        CharsetMatch charsetMatch;
        int confidence;
        ArrayList<CharsetMatch> matches = new ArrayList<CharsetMatch>();
        //  Iterate over all possible charsets, remember all that
        //    give a match quality > 0.
        for (i = 0; i < ALL_CS_RECOGNIZERS.size(); i++) {
            csr = ALL_CS_RECOGNIZERS.get(i).recognizer;
            charsetMatch = csr.match(this);
            if (charsetMatch != null) {
                confidence = charsetMatch.getConfidence() & 0x000000ff;
                if (confidence > 0) {
                    // Just to be safe, constrain
                    confidence = Math.min(confidence, MAX_CONFIDENCE);

                    // Apply charset hint.
                    if ((fDeclaredEncoding != null) && (fDeclaredEncoding.equalsIgnoreCase(csr.getName()))) {
                        // Reduce lack of confidence (delta between "sure" and current) by 50%.
                        confidence += (MAX_CONFIDENCE - confidence) / 2;
                    }
                    CharsetMatch m = new CharsetMatch(this, csr, confidence, charsetMatch.getName(), charsetMatch.getLanguage());
                    matches.add(m);
                }
            }
        }
        Collections.sort(matches);      // CharsetMatch compares on confidence
        Collections.reverse(matches);   //  Put best match first.
        CharsetMatch[] resultArray = new CharsetMatch[matches.size()];
        resultArray = matches.toArray(resultArray);
        return resultArray;
    }

    /**
     * Autodetect the charset of an inputStream, and return a Java Reader
     * to access the converted input data.
     * <p>
     * This is a convenience method that is equivalent to
     * <code>this.setDeclaredEncoding(declaredEncoding).setText(in).detect().getReader();</code>
     * <p>
     * For the input stream that supplies the character data, markSupported()
     * must be true; the  charset detection will read a small amount of data,
     * then return the stream to its original position via
     * the InputStream.reset() operation.  The exact amount that will
     * be read depends on the characteristics of the data itself.
     * <p>
     * Raise an exception if no charsets appear to match the input data.
     *
     * @param in               The source of the byte data in the unknown charset.
     * @param declaredEncoding A declared encoding for the data, if available,
     *                         or null or an empty string if none is available.
     * @stable ICU 3.4
     */
    public Reader getReader(InputStream in, String declaredEncoding) {
        fDeclaredEncoding = declaredEncoding;

        try {
            setText(in);

            CharsetMatch match = detect();

            if (match == null) {
                return null;
            }

            return match.getReader();
        } catch (IOException e) {
            return null;
        }
    }
    //   gave us a byte array.

    /**
     * Autodetect the charset of an inputStream, and return a String
     * containing the converted input data.
     * <p>
     * This is a convenience method that is equivalent to
     * <code>this.setDeclaredEncoding(declaredEncoding).setText(in).detect().getString();</code>
     * <p>
     * Raise an exception if no charsets appear to match the input data.
     *
     * @param in               The source of the byte data in the unknown charset.
     * @param declaredEncoding A declared encoding for the data, if available,
     *                         or null or an empty string if none is available.
     * @stable ICU 3.4
     */
    public String getString(byte[] in, String declaredEncoding) {
        fDeclaredEncoding = declaredEncoding;

        try {
            setText(in);

            CharsetMatch match = detect();

            if (match == null) {
                return null;
            }

            return match.getString(-1);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Test whether or not input filtering is enabled.
     *
     * @return <code>true</code> if input text will be filtered.
     * @stable ICU 3.4
     * @see #enableInputFilter
     */
    public boolean inputFilterEnabled() {
        return fStripTags;
    }
    // been changed from the default. The array index is
    // corresponding to ALL_RECOGNIZER. See setDetectableCharset().

    /**
     * Enable filtering of input text. If filtering is enabled,
     * text within angle brackets ("&lt;" and "&gt;") will be removed
     * before detection.
     *
     * @param filter <code>true</code> to enable input text filtering.
     * @return The previous setting.
     * @stable ICU 3.4
     */
    public boolean enableInputFilter(boolean filter) {
        boolean previous = fStripTags;

        fStripTags = filter;

        return previous;
    }

    /**
     * Try to set fDeclaredEncoding to the canonical name for <encoding>, if it exists.
     *
     * @param encoding - name of character encoding
     */
    private void setCanonicalDeclaredEncoding(String encoding) {
        if ((encoding == null) || encoding.isEmpty()) {
            return;
        }

        Charset cs = Charset.forName(encoding);
        if (cs != null) {
            fDeclaredEncoding = cs.name();
        }
    }

    /*
     *  MungeInput - after getting a set of raw input data to be analyzed, preprocess
     *               it by removing what appears to be html markup.
     */
    private void MungeInput() {
        int srci = 0;
        int dsti = 0;
        byte b;
        boolean inMarkup = false;
        int openTags = 0;
        int badTags = 0;

        //
        //  html / xml markup stripping.
        //     quick and dirty, not 100% accurate, but hopefully good enough, statistically.
        //     discard everything within < brackets >
        //     Count how many total '<' and illegal (nested) '<' occur, so we can make some
        //     guess as to whether the input was actually marked up at all.
        if (fStripTags) {
            for (srci = 0; srci < fRawLength && dsti < fInputBytes.length; srci++) {
                b = fRawInput[srci];
                if (b == (byte) '<') {
                    if (inMarkup) {
                        badTags++;
                    }
                    inMarkup = true;
                    openTags++;
                }

                if (!inMarkup) {
                    fInputBytes[dsti++] = b;
                }

                if (b == (byte) '>') {
                    inMarkup = false;
                }
            }

            fInputLen = dsti;
        }

        //
        //  If it looks like this input wasn't marked up, or if it looks like it's
        //    essentially nothing but markup abandon the markup stripping.
        //    Detection will have to work on the unstripped input.
        //
        if (openTags < 5 || openTags / 5 < badTags ||
                (fInputLen < 100 && fRawLength > 600)) {
            int limit = fRawLength;

            if (limit > kBufSize) {
                limit = kBufSize;
            }

            for (srci = 0; srci < limit; srci++) {
                fInputBytes[srci] = fRawInput[srci];
            }
            fInputLen = srci;
        }

        //
        // Tally up the byte occurence statistics.
        //   These are available for use by the various detectors.
        //
        Arrays.fill(fByteStats, (short) 0);
        for (srci = 0; srci < fInputLen; srci++) {
            int val = fInputBytes[srci] & 0x00ff;
            fByteStats[val]++;
        }

        fC1Bytes = false;
        for (int i = 0x80; i <= 0x9F; i += 1) {
            if (fByteStats[i] != 0) {
                fC1Bytes = true;
                break;
            }
        }
    }

    /**
     * Get the names of charsets that can be recognized by this CharsetDetector instance.
     *
     * @return an array of the names of charsets that can be recognized by this CharsetDetector
     * instance.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public String[] getDetectableCharsets() {
        List<String> csnames = new ArrayList<>(ALL_CS_RECOGNIZERS.size());
        for (int i = 0; i < ALL_CS_RECOGNIZERS.size(); i++) {
            CSRecognizerInfo rcinfo = ALL_CS_RECOGNIZERS.get(i);
            boolean active = (fEnabledRecognizers == null) ? rcinfo.isDefaultEnabled : fEnabledRecognizers[i];
            if (active) {
                csnames.add(rcinfo.recognizer.getName());
            }
        }
        return csnames.toArray(new String[csnames.size()]);
    }

    /**
     * Enable or disable individual charset encoding.
     * A name of charset encoding must be included in the names returned by
     * {@link #getAllDetectableCharsets()}.
     *
     * @param encoding the name of charset encoding.
     * @param enabled  <code>true</code> to enable, or <code>false</code> to disable the
     *                 charset encoding.
     * @return A reference to this <code>CharsetDetector</code>.
     * @throws IllegalArgumentException when the name of charset encoding is
     *                                  not supported.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public CharsetDetector setDetectableCharset(String encoding, boolean enabled) {
        int modIdx = -1;
        boolean isDefaultVal = false;
        for (int i = 0; i < ALL_CS_RECOGNIZERS.size(); i++) {
            CSRecognizerInfo csrinfo = ALL_CS_RECOGNIZERS.get(i);
            if (csrinfo.recognizer.getName().equals(encoding)) {
                modIdx = i;
                isDefaultVal = (csrinfo.isDefaultEnabled == enabled);
                break;
            }
        }
        if (modIdx < 0) {
            // No matching encoding found
            throw new IllegalArgumentException("Invalid encoding: " + "\"" + encoding + "\"");
        }

        if (fEnabledRecognizers == null && !isDefaultVal) {
            // Create an array storing the non default setting
            fEnabledRecognizers = new boolean[ALL_CS_RECOGNIZERS.size()];

            // Initialize the array with default info
            for (int i = 0; i < ALL_CS_RECOGNIZERS.size(); i++) {
                fEnabledRecognizers[i] = ALL_CS_RECOGNIZERS.get(i).isDefaultEnabled;
            }
        }

        if (fEnabledRecognizers != null) {
            fEnabledRecognizers[modIdx] = enabled;
        }

        return this;
    }

    private static class CSRecognizerInfo {
        CharsetRecognizer recognizer;
        boolean isDefaultEnabled;

        CSRecognizerInfo(CharsetRecognizer recognizer, boolean isDefaultEnabled) {
            this.recognizer = recognizer;
            this.isDefaultEnabled = isDefaultEnabled;
        }
    }
}