<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Supported Languages

Languages supported by the CharSoup language detector, extracted from the
model binaries. The model binary is the source of truth; this list is for
human reference.

To query programmatically:

```java
CharSoupLanguageDetector.getSupportedLanguages(Strategy.STANDARD)  // 203 languages
CharSoupLanguageDetector.getSupportedLanguages(Strategy.SHORT_TEXT) // 122 languages
```

- **Standard model**: `langdetect-v7-20260306.bin` (3.2 MB, 16 384 buckets, flags 0x075)
- **Short-text model**: `langdetect-short-v1-20260310.bin` (3.8 MB, 32 768 buckets, flags 0x0a1) — optimized for 20–50 char inputs; see `short-text-language-decisions.md` for inclusion/exclusion rationale.

| Code | Std | Short |
|------|:---:|:-----:|
| `ace` | x |  |
| `afr` | x | x |
| `aka` | x | x |
| `alt` | x |  |
| `amh` | x | x |
| `ami` | x |  |
| `ara` | x | x |
| `arg` | x |  |
| `asm` | x | x |
| `ava` | x |  |
| `avk` | x |  |
| `azb` | x | x |
| `aze` | x | x |
| `bak` | x | x |
| `ban` | x |  |
| `bar` | x |  |
| `bcl` | x |  |
| `be-x-old` | x |  |
| `bel` | x | x |
| `ben` | x | x |
| `bjn` | x |  |
| `bre` | x |  |
| `bul` | x | x |
| `bxr` | x |  |
| `cat` | x | x |
| `cdo-x-rom` | x | x |
| `ceb` | x |  |
| `ces` | x | x |
| `che` | x | x |
| `chv` | x | x |
| `ckb` | x | x |
| `cnh` | x |  |
| `cor` | x |  |
| `cos` | x |  |
| `csb` | x |  |
| `cym` | x | x |
| `dag` | x | x |
| `dan` | x | x |
| `deu` | x | x |
| `diq` | x |  |
| `div` | x | x |
| `dsb` | x |  |
| `ell` | x | x |
| `eng` | x | x |
| `epo` | x |  |
| `est` | x | x |
| `eus` | x | x |
| `ewe` | x | x |
| `ext` | x |  |
| `fao` | x |  |
| `fas` | x | x |
| `fin` | x | x |
| `fra` | x | x |
| `frr` | x |  |
| `fry` | x | x |
| `gla` | x |  |
| `gle` | x | x |
| `glg` | x |  |
| `glv` | x |  |
| `gom` | x |  |
| `grn` | x |  |
| `gsw` | x |  |
| `guj` | x | x |
| `hak-x-rom` | x |  |
| `hau` | x | x |
| `heb` | x | x |
| `hil` | x | x |
| `hin` | x | x |
| `hrv` | x | x |
| `hsb` | x |  |
| `hun` | x | x |
| `hye` | x | x |
| `hyw` | x | x |
| `ibo` | x | x |
| `ido` | x |  |
| `ile` | x |  |
| `ilo` | x |  |
| `ina` | x |  |
| `ind` | x | x |
| `isl` | x | x |
| `ita` | x | x |
| `jav` | x | x |
| `jbo` | x |  |
| `jpn` | x | x |
| `kaa` | x |  |
| `kab` | x |  |
| `kan` | x | x |
| `kat` | x | x |
| `kaz` | x | x |
| `kha` | x | x |
| `khm` | x | x |
| `kin` | x | x |
| `kir` | x | x |
| `kor` | x | x |
| `kpv` | x |  |
| `ksh` | x |  |
| `kur` | x | x |
| `lao` | x | x |
| `lat` | x |  |
| `lav` | x | x |
| `lez` | x |  |
| `lfn` | x |  |
| `lim` | x |  |
| `lit` | x | x |
| `ltz` | x |  |
| `lug` | x | x |
| `lus` | x | x |
| `mal` | x | x |
| `mar` | x | x |
| `mhr` | x |  |
| `min` | x |  |
| `mkd` | x | x |
| `mlg` | x | x |
| `mlt` | x | x |
| `mon` | x | x |
| `mrj` | x |  |
| `msa` | x |  |
| `mwl` | x |  |
| `mya` | x | x |
| `myv` | x |  |
| `mzn` | x |  |
| `nds` | x |  |
| `nep` | x | x |
| `nld` | x | x |
| `nno` | x |  |
| `nob` | x | x |
| `nqo` | x | x |
| `nso` | x |  |
| `nya` | x | x |
| `olo` | x |  |
| `ori` | x | x |
| `orm` | x | x |
| `oss` | x | x |
| `pam` | x |  |
| `pan` | x | x |
| `pap` | x |  |
| `pfl` | x |  |
| `pnb` | x |  |
| `pol` | x | x |
| `por` | x | x |
| `pus` | x | x |
| `roh` | x |  |
| `ron` | x | x |
| `rue` | x |  |
| `rus` | x | x |
| `sah` | x | x |
| `san` | x |  |
| `sat` | x | x |
| `sgs` | x |  |
| `sin` | x | x |
| `skr` | x |  |
| `slk` | x | x |
| `slv` | x | x |
| `sme` | x |  |
| `smn` | x |  |
| `smo` | x | x |
| `sna` | x | x |
| `snd` | x | x |
| `som` | x | x |
| `spa` | x | x |
| `sqi` | x | x |
| `srp` | x | x |
| `stq` | x |  |
| `sun` | x | x |
| `swe` | x | x |
| `swh` | x | x |
| `szl` | x |  |
| `szy` | x |  |
| `tam` | x | x |
| `tat` | x | x |
| `tay` | x | x |
| `tel` | x | x |
| `tet` | x | x |
| `tgk` | x | x |
| `tgl` | x | x |
| `tha` | x | x |
| `tir` | x | x |
| `trv` | x | x |
| `tsn` | x | x |
| `tso` | x | x |
| `tuk` | x | x |
| `tum` | x |  |
| `tur` | x | x |
| `tyv` | x |  |
| `udm` | x | x |
| `uig` | x | x |
| `ukr` | x | x |
| `urd` | x | x |
| `uzb` | x | x |
| `vep` | x |  |
| `vie` | x | x |
| `vls` | x |  |
| `vol` | x |  |
| `vro` | x |  |
| `war` | x |  |
| `wln` | x |  |
| `xho` | x | x |
| `xmf` | x |  |
| `ydd` | x | x |
| `yor` | x | x |
| `yue` | x |  |
| `zho` | x | x |
| `zul` | x | x |

**Totals**: 203 standard, 122 short-text (all short-text languages are a subset of standard).
