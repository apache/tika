#!/bin/bash

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


#############################################################################
# See https://cwiki.apache.org/confluence/display/TIKA/TikaAndNER for details
# on how to configure additional NER libraries
#############################################################################

# ------------------------------------
# Download OpenNLP Models to classpath
# ------------------------------------

OPENNLP_LOCATION="/ner/org/apache/tika/parser/ner/opennlp"
URL="http://opennlp.sourceforge.net/models-1.5"

mkdir -p $OPENNLP_LOCATION
if [ "$(ls -A $OPENNLP_LOCATION/*.bin)" ]; then
    echo "OpenNLP models directory has files, so skipping fetch";
else
	echo "No OpenNLP models found, so fetching them"
	wget "$URL/en-ner-person.bin" -O $OPENNLP_LOCATION/ner-person.bin
	wget "$URL/en-ner-location.bin" -O $OPENNLP_LOCATION/ner-location.bin
	wget "$URL/en-ner-organization.bin" -O $OPENNLP_LOCATION/ner-organization.bin;
	wget "$URL/en-ner-date.bin" -O $OPENNLP_LOCATION/ner-date.bin
	wget "$URL/en-ner-time.bin" -O $OPENNLP_LOCATION/ner-time.bin
	wget "$URL/en-ner-percentage.bin" -O $OPENNLP_LOCATION/ner-percentage.bin
	wget "$URL/en-ner-money.bin" -O $OPENNLP_LOCATION/ner-money.bin
fi

# --------------------------------------------
# Create RexExp Example for Email on classpath
# --------------------------------------------
REGEXP_LOCATION="/ner/org/apache/tika/parser/ner/regex"
mkdir -p $REGEXP_LOCATION
echo "EMAIL=(?:[a-z0-9!#$%&'*+/=?^_\`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_\`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])" > $REGEXP_LOCATION/ner-regex.txt


# -------------------
# Now run Tika Server
# -------------------

# Can be a single implementation or comma seperated list for multiple for "ner.impl.class" property
RECOGNISERS=org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser,org.apache.tika.parser.ner.regex.RegexNERecogniser
# Set classpath to the Tika Server JAR and the /ner folder so it has the configuration and models from above
CLASSPATH="/ner:/tika-server-standard-${TIKA_VERSION}.jar:/tika-extras/*"
# Run the server with the custom configuration ner.impl.class property and custom /ner/tika-config.xml
exec java -Dner.impl.class=$RECOGNISERS -cp $CLASSPATH org.apache.tika.server.core.TikaServerCli -h 0.0.0.0 -c /ner/tika-config.xml