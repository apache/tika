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
package org.apache.tika.parser.hook;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TransientParseState;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * The hooks a parse runs, in config order. Pinning and offering never fail a document: a
 * problem is a warning on that document's metadata.
 *
 * @since Apache Tika 4.1
 */
public final class ParseHooks implements TransientParseState {

    private static final Logger LOG = LoggerFactory.getLogger(ParseHooks.class);

    /** Marks a top-level parse in progress; tracks the documents being parsed, outermost last. */
    public static final class Run implements TransientParseState {
        private final Deque<Metadata> open = new ArrayDeque<>();

        /** Enters a document; returns the one whose parser embedded it, null at the top. */
        public Metadata enter(Metadata metadata) {
            Metadata parent = open.peek();
            open.push(metadata);
            return parent;
        }

        public void exit() {
            open.pop();
        }

        /** The document whose parser is running; null between documents. */
        public Metadata current() {
            return open.peek();
        }

        /** The document the current one is embedded in; null at the top. */
        public Metadata parent() {
            Iterator<Metadata> it = open.iterator();
            if (!it.hasNext()) {
                return null;
            }
            it.next();
            return it.hasNext() ? it.next() : null;
        }
    }

    private final List<ParseHook> hooks;

    public ParseHooks(List<ParseHook> hooks) {
        this.hooks = List.copyOf(hooks);
    }

    public List<ParseHook> getHooks() {
        return hooks;
    }

    public boolean isEmpty() {
        return hooks.isEmpty();
    }

    public void start(Metadata root, ParseContext context) throws TikaException {
        for (ParseHook hook : hooks) {
            hook.start(root, context);
        }
    }

    /** The document's bytes as a file when a hook wants them, else null. */
    public Path pin(MediaType type, Metadata metadata, TikaInputStream tis, ParseContext context)
            throws TikaException {
        boolean wanted = false;
        for (ParseHook hook : hooks) {
            wanted |= hook.wants(type, metadata, context);
        }
        if (!wanted) {
            return null;
        }
        try {
            return tis.getPath();
        } catch (IOException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    "parse hook: could not pin bytes: " + e.getMessage());
            return null;
        }
    }

    public void offer(MediaType type, Metadata metadata, Metadata parent, Path bytes,
                      ParseContext context) {
        for (ParseHook hook : hooks) {
            try {
                hook.offer(type, metadata, parent, bytes, context);
            } catch (IOException | TikaException | RuntimeException e) {
                LOG.warn("parse hook {} failed on {}", hook.getClass().getName(), type, e);
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "parse hook " + hook.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /** Whether any hook wants the current document's pages rendered as images of this type. */
    public boolean wantsPages(MediaType renderType, ParseContext context) throws TikaException {
        Run run = context.get(Run.class);
        Metadata document = run == null ? null : run.current();
        if (document == null) {
            return false;
        }
        for (ParseHook hook : hooks) {
            if (hook.wantsPages(renderType, document, context)) {
                return true;
            }
        }
        return false;
    }

    /** A rendered page of the current document, from its own parser; never fails it. */
    public void offerPage(MediaType type, int page, Path bytes, ParseContext context) {
        Run run = context.get(Run.class);
        Metadata document = run == null ? null : run.current();
        if (document == null) {
            return;
        }
        Metadata parent = run.parent();
        for (ParseHook hook : hooks) {
            try {
                hook.offerPage(type, document, parent, page, bytes, context);
            } catch (IOException | TikaException | RuntimeException e) {
                LOG.warn("parse hook {} failed on page {}", hook.getClass().getName(), page, e);
                document.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "parse hook " + hook.getClass().getSimpleName() + " on page " + page
                                + ": " + e.getMessage());
            }
        }
    }

    public void end(Metadata root, boolean failed, ParseContext context) {
        for (ParseHook hook : hooks) {
            try {
                hook.end(root, failed, context);
            } catch (RuntimeException e) {
                LOG.warn("parse hook {} failed at end", hook.getClass().getName(), e);
                root.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "parse hook " + hook.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
