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
package org.apache.tika.io;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import static java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Copied/pasted from the Apache Lucene/Solr project.
 */
public class MappedBufferCleaner {
    /** Reference to a BufferCleaner that does unmapping; {@code null} if not supported. */
    private static final BufferCleaner CLEANER;

    /**
     * <code>true</code>, if this platform supports unmapping mmapped files.
     */
    public static final boolean UNMAP_SUPPORTED;

    /**
     * if {@link #UNMAP_SUPPORTED} is {@code false}, this contains the reason why unmapping is not supported.
     */
    public static final String UNMAP_NOT_SUPPORTED_REASON;

    static {
        final Object hack = AccessController.doPrivileged((PrivilegedAction<Object>) MappedBufferCleaner::unmapHackImpl);
        if (hack instanceof BufferCleaner) {
            CLEANER = (BufferCleaner) hack;
            UNMAP_SUPPORTED = true;
            UNMAP_NOT_SUPPORTED_REASON = null;
        } else {
            CLEANER = null;
            UNMAP_SUPPORTED = false;
            UNMAP_NOT_SUPPORTED_REASON = hack.toString();
        }
    }

    /**
     * If a cleaner is available, this buffer will be cleaned.
     * Otherwise, this is a no-op.
     *
     * @param b buffer to clean; no-op if buffer is null
     * @throws IOException
     */
    public static void freeBuffer(ByteBuffer b) throws IOException {
        if (CLEANER != null && b != null) {
            CLEANER.freeBuffer("", b);
        }
    }
    //Copied/pasted from Lucene's MMapDirectory
    private interface BufferCleaner {
        void freeBuffer(String resourceDescription, ByteBuffer b) throws IOException;
    }

    //"Needs access to private APIs in DirectBuffer, sun.misc.Cleaner, and sun.misc.Unsafe to enable hack")
    private static Object unmapHackImpl() {
        final Lookup lookup = lookup();
        try {
            try {
                // *** sun.misc.Unsafe unmapping (Java 9+) ***
                final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                // first check if Unsafe has the right method, otherwise we can give up
                // without doing any security critical stuff:
                final MethodHandle unmapper = lookup.findVirtual(unsafeClass, "invokeCleaner",
                        methodType(void.class, ByteBuffer.class));
                // fetch the unsafe instance and bind it to the virtual MH:
                final Field f = unsafeClass.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                final Object theUnsafe = f.get(null);
                return newBufferCleaner(ByteBuffer.class, unmapper.bindTo(theUnsafe));
            } catch (SecurityException se) {
                // rethrow to report errors correctly (we need to catch it here, as we also catch RuntimeException below!):
                throw se;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // *** sun.misc.Cleaner unmapping (Java 8) ***
                final Class<?> directBufferClass = Class.forName("java.nio.DirectByteBuffer");

                final Method m = directBufferClass.getMethod("cleaner");
                m.setAccessible(true);
                final MethodHandle directBufferCleanerMethod = lookup.unreflect(m);
                final Class<?> cleanerClass = directBufferCleanerMethod.type().returnType();

                /* "Compile" a MH that basically is equivalent to the following code:
                 * void unmapper(ByteBuffer byteBuffer) {
                 *   sun.misc.Cleaner cleaner = ((java.nio.DirectByteBuffer) byteBuffer).cleaner();
                 *   if (Objects.nonNull(cleaner)) {
                 *     cleaner.clean();
                 *   } else {
                 *     noop(cleaner); // the noop is needed because MethodHandles#guardWithTest always needs ELSE
                 *   }
                 * }
                 */
                final MethodHandle cleanMethod = lookup.findVirtual(cleanerClass, "clean", methodType(void.class));
                final MethodHandle nonNullTest = lookup.findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class))
                        .asType(methodType(boolean.class, cleanerClass));
                final MethodHandle noop = dropArguments(constant(Void.class, null).asType(methodType(void.class)), 0, cleanerClass);
                final MethodHandle unmapper = filterReturnValue(directBufferCleanerMethod, guardWithTest(nonNullTest, cleanMethod, noop))
                        .asType(methodType(void.class, ByteBuffer.class));
                return newBufferCleaner(directBufferClass, unmapper);
            }
        } catch (SecurityException se) {
            return "Unmapping is not supported, because not all required permissions are given to the Tika JAR file: " + se +
                    " [Please grant at least the following permissions: RuntimePermission(\"accessClassInPackage.sun.misc\") " +
                    " and ReflectPermission(\"suppressAccessChecks\")]";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Unmapping is not supported on this platform, because internal Java APIs are not compatible with this Lucene version: " + e;
        }
    }

    private static BufferCleaner newBufferCleaner(final Class<?> unmappableBufferClass, final MethodHandle unmapper) {
        assert Objects.equals(methodType(void.class, ByteBuffer.class), unmapper.type());
        return (String resourceDescription, ByteBuffer buffer) -> {
            if (!buffer.isDirect()) {
                throw new IllegalArgumentException("unmapping only works with direct buffers");
            }
            if (!unmappableBufferClass.isInstance(buffer)) {
                throw new IllegalArgumentException("buffer is not an instance of " + unmappableBufferClass.getName());
            }
            final Throwable error = AccessController.doPrivileged((PrivilegedAction<Throwable>) () -> {
                try {
                    unmapper.invokeExact(buffer);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
            if (error != null) {
                throw new IOException("Unable to unmap the mapped buffer: " + resourceDescription, error);
            }
        };
    }
}
