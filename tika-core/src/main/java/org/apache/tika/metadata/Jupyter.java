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

/**
 * Properties of a Jupyter notebook, read from its top-level metadata.
 *
 * @since Apache Tika 4.3.0
 */
public interface Jupyter {

    String JUPYTER_PREFIX = "jupyter" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * The kernel the notebook was saved with, {@code metadata.kernelspec.name},
     * e.g. &quot;python3&quot;.
     */
    Property KERNEL = Property.externalText(JUPYTER_PREFIX + "kernel");

    /**
     * The programming language of the notebook, {@code metadata.language_info.name},
     * else {@code metadata.kernelspec.language}.
     */
    Property LANGUAGE = Property.externalText(JUPYTER_PREFIX + "language");

    /**
     * The notebook format version as major.minor, e.g. &quot;4.5&quot;.
     */
    Property NBFORMAT = Property.externalText(JUPYTER_PREFIX + "nbformat");
}
