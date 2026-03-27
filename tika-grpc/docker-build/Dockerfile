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

FROM ubuntu:plucky
COPY libs/ /tika/libs/
COPY plugins/ /tika/plugins/
COPY config/ /tika/config/
COPY bin/ /tika/bin
ARG JRE='openjdk-17-jre-headless'
ARG VERSION
ARG TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE=104857600
ARG TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE=104857600
ARG TIKA_GRPC_NUM_THREADS=4
RUN set -eux \
    && apt-get update \
    && apt-get install --yes --no-install-recommends gnupg2 software-properties-common \
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

EXPOSE 9090
ENV TIKA_VERSION=$VERSION
ENV TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE=$TIKA_GRPC_MAX_INBOUND_MESSAGE_SIZE
ENV TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE=$TIKA_GRPC_MAX_OUTBOUND_MESSAGE_SIZE
ENV TIKA_GRPC_NUM_THREADS=$TIKA_GRPC_NUM_THREADS
RUN chmod +x "/tika/bin/start-tika-grpc.sh"
ENTRYPOINT ["/tika/bin/start-tika-grpc.sh"]

LABEL maintainer="Apache Tika Developers dev@tika.apache.org"
