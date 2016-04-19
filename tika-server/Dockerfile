#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#    http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.

FROM ubuntu:latest
MAINTAINER Apache Tika Team

ENV TIKA_VERSION 1.7
ENV TIKA_SERVER_URL https://www.apache.org/dist/tika/tika-server-$TIKA_VERSION.jar

RUN	apt-get update \
	&& apt-get install openjdk-7-jre-headless curl gdal-bin tesseract-ocr \
		tesseract-ocr-eng tesseract-ocr-ita tesseract-ocr-fra tesseract-ocr-spa tesseract-ocr-deu -y \
	&& curl -sSL https://people.apache.org/keys/group/tika.asc -o /tmp/tika.asc \
	&& gpg --import /tmp/tika.asc \
	&& curl -sSL "$TIKA_SERVER_URL.asc" -o /tmp/tika-server-${TIKA_VERSION}.jar.asc \
	&& NEAREST_TIKA_SERVER_URL=$(curl -sSL http://www.apache.org/dyn/closer.cgi/${TIKA_SERVER_URL#https://www.apache.org/dist/}\?asjson\=1 \
		| awk '/"path_info": / { pi=$2; }; /"preferred":/ { pref=$2; }; END { print pref " " pi; };' \
		| sed -r -e 's/^"//; s/",$//; s/" "//') \
	&& echo "Nearest mirror: $NEAREST_TIKA_SERVER_URL" \
	&& curl -sSL "$NEAREST_TIKA_SERVER_URL" -o /tika-server-${TIKA_VERSION}.jar \
	&& gpg --verify /tmp/tika-server-${TIKA_VERSION}.jar.asc /tika-server-${TIKA_VERSION}.jar \
	&& apt-get clean -y && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

EXPOSE 9998
ENTRYPOINT java -jar /tika-server-${TIKA_VERSION}.jar -h 0.0.0.0
