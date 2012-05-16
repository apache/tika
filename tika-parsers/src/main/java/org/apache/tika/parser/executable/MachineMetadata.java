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
package org.apache.tika.parser.executable;

import org.apache.tika.metadata.Property;

/**
 * Metadata for describing machines, such as their
 *  architecture, type and endian-ness
 */
public interface MachineMetadata {
    public static final String PREFIX = "machine:";
   
    public static Property ARCHITECTURE_BITS = Property.internalClosedChoise(PREFIX+"architectureBits", 
         new String[] { "8", "16", "32", "64" });

    public static final String PLATFORM_SYSV    = "System V";
    public static final String PLATFORM_HPUX    = "HP-UX";
    public static final String PLATFORM_NETBSD  = "NetBSD";
    public static final String PLATFORM_LINUX   = "Linux";
    public static final String PLATFORM_SOLARIS = "Solaris";
    public static final String PLATFORM_AIX     = "AIX";
    public static final String PLATFORM_IRIX    = "IRIX";
    public static final String PLATFORM_FREEBSD = "FreeBSD";
    public static final String PLATFORM_TRU64   = "Tru64";
    public static final String PLATFORM_ARM     = "ARM"; // ARM architecture ABI
    public static final String PLATFORM_EMBEDDED = "Embedded"; // Stand-alone (embedded) ABI
    public static final String PLATFORM_WINDOWS = "Windows";
    
    public static Property PLATFORM = Property.internalClosedChoise(PREFIX+"platform", 
          new String[] { PLATFORM_SYSV, PLATFORM_HPUX, PLATFORM_NETBSD, PLATFORM_LINUX,
                         PLATFORM_SOLARIS, PLATFORM_AIX, PLATFORM_IRIX, PLATFORM_FREEBSD, PLATFORM_TRU64,
                         PLATFORM_ARM, PLATFORM_EMBEDDED, PLATFORM_WINDOWS });
    
    public static final String MACHINE_x86_32 = "x86-32";
    public static final String MACHINE_x86_64 = "x86-64";
    public static final String MACHINE_IA_64  = "IA-64";
    public static final String MACHINE_SPARC  = "SPARC";
    public static final String MACHINE_M68K   = "Motorola-68000";
    public static final String MACHINE_M88K   = "Motorola-88000";
    public static final String MACHINE_MIPS   = "MIPS";
    public static final String MACHINE_PPC    = "PPC";
    public static final String MACHINE_S370   = "S370";
    public static final String MACHINE_S390   = "S390";
    public static final String MACHINE_ARM    = "ARM";
    public static final String MACHINE_VAX    = "Vax";
    public static final String MACHINE_ALPHA  = "Alpha";
    public static final String MACHINE_EFI    = "EFI"; // EFI ByteCode
    public static final String MACHINE_M32R   = "M32R";
    public static final String MACHINE_SH3    = "SH3";
    public static final String MACHINE_SH4    = "SH4";
    public static final String MACHINE_SH5    = "SH5";
    public static final String MACHINE_UNKNOWN = "Unknown";
    
    public static Property MACHINE_TYPE = Property.internalClosedChoise(PREFIX+"machineType", 
          new String[] { MACHINE_x86_32, MACHINE_x86_64, MACHINE_IA_64, MACHINE_SPARC,
                         MACHINE_M68K, MACHINE_M88K, MACHINE_MIPS, MACHINE_PPC, 
                         MACHINE_S370, MACHINE_S390,
                         MACHINE_ARM, MACHINE_VAX, MACHINE_ALPHA, MACHINE_EFI, MACHINE_M32R,
                         MACHINE_SH3, MACHINE_SH4, MACHINE_SH5, MACHINE_UNKNOWN });
    
    public static final class Endian {
       private String name;
       private boolean msb;
       public String getName() { return name; }
       public boolean isMSB() { return msb; }
       public String getMSB() { if(msb) { return "MSB"; } else { return "LSB"; } }
       private Endian(String name, boolean msb) { this.name = name; this.msb = msb; }
       
       public static final Endian LITTLE = new Endian("Little", false);
       public static final Endian BIG = new Endian("Big", true);
    }
    public static Property ENDIAN = Property.internalClosedChoise(PREFIX+"endian", 
          new String[] { Endian.LITTLE.name, Endian.BIG.name });
}
