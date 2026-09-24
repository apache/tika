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
package org.apache.tika.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Runs each test JVM under a random default locale, as Lucene's test framework does, so
 * locale-sensitive code paths are exercised across the JDK's whole locale set over time
 * rather than in one pinned locale. Registered through {@code META-INF/services}; the
 * launcher picks it up from the test classpath.
 * <p>
 * {@code -Dtika.test.locale=<language tag>} pins the locale (as printed by a failed run),
 * {@code -Dtika.test.locale=system} keeps the JVM's own. The effective language tag is
 * published under the same property, and every failure carries it as a suppressed
 * exception so a surefire report shows how to reproduce.
 */
public class RandomLocaleListener implements LauncherSessionListener, TestExecutionListener {

    public static final String PROPERTY = "tika.test.locale";

    private static volatile boolean reported;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Locale locale = choose(System.getProperty(PROPERTY));
        Locale.setDefault(locale);
        System.setProperty(PROPERTY, locale.toLanguageTag());
        System.out.println("[tika] default locale " + locale.toLanguageTag() +
                " (reproduce with -D" + PROPERTY + "=" + locale.toLanguageTag() + ")");
    }

    static Locale choose(String setting) {
        if (setting == null || setting.isEmpty() || "random".equals(setting)) {
            List<Locale> candidates = candidates();
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        if ("system".equals(setting)) {
            return Locale.getDefault();
        }
        Locale locale = Locale.forLanguageTag(setting);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("not a language tag: -D" + PROPERTY + "=" + setting);
        }
        return locale;
    }

    /** Available locales whose language tag reproduces them; no_NO_NY, for one, does not. */
    static List<Locale> candidates() {
        List<Locale> candidates = new ArrayList<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            if (!locale.getLanguage().isEmpty() &&
                    locale.equals(Locale.forLanguageTag(locale.toLanguageTag()))) {
                candidates.add(locale);
            }
        }
        return candidates;
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        if (result.getStatus() != TestExecutionResult.Status.FAILED) {
            return;
        }
        String tag = System.getProperty(PROPERTY);
        if (tag == null) {
            return;
        }
        result.getThrowable().ifPresent(t -> t.addSuppressed(new LocaleNote(tag)));
        if (!reported) {
            reported = true;
            System.err.println("[tika] failure under default locale " + tag +
                    "; reproduce with -D" + PROPERTY + "=" + tag);
        }
    }

    /** Attached to a failure so the reproduction hint survives into the surefire report. */
    public static final class LocaleNote extends Exception {
        LocaleNote(String tag) {
            super("default locale was " + tag + "; reproduce with -D" + PROPERTY + "=" + tag,
                    null, false, false);
        }
    }
}
