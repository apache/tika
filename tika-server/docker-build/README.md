# tika-docker <!--- update this once we migrate to github actions(?) [![Build Status](https://api.travis-ci.com/apache/tika-docker.svg?branch=master)](https://travis-ci.com/github/apache/tika-docker) -->

This repo is used to create convenience Docker images for Apache Tika Server published as [apache/tika](https://hub.docker.com/r/apache/tika) on DockerHub by the [Apache Tika](http://tika.apache.org) Dev team

The images create a functional Apache Tika Server instance that contains the latest Ubuntu running the appropriate version's server on Port 9998 using Java 8 (until version 1.20), Java 11 (1.21 and 1.24.1), Java 14 (until 1.27/2.0.0), Java 16 (for 2.1.0), and Java 17 LTS for newer versions.

There is a minimal version, which contains only Apache Tika and it's core dependencies, and a full version, which also includes dependencies for the GDAL and Tesseract OCR parsers. To balance showing functionality versus the size of the full image, this file by default installs the language packs for the following languages:
* English
* French
* German
* Italian
* Spanish
* Japanese

To install more languages, set the build argument `LANGUAGES` or include your own custom packs using an ADD command.

## Available Tags

Each 4.x release publishes three tags per image, all pointing at the same
manifest digest:

- `apache/tika:<version>` — mutable, rolls forward on Docker-only rebuilds for the same Tika version.
- `apache/tika:<version>-<N>` — immutable, never reassigned. Pin to this if you want stability across rebuilds. `N=1` is the initial build; `N=2,3,...` for subsequent rebuilds (CVE fixes, base-image refresh, etc.).
- `apache/tika:latest` — rolling pointer to the newest **stable** release. Stays on 3.x until 4.0.0 GA; preview tags (`-alpha`, `-BETA`, `-RC`) do **not** displace it.

(Same scheme applies to the `-full` variants and to `apache/tika-grpc`, with
the caveat that `apache/tika-grpc:latest` always tracks the newest 4.x release
since there's no 3.x incumbent.)

Most recent tags:
- `latest`, `latest-full`: Apache Tika Server 3.3.0 (currently — moves to 4.0.0 at GA)
- `4.0.0-alpha-1`, `4.0.0-alpha-1-1`: Apache Tika Server 4.0.0-alpha-1 (Minimal, 4.x preview)
- `4.0.0-alpha-1-full`, `4.0.0-alpha-1-1-full`: Apache Tika Server 4.0.0-alpha-1 (Full, 4.x preview)

Legacy 3.x and earlier tags use the `<version>.<docker-build-number>`
convention (e.g. `3.3.0.0`, `3.2.3.0`). Those tags are immutable and still
pullable.

- `3.3.0.0`, `3.3.0.0`: Apache Tika Server 3.3.0.0 (Minimal)
- `3.3.0.0`, `3.3.0.0-full`: Apache Tika Server 3.3.0.0 (Full)
- `3.2.3.0`, `3.2.3.0`: Apache Tika Server 3.2.3.0 (Minimal)
- `3.2.3.0`, `3.2.3.0-full`: Apache Tika Server 3.2.3.0 (Full)
- `3.2.2.0`, `3.2.2.0`: Apache Tika Server 3.2.2.0 (Minimal)
- `3.2.2.0`, `3.2.2.0-full`: Apache Tika Server 3.2.2.0 (Full)
- `3.2.1.0`, `3.2.1.0`: Apache Tika Server 3.2.1.0 (Minimal)
- `3.2.1.0`, `3.2.1.0-full`: Apache Tika Server 3.2.1.0 (Full)
- `3.2.0.0`, `3.2.0.0`: Apache Tika Server 3.2.0.0 (Minimal)
- `3.2.0.0`, `3.2.0.0-full`: Apache Tika Server 3.2.0.0 (Full)
- `3.1.0.0`, `3.1.0.0`: Apache Tika Server 3.1.0.0 (Minimal)
- `3.1.0.0`, `3.1.0.0-full`: Apache Tika Server 3.1.0.0 (Full)
- `3.0.0.0`, `3.0.0.0`: Apache Tika Server 3.0.0.0 (Minimal)
- `3.0.0.0`, `3.0.0.0-full`: Apache Tika Server 3.0.0.0 (Full)
- `3.0.0.0-BETA2`, `3.0.0.0-BETA2`: Apache Tika Server 3.0.0.0-BETA2 (Minimal)
- `3.0.0.0-BETA2`, `3.0.0.0-BETA2-full`: Apache Tika Server 3.0.0.0-BETA2 (Full)
- `2.9.2.1`, `2.9.2.1`: Apache Tika Server 2.9.2.1 (Minimal)
- `2.9.2.1`, `2.9.2.1-full`: Apache Tika Server 2.9.2.1 (Full)
- `2.9.2.0`, `2.9.2.0`: Apache Tika Server 2.9.2.0 (Minimal)
- `2.9.2.0`, `2.9.2.0-full`: Apache Tika Server 2.9.2.0 (Full)
- `2.9.1.0`, `2.9.1.0`: Apache Tika Server 2.9.1.0 (Minimal)
- `2.9.1.0`, `2.9.1.0-full`: Apache Tika Server 2.9.1.0 (Full)
- `2.9.0.0`, `2.9.0.0`: Apache Tika Server 2.9.0.0 (Minimal)
- `2.9.0.0`, `2.9.0.0-full`: Apache Tika Server 2.9.0.0 (Full)
- `2.8.0.0`, `2.8.0.0`: Apache Tika Server 2.8.0.0 (Minimal)
- `2.8.0.0`, `2.8.0.0-full`: Apache Tika Server 2.8.0.0 (Full)
- `2.7.0.1`, `2.7.0.1`: Apache Tika Server 2.7.0.1 (Minimal)
- `2.7.0.1`, `2.7.0.1-full`: Apache Tika Server 2.7.0.1 (Full)
- `2.7.0.0`, `2.7.0.0`: Apache Tika Server 2.7.0.0 (Minimal)
- `2.7.0.0`, `2.7.0.0-full`: Apache Tika Server 2.7.0.0 (Full)
- `2.6.0.1`: Apache Tika Server 2.6.0.1 (Minimal)
- `2.6.0.1-full`: Apache Tika Server 2.6.0.1 (Full)
- `2.6.0.0`: Apache Tika Server 2.6.0.0 (Minimal)
- `2.6.0.0-full`: Apache Tika Server 2.6.0.0 (Full)
- `2.5.0.2`: Apache Tika Server 2.5.0.2 (Minimal)
- `2.5.0.2-full`: Apache Tika Server 2.5.0.2 (Full)
- `2.5.0.1`: Apache Tika Server 2.5.0.1 (Minimal)
- `2.5.0.1-full`: Apache Tika Server 2.5.0.1 (Full)
- `2.5.0`: Apache Tika Server 2.5.0 (Minimal)
- `2.5.0-full`: Apache Tika Server 2.5.0 (Full)
- `2.4.1`: Apache Tika Server 2.4.1 (Minimal)
- `2.4.1-full`: Apache Tika Server 2.4.1 (Full)
- `2.4.0`: Apache Tika Server 2.4.0 (Minimal)
- `2.4.0-full`: Apache Tika Server 2.4.0 (Full)
- `2.3.0`: Apache Tika Server 2.3.0 (Minimal)
- `2.3.0-full`: Apache Tika Server 2.3.0 (Full)
- `2.2.1`: Apache Tika Server 2.2.1 (Minimal)
- `2.2.1-full`: Apache Tika Server 2.2.1 (Full)

Below are the most recent 1.x series tags. **Note** that as of 30 September 2022, the 1.x branch is no longer supported.

- `1.28.5`: Apache Tika Server 1.28.5 (Minimal)
- `1.28.5-full`: Apache Tika Server 1.28.5 (Full)
- `1.28.4`: Apache Tika Server 1.28.4 (Minimal)
- `1.28.4-full`: Apache Tika Server 1.28.4 (Full)
- `1.28.3`: Apache Tika Server 1.28.3 (Minimal)
- `1.28.3-full`: Apache Tika Server 1.28.3 (Full)
- `1.28.2`: Apache Tika Server 1.28.2 (Minimal)
- `1.28.2-full`: Apache Tika Server 1.28.2 (Full)
- `1.28.1`: Apache Tika Server 1.28.1 (Minimal)
- `1.28.1-full`: Apache Tika Server 1.28.1 (Full)

You can see a full set of tags for historical versions [here](https://hub.docker.com/r/apache/tika/tags?page=1&ordering=last_updated).

## 4.x Preview Notes

The `4.0.0-alpha-1` images are a preview of the upcoming Tika 4.x line and are
not tagged `latest`. Tag scheme is `<tika-version>` (rolling) plus
`<tika-version>-<N>` (immutable) — see Available Tags above. The legacy `.N`
suffix (`4.0.0-alpha-1.0`) is retained as a frozen pointer to the first build
but is no longer the active convention.

Tika 4.x changed the `tika-server-standard` packaging: the published jar is now
a thin top-level jar that resolves its dependencies from a sibling `lib/`
directory. The 4.x image therefore ships the unpacked `tika-server-standard-bin.zip`
distribution under `/opt/tika-server/` (containing `tika-server.jar`, `lib/`,
and `plugins/`) instead of a single fat jar.

The standard REST endpoints (`/tika`, `/rmeta`, `/unpack`, `/detect`, etc.)
work as in 3.x — they spool the request body to a temp file internally via
`TikaInputStream` and do not require any pipes plugin.

Pipes-mode endpoints (`/pipes`, `/async`) require pf4j plugins. The
`tika-pipes-file-system` plugin is **bundled** under
`/opt/tika-server/plugins/tika-pipes-file-system/` (it ships inside the
upstream `tika-server-standard-bin.zip`). Other pipes plugins
(`tika-pipes-http`, `tika-pipes-s3`, etc.) are not currently bundled in the
preview image; mount them into `/opt/tika-server/plugins/` if you need them.
Bundling additional common plugins is planned for `4.0.0-beta-1.0`.

## Supported Platforms

The Docker images are published as multi-platform images supporting the following architectures:

- `linux/amd64` - 64-bit x86 processors (Intel/AMD)
- `linux/arm64/v8` - 64-bit ARM processors (Apple Silicon, AWS Graviton, etc.)
- `linux/s390x` - IBM System z mainframes

NOTE: `linux/arm/v7` was published for 3.x but dropped starting with `4.0.0-alpha-1.0`.
If you need 32-bit ARM, pin to a 3.x tag. The drop was driven by a qemu/dpkg
emulation bug that broke font-package installation on the Ubuntu 26.04 base.

Docker will automatically pull the correct image for your platform when you use `docker pull` or `docker run`.

## Usage

### Default

You can pull down the version you would like using:

    docker pull apache/tika:<tag>

Then to run the container, execute the following command:

    docker run -d -p 127.0.0.1:9998:9998 apache/tika:<tag>

Where <tag> is the DockerHub tag corresponding to the Apache Tika Server version - e.g. 1.23, 1.22, 1.23-full, 1.22-full.

NOTE: The latest and latest-full tags are explicitly set to the latest released version when they are published.

NOTE: In the example above, we recommend binding the server to localhost because Docker alters iptables and may expose
your tika-server to the internet.  If you are confident that your tika-server is on an isolated network
you can simply run:

    docker run -d -p 9998:9998 apache/tika:<tag>

### Custom Config

From version 1.25 and 1.25-full of the image it is now easier to override the defaults and pass parameters to the running instance.

So for example if you wish to disable the OCR parser in the full image you could write a custom configuration:

```
cat <<EOT >> tika-config.json
{
  "parsers": [
    { "default-parser": {} },
    { "tesseract-ocr-parser": { "skipOcr": true } }
  ]
}
EOT
```
Then by mounting this custom configuration as a volume, you could pass the command line parameter to load it

    docker run -d -p 127.0.0.1:9998:9998 -v `pwd`/tika-config.json:/tika-config.json apache/tika:<tag>-full -c /tika-config.json

NOTE: Tika 4.x replaced the XML `tika-config.xml` format with JSON
`tika-config.json` (see TIKA-4544). The XML form above is what 2.x / 3.x
images expect; if you're pinned to those tags, keep using the XML.

You can see more configuration examples on the
[Tika website](https://tika.apache.org/) and in the canonical samples under
`tika-server/tika-server-core/src/test/resources/config-examples/` in the
source tree.

As of 2.5.0.2, if you'd like to add extra jars from your local `my-jars` directory to Tika's classpath, mount to `/tika-extras` like so:

    docker run -d -p 127.0.0.1:9998:9998 -v `pwd`/my-jars:/tika-extras apache/tika:2.5.0.2-full

You may want to do this to add optional components, such as the tika-eval metadata filter, or optional
dependencies such as jai-imageio-jpeg2000 (check license compatibility first!).

### Docker Compose Examples

There are a number of sample Docker Compose files included in the repos to allow you to test some different scenarios.

These files use docker-compose 3.x series and include:

* docker-compose-tika-vision.yml - Vision-Language Model parsing example (OpenAI-compatible / Claude / Gemini)
* docker-compose-tika-grobid.yml - Grobid REST parsing example
* docker-compose-tika-customocr.yml - Tesseract OCR example with custom configuration

The Docker Compose files and configurations (sourced from _sample-configs_ directory) all have comments in them so you can try different options, or use them as a base to create your own custom configuration.

**N.B.** You will want to create a environment variable (used in some bash scripts) matching the version of tika-docker you want to work with in the docker compositions e.g. `export TAG=1.26`. Similarly you should also consult `.env` which is used in the docker-compose `.yml` files.

You can install docker-compose from [here](https://docs.docker.com/compose/install/).

## Building

To build the image from scratch, simply invoke:

    docker build -t 'apache/tika' github.com/apache/tika-docker
   
You can then use the following command (using the name you allocated in the build command as part of -t option):

    docker run -d -p 127.0.0.1:9998:9998 apache/tika
    
## More Information

For more infomation on Apache Tika Server, go to the [Apache Tika Server documentation](https://cwiki.apache.org/confluence/display/TIKA/TikaServer).

For more information on Apache Tika, go to the official [Apache Tika](http://tika.apache.org) project website.

To meet up with others using Apache Tika, consider coming to one of the [Apache Tika Virtual Meetups](https://www.meetup.com/apache-tika-community/).

For more information on the Apache Software Foundation, go to the [Apache Software Foundation](http://apache.org) website.

For a full list of changes as of 2.5.0.1, visit [CHANGES.md](CHANGES.md).

For our current release process, visit [tika-docker Release Process](https://cwiki.apache.org/confluence/display/TIKA/Release+Process+for+tika-docker)

## Authors

Apache Tika Dev Team (dev@tika.apache.org)
   
## Contributors

There have been a range of [contributors](https://github.com/apache/tika-docker/graphs/contributors) on GitHub and via suggestions, including:

- [@grossws](https://github.com/grossws)
- [@arjunyel](https://github.com/arjunyel)
- [@mpdude](https://github.com/mpdude)
- [@laszlocsontosuw](https://github.com/laszlocsontosuw)
- [@tallisonapache](https://github.com/tballison)

## License

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
## Disclaimer

It is worth noting that whilst these Docker images download the binary JARs published by the Apache Tika Team on the Apache Software Foundation distribution sites, only the source release of an Apache Software Foundation project is an official release artefact. See [Release Distribution Policy](https://www.apache.org/dev/release-distribution.html) for more details.
