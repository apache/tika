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
package org.apache.tika.metadata;

/** Metadata for describing machines, such as their architecture, type and endian-ness */
public interface MachineMetadata {
    String PREFIX = "machine:";

    Property ARCHITECTURE_BITS =
            Property.internalClosedChoise(PREFIX + "architectureBits", "8", "16", "32", "64");

    String PLATFORM_SYSV = "System V";
    String PLATFORM_HPUX = "HP-UX";
    String PLATFORM_NETBSD = "NetBSD";
    String PLATFORM_LINUX = "Linux";
    String PLATFORM_SOLARIS = "Solaris";
    String PLATFORM_AIX = "AIX";
    String PLATFORM_IRIX = "IRIX";
    String PLATFORM_FREEBSD = "FreeBSD";
    String PLATFORM_TRU64 = "Tru64";
    String PLATFORM_ARM = "ARM"; // ARM architecture ABI
    String PLATFORM_EMBEDDED = "Embedded"; // Stand-alone (embedded) ABI
    String PLATFORM_WINDOWS = "Windows";

    Property PLATFORM =
            Property.internalClosedChoise(
                    PREFIX + "platform",
                    PLATFORM_SYSV,
                    PLATFORM_HPUX,
                    PLATFORM_NETBSD,
                    PLATFORM_LINUX,
                    PLATFORM_SOLARIS,
                    PLATFORM_AIX,
                    PLATFORM_IRIX,
                    PLATFORM_FREEBSD,
                    PLATFORM_TRU64,
                    PLATFORM_ARM,
                    PLATFORM_EMBEDDED,
                    PLATFORM_WINDOWS);

    String MACHINE_x86_32 = "x86-32";
    String MACHINE_x86_64 = "x86-64";
    String MACHINE_IA_64 = "IA-64";
    String MACHINE_SPARC = "SPARC";
    String MACHINE_M68K = "Motorola-68000";
    String MACHINE_M88K = "Motorola-88000";
    String MACHINE_MIPS = "MIPS";
    String MACHINE_PPC = "PPC";
    String MACHINE_S370 = "S370";
    String MACHINE_S390 = "S390";
    String MACHINE_ARM = "ARM";
    String MACHINE_VAX = "Vax";
    String MACHINE_ALPHA = "Alpha";
    String MACHINE_EFI = "EFI"; // EFI ByteCode
    String MACHINE_M32R = "M32R";
    String MACHINE_SH3 = "SH3";
    String MACHINE_SH4 = "SH4";
    String MACHINE_SH5 = "SH5";
    String MACHINE_UNKNOWN = "Unknown";

    Property MACHINE_TYPE =
            Property.internalClosedChoise(
                    PREFIX + "machineType",
                    MACHINE_x86_32,
                    MACHINE_x86_64,
                    MACHINE_IA_64,
                    MACHINE_SPARC,
                    MACHINE_M68K,
                    MACHINE_M88K,
                    MACHINE_MIPS,
                    MACHINE_PPC,
                    MACHINE_S370,
                    MACHINE_S390,
                    MACHINE_ARM,
                    MACHINE_VAX,
                    MACHINE_ALPHA,
                    MACHINE_EFI,
                    MACHINE_M32R,
                    MACHINE_SH3,
                    MACHINE_SH4,
                    MACHINE_SH5,
                    MACHINE_UNKNOWN);
    Property ENDIAN =
            Property.internalClosedChoise(PREFIX + "endian", Endian.LITTLE.name, Endian.BIG.name);

    final class Endian {
        public static final Endian LITTLE = new Endian("Little", false);
        public static final Endian BIG = new Endian("Big", true);
        private final String name;
        private final boolean msb;

        private Endian(String name, boolean msb) {
            this.name = name;
            this.msb = msb;
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("unused")
        public boolean isMSB() {
            return msb;
        }

        @SuppressWarnings("unused")
        public String getMSB() {
            if (msb) {
                return "MSB";
            } else {
                return "LSB";
            }
        }
    }
}
