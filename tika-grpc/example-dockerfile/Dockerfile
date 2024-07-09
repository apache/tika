
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
FROM ubuntu:latest
COPY libs/ /tika/libs/
COPY tika-config.xml /tika/config/tika-config.xml
COPY log4j2.xml /tika/config/log4j2.xml
ARG JRE='openjdk-17-jre-headless'
RUN set -eux \
    && apt-get update \
    && apt-get install --yes --no-install-recommends gnupg2 software-properties-common \
    && add-apt-repository -y ppa:alex-p/tesseract-ocr5 \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends $JRE \
        gdal-bin \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-ita \
        tesseract-ocr-fra \
        tesseract-ocr-spa \
        tesseract-ocr-deu \
    && echo ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true | debconf-set-selections \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        xfonts-utils \
        fonts-freefont-ttf \
        fonts-liberation \
        ttf-mscorefonts-installer \
        wget \
        cabextract \
    && apt-get clean -y \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

EXPOSE 50051
ENTRYPOINT [ "/bin/sh", "-c", "exec java -Dlog4j.configurationFile=/tika/config/log4j2.xml -cp \"/tika/libs/*\" org.apache.tika.pipes.grpc.TikaGrpcServer --port 50051 --tika-config /tika/config/tika-config.xml"]
