FROM ubuntu:latest
COPY libs/ /tika/libs/
COPY plugins/ /tika/plugins/
COPY config/ /tika/config/
COPY bin/ /tika/bin
ARG JRE='openjdk-17-jre-headless'
ARG VERSION='4.0.0-SNAPSHOT'
ARG TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE=104857600
ARG TIKA_PIPES_MAX_OUTBOUND_MESSAGE_SIZE=104857600
ARG TIKA_PIPES_GRPC_NUM_THREADS=4
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

EXPOSE 8080
EXPOSE 9090
ENV TIKA_PIPES_VERSION=$VERSION
ENV TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE=$TIKA_PIPES_MAX_INBOUND_MESSAGE_SIZE
ENV TIKA_PIPES_MAX_OUTBOUND_MESSAGE_SIZE=$TIKA_PIPES_MAX_OUTBOUND_MESSAGE_SIZE
ENV TIKA_PIPES_GRPC_NUM_THREADS=$TIKA_PIPES_GRPC_NUM_THREADS
RUN chmod +x "/tika/bin/start-tika-grpc.sh"
ENTRYPOINT ["/tika/bin/start-tika-grpc.sh"]
