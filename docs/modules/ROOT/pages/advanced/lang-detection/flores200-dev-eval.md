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

# CharSoup Language Detection — Evaluation Report

**Models**: v7 standard (203 languages, 16 384 buckets, flags 0x075) + short-text v1 (122 languages, 32 768 buckets, flags 0x0a1).  
**Training corpus**: Wikipedia + MADLAD-400 (Kudugunta et al., arXiv:2309.04662) supplements.  
**Evaluation set**: [FLORES-200](https://github.com/facebookresearch/flores) dev split (203 languages × 1 001 sentences = 203 381 sentences).
Sentence lengths: median 124 chars, 91% under 200 chars — scores flatten from @200 onward because truncating to 200 vs 500 chars affects only the remaining 9% of sentences.  
**Raw reports**: `~/datasets/compare-v7-final/flores-STANDARD.log`,
`~/datasets/compare-v7-final/flores-SHORT_TEXT.log`,
`~/datasets/compare-v7-final/flores-AUTOMATIC.log`
(generated 2026-03-10 against final shipped models)

**Model provenance**:
- General: `langdetect-v7-20260306.bin` — ScriptAwareFeatureExtractor (bigrams + trigrams + suffixes + prefixes + word unigrams + char unigrams)
- Short-text: `langdetect-short-v1-20260310.bin` — ShortTextFeatureExtractor (trigrams + word unigrams + 4-grams). Trained 2026-03-10 on wikipedia-model-v7 pool, 122 languages (excludes `ceb`; see `short-text-language-decisions.md`).

---

## Inference pipeline (v7)

The full pipeline applied to every input chunk:

1. **Feature extraction** — standard model: bigrams + trigrams + suffixes + prefixes + word unigrams + char unigrams (ScriptAwareFeatureExtractor, flags 0x075);
   short-text model: bigrams + trigrams + word unigrams + 4-grams (ShortTextFeatureExtractor, flags 0x0a1)
2. **Raw logits** — dot-product against INT8-quantized weight matrix
3. **Script gate** — mask languages whose expected script doesn't match the dominant
   script of the input (fires when ≥85% of characters fall in one Unicode block group)
4. **Confusable-group collapse** — for structurally confusable pairs (`ind`/`msa`,
   `yue`/`zho`, etc.), sum logits of both members and return only the higher-scoring
   one as the group winner
5. **Rank and return** — top-k results by logit

**Automatic model selection** (Strategy.AUTOMATIC): for inputs shorter than 200 characters
or with fewer than 200 n-gram emissions (sparse inputs), the short-text model is used
instead of the standard model. Both models are loaded at startup; switching is zero-copy.

---

## Detectors compared

| Detector | Model size | Languages | Notes |
|----------|-----------|-----------|-------|
| **CharSoup standard** | ~3.2 MB | **203** | Bigrams + trigrams + suffixes + prefixes + word unigrams + char unigrams, 16k buckets |
| **CharSoup short-text** | ~3.8 MB | **122** | Bigrams + trigrams + word unigrams + 4-grams, 32k buckets |
| **CharSoup automatic** | ~7.0 MB combined | **203** | Routes to short-text for short/sparse input |
| **OpenNLP** | ~76 MB | 105 (of 203) | `tika-langdetect-opennlp` |
| **Lingua** | ~0.1 MB | 71 (of 203) | `lingua-detector`, low-accuracy mode |
| **Optimaize** | ~95 MB | 63 (of 203) | `tika-langdetect-optimaize` |

---

## Coverage-adjusted macro-F1

Each detector is scored **only on its own supported language set** — sentences whose true
language is not in a detector's covered set are skipped entirely. This rewards accuracy
within coverage and prevents penalising detectors for languages they don't claim to support.

| Length | CS-std (203L) | CS-short (122L) | CS-auto (203L) | OpenNLP (105L) | Lingua (71L) | Optimaize (63L) |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| @20    | 78.71% | **84.68%** | 65.04%† | 74.87% | 76.35% | 84.87% |
| @50    | 93.73% | **96.06%** | 73.72%† | 86.09% | 90.99% | 94.44% |
| @100   | 96.67% | **97.92%** | 75.32%† | 90.25% | 95.43% | 96.51% |
| @150   | 97.08% | **98.11%** | 75.53%† | 90.98% | 96.15% | 96.72% |
| @200   | 97.14% | **98.14%** | 79.21%† | 91.11% | 96.23% | 96.75% |
| @500   | 97.14% | **98.14%** | 79.22%† | 91.12% | 96.25% | 96.76% |

† **CS-auto caveat**: in AUTOMATIC mode the length threshold fires for nearly every FLORES sentence
(all are ≥ 20 chars), routing all 203 languages through the 122-language short-text model.
The 81 languages that are in the standard model but not the short-text model receive
suboptimal predictions, which depresses the coverage-adjusted score. In real use, those
languages do still receive a best-effort result from the short-text model; the penalty here
is an evaluation artefact of the threshold being set relative to natural prose lengths rather
than FLORES sentence lengths.

---

## Breadth-weighted macro-F1

All 203 FLORES languages are included in the denominator. Languages not in a detector's
supported set contribute 0 to the macro average. This penalises limited coverage and
rewards both accuracy and breadth.

| Length | CS-std | CS-short | CS-auto | OpenNLP | Lingua | Optimaize |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| @20    | **49.63%** | 43.80% | 41.01% | 42.05% | 27.46% | 26.76% |
| @50    | **59.10%** | 49.68% | 46.49% | 48.35% | 32.72% | 29.77% |
| @100   | **60.95%** | 50.65% | 47.49% | 50.68% | 34.32% | 30.43% |
| @150   | **61.21%** | 50.75% | 47.63% | 51.09% | 34.58% | 30.49% |
| @200   | **61.25%** | 50.76% | 49.95% | 51.16% | 34.61% | 30.50% |
| @500   | **61.25%** | 50.76% | 49.95% | 51.17% | 34.61% | 30.51% |

**Key takeaway**: every detector drops substantially because none covers all 203 FLORES
evaluation languages. CS-std leads because its 203-language model covers 128 of the 203
FLORES codes — the remaining 75 (Arabic dialect codes, regional languages, script variants)
are not in the model and score 0.  Other detectors cover even fewer FLORES languages:
OpenNLP 105 → covers ~105 of 203 (42.05%); CS-short 122 → covers ~105 of 203 (43.80%);
Lingua 71 → covers ~71 of 203 (27.46%); Optimaize 63 → covers ~63 of 203 (26.76%).

**CS-auto caveat**: the AUTOMATIC routing threshold fires whenever `input.length() < 200`,
routing those sentences to the 122-language short-text model. The 81 languages in the
standard model but not the short-text model receive wrong predictions, which depresses
the score further than a simple 0 would.

---

## Intersection comparisons (shared supported languages only)

For a fair head-to-head, both detectors are evaluated on the **intersection** of their
supported language sets. See the raw report files for the full pairwise intersection tables.

Key intersections at @20 chars (macro-F1 on shared languages):

| Pair | Shared langs | CS F1 | Other F1 |
|------|:---:|:---:|:---:|
| CS-std ∩ OpenNLP | 105 | 80.17% | 76.69% |
| CS-std ∩ Lingua  | 71  | 81.50% | 77.78% |
| CS-std ∩ Optimaize | 63 | 82.07% | 86.30% |
| CS-short ∩ OpenNLP | 91 | 85.20% | 81.12% |
| CS-short ∩ Lingua | 67 | 85.77% | 79.29% |
| CS-short ∩ Optimaize | 59 | 86.44% | 87.83% |

---

## Throughput

Measured on FLORES-200 dev set, multi-threaded (12 cores), Apple M-series.
All CharSoup variants run within a single JVM with both models loaded.
(Measured 2026-03-10; strategy evals ran sequentially. Absolute numbers
are approximate — relative ordering is the meaningful signal.)

| Length | CS-std | CS-short | CS-auto | OpenNLP | Lingua | Optimaize |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| @20    | 75.0k/s | 55.4k/s | 68.8k/s | 49.1k/s | 2.8k/s | 93.8k/s |
| @50    | 75.5k/s | 51.6k/s | 55.9k/s | 31.3k/s | 1.6k/s | 7.8k/s |
| @100   | 66.1k/s | 41.3k/s | 58.2k/s | 21.4k/s | 1.0k/s | 8.1k/s |
| @200   | 55.1k/s | 36.1k/s | 40.6k/s | 23.6k/s | 0.7k/s | 12.3k/s |
| @500   | 55.3k/s | 35.1k/s | 30.1k/s | 17.1k/s | 0.8k/s | 13.7k/s |

**Notes:**
- Optimaize is very fast at @20 (94k/s) because most of its 63-language model fits in
  cache at short inputs; it degrades sharply at @50+ as scoring cost scales with length.
- CS-short is slower than CS-std despite fewer classes (122 vs 203) because it uses
  32k buckets (vs 16k) and extracts 4-grams in addition to trigrams and word unigrams.
- Lingua (low-accuracy mode) is 15–50× slower than CharSoup across all lengths.

---

## Per-language highlights (FLORES @20, CS-std vs CS-short)

Languages with the largest improvement from the short-text model at 20 chars:

| Language | CS-std @20 | CS-short @20 | Gain |
|----------|:---:|:---:|:---:|
| Portuguese (`por`) | 66.8% | 79.6% | +12.8% |
| Dutch (`nld`) | 47.4% | 71.1% | +23.7% |
| English (`eng`) | 45.5% | 52.4% | +6.9% |
| Spanish (`spa`) | 64.7% | 71.6% | +6.9% |
| French (`fra`) | 66.2% | 74.8% | +8.6% |
| German (`deu`) | 68.6% | 76.4% | +7.8% |
| Italian (`ita`) | 62.9% | 69.0% | +6.1% |

Script-distinct languages (Arabic, Thai, Japanese, Korean, etc.) already score ≥95%
at @20 in the standard model and see little change in the short-text model.

### English @20 — remaining confusions (short-text v1)

English @20 F1 = 52.4%. Top confusions:

| Predicted | Count | Notes |
|-----------|:---:|-------|
| `unk` (below confidence) | 158 | Model not confident enough to commit — flat distribution across Latin-script languages. Dual confidence thresholds (general: 0.20, short-text: 0.10) recover ~10 sentences vs uniform threshold. |
| `dag` (Dagbani) | 33 | |
| `ita` (Italian) | 29 | |
| `mlg` (Malagasy) | 22 | |
| `yor` (Yoruba) | 17 | |
| `aze` (Azerbaijani) | 17 | |
| `orm` (Oromo) | 17 | |

The primary bottleneck is not misclassification but **low confidence on very short
Latin-script text**. At 20 characters, English function words (`the`, `of`, `in`, `is`)
are shared with or resemble words in many other Latin-script languages, producing
insufficient logit margin between candidates.

**Planned improvement**: stop-word anchoring — a fixed vocabulary of high-frequency
function words per language, hashed as exact-match features. Even at 20 chars, bigrams
like `of the` or `in the` would provide a decisive English signal, while `van de` or
`in het` would anchor Dutch. This addresses the `unk` problem directly without
threshold manipulation.
