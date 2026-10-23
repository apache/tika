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
package org.apache.tika.bundle;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.osgi.framework.launch.FrameworkFactory;

public class FelixBundleIT extends BundleIT {

    @Override
    protected FrameworkFactory frameworkFactory() {
        // TODO: drop once Felix formats its JavaSE version with Locale.ROOT (Util.java:168 in
        // 7.0.5 builds "0.0.0.JavaSE_%03d" in the default locale; Arabic-Indic digits make an
        // invalid qualifier, the system bundle exports no java.* and nothing resolves)
        assumeTrue(DecimalFormatSymbols.getInstance(Locale.getDefault()).getZeroDigit() == '0',
                "Felix cannot start under a locale with non-ASCII digits");
        return new org.apache.felix.framework.FrameworkFactory();
    }
}
