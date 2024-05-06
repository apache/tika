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
package org.apache.tika.utils;

/** Copied from commons-lang to avoid requiring the dependency */
public class SystemUtils {

    public static final String OS_NAME = getSystemProperty("os.name");
    public static final String OS_VERSION = getSystemProperty("os.version");
    public static final boolean IS_OS_AIX = getOSMatchesName("AIX");
    public static final boolean IS_OS_HP_UX = getOSMatchesName("HP-UX");
    public static final boolean IS_OS_IRIX = getOSMatchesName("Irix");
    public static final boolean IS_OS_LINUX =
            getOSMatchesName("Linux") || getOSMatchesName("LINUX");
    public static final boolean IS_OS_MAC = getOSMatchesName("Mac");
    public static final boolean IS_OS_MAC_OSX = getOSMatchesName("Mac OS X");
    public static final boolean IS_OS_OS2 = getOSMatchesName("OS/2");
    public static final boolean IS_OS_SOLARIS = getOSMatchesName("Solaris");
    public static final boolean IS_OS_SUN_OS = getOSMatchesName("SunOS");
    public static final boolean IS_OS_UNIX;
    public static final boolean IS_OS_WINDOWS;
    private static final String OS_NAME_WINDOWS_PREFIX = "Windows";
    public static final boolean IS_OS_VERSION_WSL;
    private static final String OS_VERSION_WSL = "WSL";

    static {
        IS_OS_UNIX =
                IS_OS_AIX
                        || IS_OS_HP_UX
                        || IS_OS_IRIX
                        || IS_OS_LINUX
                        || IS_OS_MAC_OSX
                        || IS_OS_SOLARIS
                        || IS_OS_SUN_OS;
        IS_OS_WINDOWS = getOSMatchesName(OS_NAME_WINDOWS_PREFIX);
        IS_OS_VERSION_WSL = getOSContainsVersion(OS_VERSION_WSL);
    }

    private static String getSystemProperty(String property) {
        try {
            return System.getProperty(property);
        } catch (SecurityException var2) {
            return null;
        }
    }

    private static boolean getOSMatchesName(String osNamePrefix) {
        return isOSNameMatch(OS_NAME, osNamePrefix);
    }

    static boolean isOSNameMatch(String osName, String osNamePrefix) {
        return osName != null && osName.startsWith(osNamePrefix);
    }

    private static boolean getOSContainsVersion(String osVersionSearch) {
        return doesOSVersionContain(OS_VERSION, osVersionSearch);
    }

    static boolean doesOSVersionContain(String osVersion, String osVersionSearch) {
        return osVersion != null && osVersion.contains(osVersionSearch);
    }
}
