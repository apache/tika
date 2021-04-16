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

package org.apache.tika.server.mbean;

import org.apache.tika.server.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Heps setup custom mBeans.
 */
public class MBeanHelper {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(MBeanHelper.class);

    /**
     * Registers MBean server bean for server status (via exporter).
     *
     * @param serverStatus the server status to expose.
     */
    public static void registerServerStatusMBean(ServerStatus serverStatus) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ServerStatusExporter mbean = new ServerStatusExporter(serverStatus);
            final Class<? extends ServerStatusExporter> objectClass = mbean.getClass();
            // Construct the ObjectName for the MBean we will register
            ObjectName mbeanName = new ObjectName(
                    String.format(Locale.ROOT, "%s:type=basic,name=%s", objectClass.getPackage().getName(), objectClass.getSimpleName())
            );
            server.registerMBean(mbean, mbeanName);
            LOG.info("Registered Server Status MBean with objectname : {}", mbeanName);
        } catch (Exception e) {
            LOG.warn("Error registering MBean for status", e);
        }
    }

}
