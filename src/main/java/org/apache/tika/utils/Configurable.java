/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

/**
 * @author mattmann
 * @version $Revision$
 * 
 * <p>
 * An interface allowing a Tika object to be <code>Configured</code> by a
 * {@link Configuration} object. Based on Apache Hadoop's configuration
 * interface.
 * </p>.
 */
public interface Configurable {

    /**
     * Configures the Tika object with the provided {@link Configuration} named
     * <code>conf</code>.
     * 
     * @param conf
     *            The object's new {@link Configuration}.
     */
    public void setConf(Configuration conf);

    /**
     * 
     * @return The Tika object's existing {@link Configuration}.
     */
    public Configuration getConf();

}
