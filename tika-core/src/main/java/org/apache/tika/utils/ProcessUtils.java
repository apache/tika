package org.apache.tika.utils;


public class ProcessUtils {

    /**
     * This should correctly put double-quotes around an argument if
     * ProcessBuilder doesn't seem to work (as it doesn't
     * on paths with spaces on Windows)
     *
     * @param arg
     * @return
     */
    public static String escapeCommandLine(String arg) {
        if (arg == null) {
            return arg;
        }
        //need to test for " " on windows, can't just add double quotes
        //across platforms.
        if (arg.contains(" ") && SystemUtils.IS_OS_WINDOWS) {
            arg = "\"" + arg + "\"";
        }
        return arg;
    }
}
