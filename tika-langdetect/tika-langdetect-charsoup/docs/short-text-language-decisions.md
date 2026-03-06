# Short-Text Language Detection — Language Selection

This document records the language-inclusion policy for the **short-text
variant** of the CharSoup language detector. Where the full v5 model targets
broad coverage (~198 languages) optimised for ≥100-character inputs, this
model targets **reliable detection at 20–50 characters**.

---

## Design rationale

At 20 characters the v5 model achieves 77.98% macro-F1. The gap relative to
longer text is almost entirely explained by two phenomena:

1. **Cluster confusion.** Major European languages (English, French, German,
   Italian, Spanish, Dutch) are surrounded by many closely related regional
   varieties — Occitan, Corsican, Lombard, Walloon, Alsatian, Low Saxon, etc.
   At 20 chars the character bigram profiles of these varieties overlap heavily
   with the major language. The model splits probability mass between them,
   degrading the major language's score even though those varieties are the
   minority case in real documents.

2. **Low-entropy inputs.** Very short snippets can be genuine function-word
   sequences, partial tokens, or punctuation runs that carry almost no
   language-specific signal regardless of which languages are in the model.

The short-text model addresses (1) directly: by removing the low-traffic
regional varieties that cause the most confusion, the major languages reclaim
the probability mass that was being bled away. Phenomenon (2) is irreducible;
the model returns low confidence for those inputs regardless.

**Key consequence:** languages that look weak in the v5 @20 evaluation
(English 46.7%, French 64.2%, German 67.7%, Spanish 55.8%, Italian 47.7%,
Dutch 42.3%) are expected to recover substantially in a model trained only
on the languages listed here, because their primary confusers are absent.

---

## Inclusion criteria

A language is included if it satisfies **any** of the following:

1. **Unique or near-unique script.** Languages whose Unicode script is shared
   by few or no other included languages achieve ~100% F1 at 20 chars
   regardless of model size; there is no reason to exclude them.

2. **F1@20 ≥ 75% in the full v5 model.** If the language already detects well
   at 20 chars in the presence of 198 competitors, it will only improve in a
   smaller set.

3. **Major practical language.** Languages that appear frequently in documents
   Tika processes (large web/enterprise corpus) are included even if their
   current v5 @20 F1 is below 75%, with the expectation that removing their
   confusers restores performance. This applies primarily to the major European
   languages and to Indonesian/Malay.

A language is **excluded** if:

- It is a regional variety whose character n-gram profile closely mirrors a
  major included language at short lengths, causing the major language to lose
  probability mass (see cluster exclusions below).
- It is a classical or constructed language (Sanskrit, Esperanto, Latin).
- It has fewer than ~10,000 clean Wikipedia sentences or its corpus is known
  to be dominated by templated bot stubs (see v5 `language-drop-decisions.md`
  for the full bot-corpus audit).

---

## Included languages (~120)

### Non-Latin-script languages

These are reliable at any text length due to script uniqueness. All are
included unconditionally.

| Script | Code | Language | v5 F1@20 |
|--------|------|----------|----------|
| Arabic | `ara` | Arabic | ~100% |
| Arabic | `azb` | South Azerbaijani | 79.2% |
| Arabic | `ckb` | Central Kurdish (Sorani) | ~100% |
| Arabic | `fas` | Persian | ~100% |
| Arabic | `pus` | Pashto | ~100% |
| Arabic | `snd` | Sindhi | 91.9% |
| Arabic | `uig` | Uyghur | ~100% |
| Arabic | `urd` | Urdu | 84.7% |
| Bengali | `asm` | Assamese | 94.5% |
| Bengali | `ben` | Bengali | 94.7% |
| Han/CJK | `yue` | Cantonese | 5.4% (confusable group with `zho`) |
| Han/CJK | `zho` | Chinese (Mandarin) | 77.2% |
| Cyrillic | `bak` | Bashkir | 86.0% |
| Cyrillic | `bel` | Belarusian | 82.4% |
| Cyrillic | `bul` | Bulgarian | 77.8% |
| Cyrillic | `chv` | Chuvash | — |
| Cyrillic | `kaz` | Kazakh | 88.6% |
| Cyrillic | `kir` | Kyrgyz | 85.3% |
| Cyrillic | `mkd` | Macedonian | 78.0% |
| Cyrillic | `mon` | Mongolian | — |
| Cyrillic | `myv` | Erzya | — |
| Cyrillic | `rus` | Russian | 73.1% |
| Cyrillic | `sah` | Yakut (Sakha) | — |
| Cyrillic | `srp` | Serbian | 82.1% |
| Cyrillic | `tat` | Tatar | 84.1% |
| Cyrillic | `tgk` | Tajik | 91.4% |
| Cyrillic | `ukr` | Ukrainian | 81.5% |
| Devanagari | `hin` | Hindi | 72.0% |
| Devanagari | `mar` | Marathi | 75.4% |
| Devanagari | `nep` | Nepali | — |
| Ethiopic | `amh` | Amharic | 100% |
| Georgian | `kat` | Georgian | 100% |
| Greek | `ell` | Greek | 100% |
| Gujarati | `guj` | Gujarati | 100% |
| Gurmukhi | `pan` | Punjabi | 100% |
| Hangul | `kor` | Korean | 100% |
| Hebrew | `heb` | Hebrew | 92.6% |
| Hebrew | `ydd` | Yiddish | 94.0% |
| Japanese | `jpn` | Japanese | ~100% |
| Kannada | `kan` | Kannada | 100% |
| Khmer | `khm` | Khmer | 100% |
| Lao | `lao` | Lao | 100% |
| Malayalam | `mal` | Malayalam | 100% |
| Myanmar | `mya` | Burmese | ~100% |
| OL Chiki | `sat` | Santali | 100% |
| Sinhala | `sin` | Sinhala | 100% |
| Tamil | `tam` | Tamil | 100% |
| Telugu | `tel` | Telugu | 100% |
| Thai | `tha` | Thai | 100% |

**Non-Latin subtotal: 49 languages**

Note: `yue` (Cantonese) has near-zero FLORES F1 because the FLORES test set
contains only Mandarin for Han script — this is an evaluation artefact, not a
model failure. `yue` and `zho` form a confusable group; the model returns
whichever scores higher.

`rus` (73.1%) and `hin` (72.0%) are below the 75% threshold but are included
because their Cyrillic/Devanagari scripts make them far easier to detect at
short text than the Latin cluster numbers suggest — their confusion in v5 is
with other Cyrillic/Devanagari languages (which are all retained here, keeping
the within-family competition intact).

---

### Latin-script languages

#### Major European (34)

All are included unconditionally as major practical languages. Those currently
below 75% F1@20 in v5 are expected to recover once their primary confusers
(listed in the exclusions section) are removed from the model.

| Code | Language | v5 F1@20 | Notes |
|------|----------|----------|-------|
| `afr` | Afrikaans | 73.2% | |
| `cat` | Catalan | 66.3% | Recovers when `oci`, `glg`, `ast` removed |
| `ces` | Czech | 77.9% | |
| `cym` | Welsh | 100% | Distinctive digraph orthography |
| `dan` | Danish | 57.6% | Recovers when Scandinavian cluster thins |
| `deu` | German | 67.7% | Recovers when `gsw`, `bar`, `nds`, `ltz` removed |
| `eng` | English | 46.7% | Recovers when many Latin-script confusers removed |
| `est` | Estonian | 78.7% | |
| `eus` | Basque | 78.3% | Language isolate; distinctive orthography |
| `fao` | Faroese | 75.4% | |
| `fin` | Finnish | 72.6% | Agglutinative; distinctive character profile |
| `fra` | French | 64.2% | Recovers when `oci`, `wln`, `pms`, `bre` removed |
| `gla` | Scottish Gaelic | 88.0% | |
| `gle` | Irish | 90.6% | |
| `glg` | Galician | 61.2% | Included; major co-official language of Spain |
| `hrv` | Croatian | 72.8% | |
| `hun` | Hungarian | 83.7% | |
| `isl` | Icelandic | 81.5% | |
| `ita` | Italian | 47.7% | Recovers when `cos`, `lmo`, `nap`, `scn`, `srd`, `fur`, `lij` removed |
| `lav` | Latvian | — | EU official language; no FLORES eval data |
| `lit` | Lithuanian | 83.6% | |
| `ltz` | Luxembourgish | 73.3% | Included; official language of Luxembourg |
| `mlt` | Maltese | 72.6% | Semitic vocabulary in Latin orthography |
| `nld` | Dutch | 42.3% | Recovers when `vls`, `lim`, `nds`, `afk` reduce competition |
| `nno` | Norwegian Nynorsk | 60.5% | Included alongside `nob` |
| `nob` | Norwegian Bokmål | 49.7% | |
| `pol` | Polish | 73.4% | |
| `por` | Portuguese | 67.7% | |
| `ron` | Romanian | 83.3% | |
| `slk` | Slovak | 79.1% | |
| `slv` | Slovenian | 74.2% | |
| `spa` | Spanish | 55.8% | Recovers when `ast`, `oci`, `glg` reduce competition |
| `swe` | Swedish | 56.5% | |
| `tur` | Turkish | 72.2% | |

#### African Latin-script (16)

| Code | Language | v5 F1@20 |
|------|----------|----------|
| `hau` | Hausa | 84.2% |
| `ibo` | Igbo | 89.2% |
| `kab` | Kabyle | 80.3% |
| `kin` | Kinyarwanda | 80.6% |
| `lin` | Lingala | — |
| `lug` | Luganda | 84.1% |
| `nso` | Northern Sotho | 76.8% |
| `orm` | Oromo | — |
| `sna` | Shona | 80.1% |
| `som` | Somali | 85.0% |
| `swh` | Swahili | 77.6% |
| `tum` | Tumbuka | 79.5% |
| `twi` | Twi | 85.1% |
| `wol` | Wolof | — |
| `yor` | Yoruba | 83.9% |
| `zul` | Zulu | 83.7% |

#### Asian / Pacific / Americas (13)

| Code | Language | v5 F1@20 |
|------|----------|----------|
| `ace` | Acehnese | 75.8% |
| `bjn` | Banjar | 54.0% |
| `grn` | Guaraní | 81.1% |
| `ilo` | Ilokano | 79.9% |
| `ind` | Indonesian | 35.3% (confusable group with `msa`) |
| `jav` | Javanese | 58.5% |
| `lus` | Mizo | 80.9% |
| `min` | Minangkabau | 61.0% |
| `msa` | Malay | — (confusable group with `ind`) |
| `sun` | Sundanese | 62.1% |
| `tgl` | Tagalog | 81.2% |
| `tuk` | Turkmen | 79.8% |
| `vie` | Vietnamese | 93.6% |

#### Other Latin-script (5)

| Code | Language | v5 F1@20 | Notes |
|------|----------|----------|-------|
| `aze` | Azerbaijani (North) | — | Latin-script; distinct from `azb` (Arabic-script South Azerbaijani) |
| `ban` | Balinese | — | |
| `pap` | Papiamento | 69.1% | |
| `uzb` | Uzbek | — | |
| `war` | Waray | 75.5% | |

**Latin subtotal: 68 languages**

**Grand total: 117 languages**

---

## Excluded languages (from v5 model)

These languages appear in the v5 198-language model but are omitted from the
short-text model. In each case the exclusion is expected to improve detection
of a closely related majority language.

### Gallo-Romance cluster (harming French and Italian)

Removing these languages is the single largest lever for improving French and
Italian detection at short text. Their character n-gram profiles at 20–50
chars are nearly indistinguishable from their parent languages.

| Code | Language | Primary harm |
|------|----------|-------------|
| `oci` | Occitan | French, Catalan, Spanish |
| `cos` | Corsican | Italian |
| `lmo` | Lombard | Italian |
| `lij` | Ligurian | Italian |
| `fur` | Friulian | Italian |
| `scn` | Sicilian | Italian |
| `srd` | Sardinian | Italian |
| `wln` | Walloon | French |
| `pms` | Piedmontese | Italian, French |
| `nap` | Neapolitan | Italian |

### Ibero-Romance cluster (harming Spanish and Portuguese)

| Code | Language | Primary harm |
|------|----------|-------------|
| `ast` | Asturian | Spanish, Portuguese |
| `ext` | Extremaduran | Spanish |
| `arg` | Aragonese | Spanish |

### Germanic cluster (harming German, Dutch, and English)

| Code | Language | Primary harm |
|------|----------|-------------|
| `nds` | Low Saxon | German, Dutch, English |
| `bar` | Bavarian | German |
| `gsw` | Alsatian | German |
| `vls` | West Flemish | Dutch |
| `lim` | Limburgish | Dutch |
| `fry` | Western Frisian | Dutch, English |
| `nds-nl` | Low Saxon Dutch | Dutch (also excluded from v5) |
| `bre` | Breton | French |

Note: `ltz` (Luxembourgish) is **retained** — it is an official national
language and distinct enough in practice to be worth the minor German bleed.

### Slavic cluster (high mutual confusion at 20 chars)

The South Slavic and West Slavic languages are already known to be mutually
confusable at short text. The short-text model retains only the major national
standard languages.

| Code | Language | Primary harm |
|------|----------|-------------|
| `hsb` | Upper Sorbian | Czech, Polish |
| `dsb` | Lower Sorbian | Czech, Polish |
| `rue` | Rusyn | Ukrainian, Slovak |
| `szl` | Silesian | Polish |
| `csb` | Kashubian | Polish |
| `sgs` | Samogitian | Lithuanian |

### Classical and constructed languages

| Code | Language | Reason |
|------|----------|--------|
| `san` | Sanskrit | Classical; Devanagari profile overlaps hin/mar/nep |
| `epo` | Esperanto | Constructed; Latin profile overlaps many Romance languages |
| `lat` | Latin | Classical; Latin profile overlaps Romance |
| `grc` | Ancient Greek | Classical |
| `tlh` | Klingon | Constructed |

### Other excluded (corpus quality or low practical value)

| Code | Language | Reason |
|------|----------|--------|
| `glv` | Manx | ~2,000 speakers; causes English confusion |
| `cor` | Cornish | Very small speaker base; causes English/Welsh confusion |
| `haw` | Hawaiian | Small corpus; Latin profile at short text |
| `lzh` | Classical Chinese | Classical; HAN script same as `zho` |

---

## Notes on confusable pairs retained

The following confusable-group pairs from v5 are carried over unchanged into
the short-text model. The model returns whichever member scores higher.

| Pair | Notes |
|------|-------|
| `ind` / `msa` | Indonesian / Malay. Near-identical written forms; both important for SE Asian document processing. |
| `yue` / `zho` | Cantonese / Mandarin. Han script shared; model returns higher-scoring member. |
| `xho` / `zul` | Xhosa / Zulu. Both included; confusable group unchanged. |

---

## Post-training checklist

After training on this language set, verify:

1. **English F1@20 ≥ 85%.** If not, audit remaining confusers (check `ltz`,
   `nds` if somehow still present, and `bre`).
2. **French F1@20 ≥ 85%.**
3. **Italian F1@20 ≥ 85%.**
4. **German F1@20 ≥ 85%.** Should recover strongly with `gsw`/`bar`/`nds`
   removed.
5. **Spanish F1@20 ≥ 85%.** Should recover with `ast`/`ext`/`arg` removed.
6. **Dutch F1@20 ≥ 85%.** Should recover with `vls`/`lim`/`fry` removed.
7. **Indonesian (`ind`) F1@20.** Confusable with `msa` by design; group
   accuracy is the right metric, not per-language F1.
