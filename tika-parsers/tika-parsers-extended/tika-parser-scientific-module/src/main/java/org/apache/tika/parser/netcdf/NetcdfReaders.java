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
package org.apache.tika.parser.netcdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import ucar.nc2.NetcdfFile;

import org.apache.tika.exception.TikaException;

/**
 * Opens a file with the netcdf-java reader for its format, chosen from the file's signature.
 * A parser passes the readers for the formats it reads; any other file is refused.
 */
public final class NetcdfReaders {

    public static final String NETCDF3 = "ucar.nc2.iosp.netcdf3.N3raf";
    public static final String HDF5 = "ucar.nc2.iosp.hdf5.H5iosp";
    public static final String HDF4 = "ucar.nc2.iosp.hdf4.H4iosp";
    public static final String GRIB1 = "ucar.nc2.grib.collection.Grib1Iosp";
    public static final String GRIB2 = "ucar.nc2.grib.collection.Grib2Iosp";

    private static final int HEAD_LENGTH = 8;
    private static final int BUFFER_SIZE = 8092;

    private NetcdfReaders() {
    }

    public static NetcdfFile open(Path file, String... allowed)
            throws IOException, TikaException {
        byte[] head = new byte[HEAD_LENGTH];
        int n;
        try (InputStream is = Files.newInputStream(file)) {
            n = IOUtils.read(is, head);
        }
        String reader = allowedReader(head, n, allowed);
        try {
            return NetcdfFile.open(file.toAbsolutePath().toString(), reader, BUFFER_SIZE, null,
                    null);
        } catch (ReflectiveOperationException e) {
            throw new TikaException("netcdf-java reader is not available: " + reader, e);
        } catch (RuntimeException e) {
            throw new TikaException("netcdf-java could not read the file", e);
        }
    }

    public static NetcdfFile openInMemory(byte[] data, String... allowed)
            throws IOException, TikaException {
        String reader = allowedReader(data, data.length, allowed);
        try {
            // a fixed name
            return NetcdfFile.openInMemory("input", data, reader);
        } catch (ReflectiveOperationException e) {
            throw new TikaException("netcdf-java reader is not available: " + reader, e);
        } catch (RuntimeException e) {
            throw new TikaException("netcdf-java could not read the file", e);
        }
    }

    private static String allowedReader(byte[] head, int length, String[] allowed)
            throws TikaException {
        String reader = forSignature(head, length);
        if (reader == null || !Arrays.asList(allowed).contains(reader)) {
            throw new TikaException("not a format this parser reads");
        }
        return reader;
    }

    /**
     * @return the reader for the signature at the start of the file, or null if none
     */
    static String forSignature(byte[] head, int length) {
        if (length < HEAD_LENGTH) {
            return null;
        }
        if (head[0] == 'C' && head[1] == 'D' && head[2] == 'F' && (head[3] == 1 || head[3] == 2)) {
            return NETCDF3;
        }
        if ((head[0] & 0xff) == 0x89 && head[1] == 'H' && head[2] == 'D' && head[3] == 'F'
                && head[4] == '\r' && head[5] == '\n' && head[6] == 0x1a && head[7] == '\n') {
            return HDF5;
        }
        if (head[0] == 0x0e && head[1] == 0x03 && head[2] == 0x13 && head[3] == 0x01) {
            return HDF4;
        }
        if (head[0] == 'G' && head[1] == 'R' && head[2] == 'I' && head[3] == 'B') {
            if (head[7] == 1) {
                return GRIB1;
            }
            if (head[7] == 2) {
                return GRIB2;
            }
        }
        return null;
    }
}
