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

# "random" uid/gid hopefully not used anywhere else
# This needs to be set globally and then referenced in
# the subsequent stages -- see TIKA-3912
ARG UID_GID="35002:35002"

FROM ubuntu:resolute AS base

FROM base AS fetch_tika

ARG TIKA_VERSION
ARG CHECK_SIG=true

ENV TIKA_SERVER_ARCHIVE="tika-server-standard-${TIKA_VERSION}-bin.zip" \
    NEAREST_TIKA_SERVER_URL="https://dlcdn.apache.org/tika/${TIKA_VERSION}/tika-server-standard-${TIKA_VERSION}-bin.zip" \
    ARCHIVE_TIKA_SERVER_URL="https://archive.apache.org/dist/tika/${TIKA_VERSION}/tika-server-standard-${TIKA_VERSION}-bin.zip" \
    BACKUP_TIKA_SERVER_URL="https://downloads.apache.org/tika/${TIKA_VERSION}/tika-server-standard-${TIKA_VERSION}-bin.zip" \
    DEFAULT_TIKA_SERVER_ASC_URL="https://downloads.apache.org/tika/${TIKA_VERSION}/tika-server-standard-${TIKA_VERSION}-bin.zip.asc" \
    ARCHIVE_TIKA_SERVER_ASC_URL="https://archive.apache.org/dist/tika/${TIKA_VERSION}/tika-server-standard-${TIKA_VERSION}-bin.zip.asc" \
    TIKA_VERSION=$TIKA_VERSION

# 4.x publishes tika-server as a bin.zip distribution. The thin top-level
# tika-server.jar uses its manifest Class-Path to resolve the jars under lib/,
# and tika-server reads pf4j plugins from the plugins/ directory next to it.
RUN DEBIAN_FRONTEND=noninteractive apt-get update && apt-get -y install gnupg2 wget ca-certificates unzip \
    && wget -t 10 --max-redirect 1 --retry-connrefused -qO- https://downloads.apache.org/tika/KEYS | gpg --import \
    && wget -t 10 --max-redirect 1 --retry-connrefused $NEAREST_TIKA_SERVER_URL -O /${TIKA_SERVER_ARCHIVE} || rm /${TIKA_SERVER_ARCHIVE} \
    && sh -c "[ -f /${TIKA_SERVER_ARCHIVE} ]" || wget $ARCHIVE_TIKA_SERVER_URL -O /${TIKA_SERVER_ARCHIVE} || rm /${TIKA_SERVER_ARCHIVE} \
    && sh -c "[ -f /${TIKA_SERVER_ARCHIVE} ]" || wget $BACKUP_TIKA_SERVER_URL -O /${TIKA_SERVER_ARCHIVE} || rm /${TIKA_SERVER_ARCHIVE} \
    && sh -c "[ -f /${TIKA_SERVER_ARCHIVE} ]" || exit 1 \
    && wget -t 10 --max-redirect 1 --retry-connrefused $DEFAULT_TIKA_SERVER_ASC_URL -O /${TIKA_SERVER_ARCHIVE}.asc || rm /${TIKA_SERVER_ARCHIVE}.asc \
    && sh -c "[ -f /${TIKA_SERVER_ARCHIVE}.asc ]" || wget $ARCHIVE_TIKA_SERVER_ASC_URL -O /${TIKA_SERVER_ARCHIVE}.asc || rm /${TIKA_SERVER_ARCHIVE}.asc \
    && sh -c "[ -f /${TIKA_SERVER_ARCHIVE}.asc ]" || exit 1 \
    && gpg --verify /${TIKA_SERVER_ARCHIVE}.asc /${TIKA_SERVER_ARCHIVE} \
    && mkdir -p /opt/tika-server \
    && unzip -q /${TIKA_SERVER_ARCHIVE} -d /opt/tika-server \
    && rm /${TIKA_SERVER_ARCHIVE} /${TIKA_SERVER_ARCHIVE}.asc

FROM base AS runtime
ARG UID_GID
ARG JRE='openjdk-25-jre-headless'
ARG LANGUAGES='eng ita fra spa deu jpn'
RUN set -eux \
    && apt-get update \
    && apt-get install --yes --no-install-recommends gnupg2 software-properties-common \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends $JRE \
        gdal-bin \
        imagemagick \
        tesseract-ocr \
        $(printf 'tesseract-ocr-%s ' $LANGUAGES) \
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
ARG TIKA_VERSION
ENV TIKA_VERSION=$TIKA_VERSION

COPY --from=fetch_tika /opt/tika-server /opt/tika-server
# WORKDIR sets the CWD so tika-server's plugin-root fallback resolves
# `plugins/` relative to /opt/tika-server (its `getCodeSource()` returns a
# lib/* path, not the top-level jar, so the "next-to-jar" resolution misses).
WORKDIR /opt/tika-server
USER $UID_GID

EXPOSE 9998
# Classpath includes the thin server jar, its lib/ deps, and any user-mounted /tika-extras/.
# tika-server auto-discovers pf4j plugins from /opt/tika-server/plugins/.
ENTRYPOINT [ "/bin/sh", "-c", "exec java -cp \"/opt/tika-server/tika-server.jar:/opt/tika-server/lib/*:/tika-extras/*\" org.apache.tika.server.core.TikaServerCli -h 0.0.0.0 $0 $@"]

LABEL maintainer="Apache Tika Developers dev@tika.apache.org"

