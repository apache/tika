# Changes

Tag convention:
* 2.5.0.1 through 4.0.0-alpha-1.0 used `<tika-version>.<docker-build-number>`
  (e.g. `3.3.0.0`, `4.0.0-alpha-1.0`). Each rebuild bumped the last digit.
* Starting with **4.0.0-alpha-1 (rebuild 1)**, we publish three tags per image:
  - `<tika-version>` — rolling, moves on each rebuild
  - `<tika-version>-<N>` — immutable, never reassigned (`N=1,2,3,...`)
  - `latest` — rolling, newest stable only (prereleases never displace it)

The legacy 3.x patch flow in the external `apache/tika-docker` repo still uses
the `.N` convention until 4.0.0 GA.

* 4.0.0-alpha-1 (11 May 2026, rebuild 1)
  * Tag scheme changed to `<tika-version>` + `<tika-version>-<N>` + `latest`.
  * Migrated build out of the external `apache/tika-docker` repo into
    `tika-server/docker-build/` in `apache/tika`.
  * Switched server packaging to the unpacked `tika-server-standard-bin.zip`
    (`/opt/tika-server/`). Bundles the `tika-pipes-file-system` plugin from
    the upstream bin.zip. Pipes-mode endpoints (`/pipes`, `/async`) with
    other fetchers/emitters need plugins mounted into
    `/opt/tika-server/plugins/`.
  * Upgraded base to Ubuntu 26.04 (resolute) and JRE to OpenJDK 25.
  * Dropped `linux/arm/v7` from the published platforms. 32-bit ARM emulated
    builds on resolute hit a qemu chown-overflow in `update-notifier-common`'s
    postinst (pulled in by `ttf-mscorefonts-installer`). `linux/arm64/v8`
    covers modern ARM.

* 4.0.0-alpha-1.0 (9 May 2026) — frozen legacy tag
  * First 4.0.0-alpha-1 release using the old `.N` convention. Retagged
    afterward so `4.0.0-alpha-1` (no `.0`) points at the same digest.

* 3.3.0.0 (23 Mar 2026)
  * First 3.3.0 release
  
* 3.2.3.0 (15 Sep 2025)
  * First 3.2.3 release

* 3.2.2.0 (8 Aug 2025)
  * First 3.2.2 release

* 3.2.1.0 (9 Jul 2025)
  * First 3.2.1 release

* 3.2.0.0 (2 Jun 2025)
  * First 3.2.0 release
  * Update base to plucky
  * Add Japanese language pack for tesseract
  * Add ImageMagick
  
* 3.1.0.0 (31 Jan 2025)
  * First 3.1.0 release
  * Update base to oracular

* 3.0.0.0 (21 Oct 2024)
  * First 3.x stable release
  * Bump jre to 21

* 2.9.2.1 (21 May 2024)
  * Updated to noble
  * First multi-arch release

* 2.9.2.0 (10 October 2023)
  * Initial release for Tika 2.9.2

* 2.9.1.0 (10 October 2023)
  * Initial release for Tika 2.9.1

* 2.9.0.0 (28 August 2023)
  * Initial release for Tika 2.9.0

* 2.8.0.0 (15 May 2023)
  * Initial release for Tika 2.8.0


* 2.7.0.1 (27 March 2023)
  * More efficient build process and final image size via @stumpylog on [pr#17](https://github.com/apache/tika-docker/pull).

* 2.7.0.0 (6 Feb 2023)
  * Initial release for Tika 2.7.0

* 2.6.0.1 (10 November 2022)
  * Update operating system against OpenSSL CVE (TIKA-3926).

* 2.6.0.0 (7 November 2022)
  * Initial release for Tika 2.6.0

* 2.5.0.2 (31 October 2022)
  * Fixed root-user regression caused by differences in Docker behavior based on the build system's OS (TIKA-3912)
  * Added tika-extras/ directory to pick up extra jars via mounted drive or for those using our image as a base image (TIKA-3907)
* 
* 2.5.0.1 (27 October 2022)
  * Update to latest jammy to avoid recent CVEs (TIKA-3906)