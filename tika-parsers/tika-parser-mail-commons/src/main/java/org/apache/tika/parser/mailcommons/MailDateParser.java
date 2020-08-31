package org.apache.tika.parser.mailcommons;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MailDateParser {
    public static Date parseDate(String headerContent) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        return dateFormat.parse(headerContent);
    }

}
