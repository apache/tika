package org.apache.tika.parser.dwg;

public class DWGReadFormatRemover {
    public String cleanupDwgString(String dwgString) {
        // Cleaning the formatting of the text has been found from the following
        // website's:
        // https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640
        // https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html
        // These have also been spotted (pxqc,pxqr,pxql,simplex)
        // We always to do a backwards look to make sure the string to replace hasn't
        // been escaped
        String cleanString;
        // replace A0-2 (Alignment)
        cleanString = dwgString.replaceAll("(?<!\\\\)\\\\A[0-2];", "");
        // replace \\p (New paragraph/ new line) and with new line
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\P", "\n");
        // remove pi (numbered paragraphs)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pi(.*?);", "");
        // remove pxi (bullets)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxi(.*?);", "");
        // remove pxt (tab stops)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxt(.*?);", "");
        // remove pt (tabs)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pt(.*?);", "");
        // remove lines with \H (text height)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\H[0-9]*(.*?);", "");
        // remove lines with \F Font Selection
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\F|f(.*?);", "");
        // Replace \L \l (underlines)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\L)(.*?)(\\\\l)", "$2");
        // Replace \O \o (over strikes)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\O)(.*?)(\\\\o)", "$2");
        // Replace \K \k (Strike through)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\K)(.*?)(\\\\k)", "$2");
        // Replace \N (new Column)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\N)", "\t");
        // Replace \Q (text angle)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\Q[\\d];", "");
        // Replace \W (Text Width)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\W(.*?);", "");
        // Replace \S (Stacking)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\S(.*?):", "");
        // Replace \C (Stacking)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\C|c[1-7];)", "");
        // Replace \T (Tracking)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\T(.*?);)", "");
        // Replace \pxqc mtext justfication
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxqc;)", "");
        // Replace \pxqr mtext justfication
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxqr;)", "");
        // Replace \pxql mtext justfication
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxql;)", "");
        // Replace \simplex (simplex)
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\simplex\\|c(.*?);)", "");
        // Now we have cleaned the formatting we can now remove the escaped \
        cleanString = cleanString.replaceAll("(\\\\)", "\\\\");
        // Replace {} (text formatted by the above)
        cleanString = cleanString.replaceAll("(?<!\\\\)\\}|(?<!\\\\)\\{", "");

        //
        return cleanString;

    }
}
