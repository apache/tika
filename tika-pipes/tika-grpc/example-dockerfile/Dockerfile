FROM ubuntu:latest
COPY libs/ /tika/libs/
COPY tika-config.xml /tika/config/tika-config.xml
COPY log4j2.xml /tika/config/log4j2.xml
ARG JRE='openjdk-17-jre-headless'
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

EXPOSE 50051
ENTRYPOINT [ "/bin/sh", "-c", "exec java -Dlog4j.configurationFile=/tika/config/log4j2.xml -cp \"/tika/libs/*\" org.apache.tika.pipes.grpc.TikaGrpcServer --port 50051 --tika-config /tika/config/tika-config.xml"]
