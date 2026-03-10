# Language Drop Decisions

This document records why specific languages were removed from the CharSoup
training pool and how closely related languages are handled at inference time.

**Important framing**: every decision here is a limitation of this model —
specifically of character n-gram features — not a statement about the languages
themselves. Languages that share short n-gram profiles are genuinely distinct
languages whose speakers can tell them apart immediately from broader context,
vocabulary, and grammar. A character n-gram model working on short text snippets
simply does not have access to that evidence. Where possible we use the gating
mechanism below to keep languages in the model rather than drop them entirely.

**Current model: 203 languages** (v7, `langdetect-v7-20260306.bin`).
**Short-text model: 122 languages** (v1, `langdetect-short-v1-20260310.bin`; see `short-text-language-decisions.md`).

History: v4 → v5 dropped 9 bot-corpus languages and 3 gate-dependent languages,
removed the length-gating mechanism, and added `yue`/`zho` confusable pair.
v5 → v7 added MADLAD-400 supplements and switched to ScriptAwareFeatureExtractor
(trigrams + suffixes + prefixes + word unigrams + char unigrams, 16k buckets).

---

## Excluded languages

All exclusions are enforced in `PrepareCorpus.EXCLUDED_LANGS`. The code
comments contain per-language rationale; this table summarises.

### Insufficient character-level separation in current model

These languages overlap so heavily in their short character n-gram profiles
that keeping all members produces severe mutual accuracy loss with no reliable
gain. The limitation is the model's feature set, not the languages.

| Code | Language | Reason |
|------|----------|--------|
| `bos` | Bosnian | Character n-gram overlap with `hrv` and `srp` is too high for reliable short-text separation. `hrv` and `srp` are retained; `bos` documents will typically match one of them. |
| `hbs` | Serbo-Croatian | Same character n-gram overlap problem across the whole South Slavic cluster; F1 never above 0.85. 77.6% Pass-2 retention — highest mislabel rate in pool. |
| `dzo` | Dzongkha | Tibetan script profile overlaps `bod` severely; neither can be returned reliably when both are in the model. |
| `bod` | Tibetan | Same issue as `dzo`; both excluded rather than producing unreliable results for either. |
| `koi` | Komi-Permyak | 22% n-gram overlap with `kpv` (Komi-Zyrian) at 500 chars — not resolvable with bigrams alone. Both are Komi varieties; `kpv` has higher F1 at every length and is retained. |
| `msi` | Sabah Malay | Short-text n-gram profile converges with `msa`/`ind` beyond what bigrams can separate. |
| `meo` | Meru | Same convergence profile as `msi` with Malay-family languages. |
| `que` | Quechua | Character n-gram separation from `aym` never reaches F1=0.85 at any text length. |
| `aym` | Aymara | Character n-gram separation from `que` never reaches F1=0.85 at any text length. |

### Accuracy too low / corpus too poor to be useful

Keeping these would add a label that misfires on most inputs, which is worse
than not covering the language at all.

| Code | Language | F1@500 | Reason |
|------|----------|--------|--------|
| `mad` | Madurese | 0.09 | Model accuracy near-random; character profile converges with Javanese/Indonesian at all lengths. |
| `anw` | Anaang | 0.33 | Too few clean training sentences for reliable detection at any length. |
| `glk` | Gilaki | — | Script profile converges with Persian and Mazanderani. |
| `bgp` | Eastern Balochi | — | Short-text n-gram profile converges with Arabic/Urdu/Persian. |
| `hif` | Fiji Hindi | 0.81 | Character-level convergence with standard Hindi at every length. |
| `gor` | Gorontalo | 0.80 | F1@50=0.50 — near-random at 50-char samples, no recovery at longer lengths. |
| `pcd` | Picard | 0.81 | Character n-gram profile converges with French at all lengths; no reliable recovery. |
| `sot` | Southern Sotho | — | Including `sot` collapsed Tswana (`tsn`) from 98% to 86% — net harm to both. |
| `crh` | Crimean Tatar | — | Latin-script orthography produces n-gram profiles that overlap heavily with Turkish; caused ~400 Turkish false positives. |
| `zza` | Zazaki | — | Training data contaminated with Turkish; caused ~400 Turkish false positives. |
| `knn` | Konkani | — | Devanagari script produces profiles that converge with Marathi. |
| `mkw` | Kituba | — | Profile converges with Kongo and Lingala. |
| `ang` | Old English | — | Character profile converges with modern English; 109 English misclassifications. |
| `als` | Tosk Albanian | — | Including `als` collapsed standard Albanian (`sqi`) from 99% to 51.6% — net harm to both. |
| `vec` | Venetian | — | Including `vec` collapsed Italian (`ita`) by 14.5 pp — net harm to both. |
| `mai` | Maithili | 0.90 | F1@50=0.49. Devanagari script profiles converge with Hindi/Nepali; 73.5% Pass-2 retention indicates substantial corpus contamination. |

### Training corpus unreliable

The language itself is well-defined and detectable in principle, but the
available training data does not represent it well enough to produce a
useful model.

| Code | Language | Reason |
|------|----------|--------|
| `sco` | Scots | The Scots Wikipedia was largely written by a non-Scots speaker modifying English articles; the training data does not reliably represent Scots. The resulting model was simultaneously the #1 false-positive source into English and the #2 recall drain for English @20 chars. |
| `ceb` | Cebuano | Wikipedia corpus is ~80% Lsjbot-generated municipality stubs. F1@500=0.999 on Wikipedia dev vs **13.9% on FLORES-200** — the model learned the template, not the language. MADLAD-400 data (Kudugunta et al., arXiv:2309.04662) was later tried in the short-text model but achieved only 26.9% FLORES @20 across all feature configs — still below useful threshold, with heavy `tgl` (Tagalog) confusion. |

### Bot-generated or templated Wikipedia corpus (dropped in v5)

These languages were identified by a systematic audit: (1) computing the gap
between Wikipedia F1@500 (in-domain) and FLORES F1 (out-of-domain) for all
overlapping languages, then (2) manually sampling 20–30 evenly-spaced
sentences from each language's pool file. All languages below had their
training data found to be overwhelmingly templated bot stubs. Reinclusion
requires a corpus of genuine prose.

| Code | Language | Corpus size | Finding |
|------|----------|-------------|---------|
| `arz` | Egyptian Arabic | — | Sentences follow person/place birth-stub template: "من مواليد يوم [date] سنة [year] فى [place]". Wikipedia F1@500=99.9%, FLORES F1=5.3% — same bot-overfit signature as `ceb`. |
| `bug` | Buginese | 8k sentences | ~95% French municipality stubs: "X iyanaritu séuwa komun ri déparetema Y ri Perancis." |
| `bpy` | Bishnupriya Manipuri | 18k sentences | Dominated by Brazilian municipality, US county, and Indian/Bangladeshi location stubs. Near-zero genuine prose. |
| `mlg` | Malagasy | 179k sentences | French commune stubs dominate: "Ny isam-ponin'i X dia Y mponina araka ny fanisana..." Model learned the template. |
| `nan-x-rom` | Min Nan Chinese (romanized) | 455k sentences | 6/8 random samples are geographic stubs (Romanian communes, Bolivian municipalities, German/Iranian villages). |
| `new` | Newari | 20k sentences | Indian village stubs dominate: "भारतया X राज्यया Y जनपदया Z तहसीलया छगु गां ख". |
| `lld` | Ladin | 143k sentences | All sampled sentences are stubs — mountain ranges, municipalities, towns, incomplete game articles. |
| `che` | Chechen | 456k sentences | 19/20 random samples are Russian, Turkish, Belarusian, and Mexican village/town stubs written in Chechen script. |
| `nav` | Navajo | 47k sentences | ~95% species distribution stubs: "éí [animal] dah yikahjí atah yisdzoh... Ndaʼałkaahí dóó ééʼdeetįįhii éí [scientific name] deiłníigo dayózhí". |

### Below F1=0.85 threshold

| Code | Language | F1@500 | Reason |
|------|----------|--------|--------|
| `zea` | Zeelandic | 0.827 | Produces 1.4% recall loss for Dutch (`nld`) on FLORES at full sentence length. `vls` is retained at F1@500=0.925. |

### Gate-dependent languages (dropped in v5)

The length-gating mechanism was removed from `CharSoupLanguageDetector`
in v5. Languages that required gating to avoid harming high-traffic parent
languages are dropped instead. Languages with only minor short-text bleed into
their parent (`vls`, `ext`, `rue`, `bjn`, `nap`) are **retained** — the bleed
at short lengths is acceptable and short-text variety detection can be
revisited in a future model.

| Code | Language | Reason for dropping |
|------|----------|---------------------|
| `hat` | Haitian Creole | Was the #1 false-positive source into English @20 chars without gating. Corpus is mixed (filmography stubs, place stubs, genuine prose), weakening the hat signal. Dropping rather than restoring the English bleed. |
| `nds-nl` | Low Saxon Dutch | Had the most complex dual-confusable behavior: permanently paired with `nds` (Low Saxon German) AND requiring a short-text gate against `nld` (Dutch). nds-nl text routes to `nds` via the existing confusable pair. |
| `map-bms` | Banyumasan | 5.7% bleed into `ind` and 3.5% into `jav` at **full length** — gating only addressed short text, the full-length bleed remained. |

---

## Paired languages (`confusables.txt`)

These are distinct, well-established languages that the model supports and
can separate at longer lengths. However, their short character n-gram profiles
overlap enough that a short-text coin-flip between them is not useful. The
model combines their scores and returns the higher one, reporting confidence
in the pair rather than a noisy single-language guess.

| Pair | Notes |
|------|-------|
| `ind` (Indonesian) / `msa` (Malay) | These are distinct national standard languages with a shared historical root and substantial written overlap. At 500 chars, ~9% of Indonesian documents score higher for Malay and vice versa — the character bigram space does not reliably separate them at any length. Both are fully supported; the model returns whichever scores higher. |
| `nds` (Low Saxon German) | Low Saxon German. `nds-nl` (Low Saxon Dutch) was previously paired here but was dropped in v5 (see gate-dependent drops above). |
| `xho` (Xhosa) / `zul` (Zulu) | Both Nguni Bantu languages. Character n-gram profiles overlap heavily; reliable separation requires broader lexical and morphological context. Both fully supported. |
| `bel` (Belarusian) / `be-x-old` (Belarusian Taraškievica) | Two written standards for Belarusian. Grouped to prevent cross-script contamination filtering during training. Both fully supported. |
| `yue` (Cantonese) / `zho` (Mandarin) | *(Added v5)* Both written in Han script. Manual sampling confirms the `yue` Wikipedia corpus is genuine Cantonese prose (distinctive particles and vocabulary), but the shared script means character n-gram profiles overlap heavily at the bigram level. FLORES F1 for `yue` is near zero because FLORES contains only Mandarin for that script — this is an evaluation artefact, not a model failure. Both fully supported; the model returns whichever scores higher on the input. |

---

## Reinstated languages (previously excluded from MADLAD model)

The switch to Wikipedia as the primary corpus resolved several MADLAD-specific
data quality issues. The following languages were re-evaluated and retained:

| Code | Language | Previous reason for exclusion | Resolution |
|------|----------|-------------------------------|------------|
| `war` | Waray | MADLAD: quality flags, elevated English overlap | Wikipedia corpus clean; 99.2% FLORES recall |
| `iku` | Inuktitut | MADLAD: corpus contaminated with English text | Wikipedia uses Canadian Syllabics script; script gate (`CANADIAN_ABORIGINAL`) ensures Latin-script input cannot trigger `iku` |
| `shn` | Shan | MADLAD: 23% of sentences flagged as English | Wikipedia uses Myanmar script; script gate added |
| `cor` | Cornish | MADLAD: corpus contained substantial English code-switching | Wikipedia corpus retested; retained |
| `glv` | Manx | MADLAD: corpus contaminated with English | Wikipedia corpus retested; retained |
| `gsw` | Alsatian | MADLAD: quality flags, German overlap | Wikipedia corpus retested; retained |
| `haw` | Hawaiian | MADLAD: insufficient clean data | Wikipedia corpus sufficient |
| `bjn` | Banjar | Wikipedia round 2: short-text overlap with `ind`/`msa` | Retained in v5 without gating; minor short-text bleed into `ind` accepted |
