# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Settings here will override settings in existing env vars or in bin/tika.  The default shipped state
# of this file is completely commented.

# By default the script will use JAVA_HOME to determine which java
# to use, but you can set a specific path for Tika to use without
# affecting other Java applications on your server/workstation.
#TIKA_JAVA_HOME=""

# This controls the number of seconds that the Tika script will wait for
# Tika to start.  If the start fails, the script will
# give up waiting and display the last few lines of the logfile.
#TIKA_STOP_WAIT="180"

# Enable verbose GC logging...
#  * If this is unset, various default options will be selected depending on which JVM version is in use
#  * For Java 8: if this is set, additional params will be added to specify the log file & rotation
#  * For Java 9 or higher: each included opt param that starts with '-Xlog:gc', but does not include an
#    output specifier, will have a 'file' output specifier (as well as formatting & rollover options)
#    appended, using the effective value of the SOLR_LOGS_DIR.
#
#GC_LOG_OPTS='-Xlog:gc*'  # (Java 9+)
#GC_LOG_OPTS="-verbose:gc -XX:+PrintHeapAtGC -XX:+PrintGCDetails \
#  -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+PrintTenuringDistribution -XX:+PrintGCApplicationStoppedTime"

# These GC settings have shown to work well for a number of common Solr workloads.  Good for Tika?
#GC_TUNE=" \
#-XX:SurvivorRatio=4 \
#-XX:TargetSurvivorRatio=90 \
#-XX:MaxTenuringThreshold=8 \
#-XX:+UseConcMarkSweepGC \
#-XX:ConcGCThreads=4 -XX:ParallelGCThreads=4 \
#-XX:+CMSScavengeBeforeRemark \
#-XX:PretenureSizeThreshold=64m \
#-XX:+UseCMSInitiatingOccupancyOnly \
#-XX:CMSInitiatingOccupancyFraction=50 \
#-XX:CMSMaxAbortablePrecleanTime=6000 \
#-XX:+CMSParallelRemarkEnabled \
#-XX:+ParallelRefProcEnabled \
#-XX:-OmitStackTraceInFastThrow  etc.

# By default the start script uses UTC; override the timezone if needed
#TIKA_TIMEZONE="UTC"

# Anything you add to the TIKA_OPTS variable will be included in the java
# start command line as-is, in ADDITION to other options. If you specify the
# -a option on start script, those options will be appended as well. Examples:
#TIKA_OPTS="$TIKA_OPTS -Dsolr.autoSoftCommit.maxTime=3000"

# Location where the bin/tika script will save PID files for running instances
# If not set, the script will create PID files in $TIKA_TIP/bin
#TIKA_PID_DIR=

# Solr provides a default Log4J configuration xml file in server/resources
# however, you may want to customize the log settings and file appender location
# so you can point the script to use a different log4j2.xml file
#LOG4J_PROPS=/var/solr/log4j2.xml

# Changes the logging level. Valid values: ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF. Default is INFO
# This is an alternative to changing the rootLogger in log4j2.xml
#TIKA_LOG_LEVEL=INFO

# Location where Solr should write logs to. Absolute or relative to solr start dir
#TIKA_LOGS_DIR=logs

# Sets the port Tika binds to, default is 9998
#TIKA_PORT=9998

# This would be cool in Tika land.
# For a visual indication in the Admin UI of what type of environment this cluster is, configure
# a -Dsolr.environment property below. Valid values are prod, stage, test, dev, with an optional
# label or color, e.g. -Dsolr.environment=test,label=Functional+test,color=brown
#SOLR_OPTS="$SOLR_OPTS -Dsolr.environment=prod"
