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

package org.apache.tika.parser.pot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuration for PooledTimeSeriesParser.
 *
 * This allows to enable PooledTimeSeriesParser and set its parameters:
 * <p>
 * PooledTimeSeriesConfig config = new PooledTimeSeriesConfig();<br>
 * config.setOpenCVPath(opencvFolder);<br>
 * parseContext.set(PooledTimeSeriesConfig.class, config);<br>
 * </p>
 *
 * Parameters can also be set by either editing the existing PooledTimeSeriesConfig.properties file in,
 * tika-parser/src/main/resources/org/apache/tika/parser/pot, or overriding it by creating your own
 * and placing it in the package org/apache/tika/parser/pot on the classpath.
 *
 */
public class PooledTimeSeriesConfig implements Serializable {

    private static final long serialVersionUID = -4861942486845757891L;

    // Path to OpenCV installation folder, if not on system path.
    private  String opencvPath = "";

    // Path to PooledTimeSeries program
    private String pooledTimeSeriesPath = "";

    // Maximum time (seconds) to wait for the pot-ing process termination
    private int timeout = 84600;

    /**
     * Default contructor.
     */
    public PooledTimeSeriesConfig() {
        init(this.getClass().getResourceAsStream("PooledTimeSeriesConfig.properties"));
    }

    /**
     * Loads properties from InputStream and then tries to close InputStream.
     * If there is an IOException, this silently swallows the exception
     * and goes back to the default.
     *
     * @param is
     */
    public PooledTimeSeriesConfig(InputStream is) {
        init(is);
    }

    private void init(InputStream is) {
        if (is == null) {
            return;
        }
        Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException e) {
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //swallow
                }
            }
        }

        setOpenCVPath(
                getProp(props, "opencvPath", getOpenCVPath()));
        setPooledTimeSeriesPath(
                getProp(props, "pooledTimeSeriesPath", getOpenCVPath()));
        setTimeout(
                getProp(props, "timeout", getTimeout()));

    }

    /** @see #setOpenCVPath(String tesseractPath)*/
    public String getOpenCVPath() {
        return opencvPath;
    }

    /**
     * Set the path to the OpenCV executable, needed if it is not on system path.
     */
    public void setOpenCVPath(String opencvPath) {
        if(!opencvPath.isEmpty() && !opencvPath.endsWith(File.separator))
            opencvPath += File.separator;

        this.opencvPath = opencvPath;
    }

    public String getPooledTimeSeriesPath() {
        return pooledTimeSeriesPath;
    }

    public void setPooledTimeSeriesPath(String pooledTimeSeriesPath) {
        this.pooledTimeSeriesPath = pooledTimeSeriesPath;
    }

    /**
     * Set maximum time (seconds) to wait for the pooled time series process to terminate.
     * Default value is 120s.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /** @see #setTimeout(int timeout)*/
    public int getTimeout() {
        return timeout;
    }

    /**
     * Get property from the properties file passed in.
     * @param properties properties file to read from.
     * @param property the property to fetch.
     * @param defaultMissing default parameter to use.
     * @return the value.
     */
    private int getProp(Properties properties, String property, int defaultMissing) {
        String p = properties.getProperty(property);
        if (p == null || p.isEmpty()){
            return defaultMissing;
        }
        try {
            return Integer.parseInt(p);
        } catch (Throwable ex) {
            throw new RuntimeException(String.format(Locale.ROOT, "Cannot parse PooledTimeSeriesConfig variable %s, invalid integer value",
                    property), ex);
        }
    }

    /**
     * Get property from the properties file passed in.
     * @param properties properties file to read from.
     * @param property the property to fetch.
     * @param defaultMissing default parameter to use.
     * @return the value.
     */
    private String getProp(Properties properties, String property, String defaultMissing) {
        return properties.getProperty(property, defaultMissing);
    }
}
