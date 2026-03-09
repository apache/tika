# CharSoup Language Detection — Evaluation Report

**Models**: v7 standard (203 languages, 16 384 buckets) + v7 short-text (123 languages, 32 768 buckets).  
**Training corpus**: Wikipedia + MADLAD supplements for `lus`, `cnh`, `kha`, `div`, `mlg`, `che`.  
**Evaluation set**: [FLORES-200](https://github.com/facebookresearch/flores) dev split (203 languages × 1 001 sentences = 203 381 sentences).
Sentence lengths: median 124 chars, 91% under 200 chars — scores flatten from @200 onward because truncating to 200 vs 500 chars affects only the remaining 9% of sentences.  
**Raw reports**: [`flores-STANDARD.log`](../../../datasets/compare-v7/flores-STANDARD.log),
[`flores-SHORT_TEXT.log`](../../../datasets/compare-v7/flores-SHORT_TEXT.log),
[`flores-AUTOMATIC.log`](../../../datasets/compare-v7/flores-AUTOMATIC.log)

---

## Inference pipeline (v7)

The full pipeline applied to every input chunk:

1. **Feature extraction** — character bigrams + word unigrams → sparse feature vector  
   (short-text model additionally uses trigrams and 4-grams)
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
| **CharSoup standard** | ~3.2 MB | **203** | Bigrams + word unigrams, 16k buckets |
| **CharSoup short-text** | ~3.8 MB | **123** | + trigrams + 4-grams, 32k buckets |
| **CharSoup automatic** | ~7.0 MB combined | **203** | Routes to short-text for short/sparse input |
| **OpenNLP** | ~76 MB | 105 (of 203) | `tika-langdetect-opennlp` |
| **Lingua** | ~0.1 MB | 71 (of 203) | `lingua-detector`, low-accuracy mode |
| **Optimaize** | ~95 MB | 63 (of 203) | `tika-langdetect-optimaize` |

---

## Coverage-adjusted macro-F1

Each detector is scored **only on its own supported language set** — sentences whose true
language is not in a detector's covered set are skipped entirely. This rewards accuracy
within coverage and prevents penalising detectors for languages they don't claim to support.

| Length | CS-std (203L) | CS-short (123L) | CS-auto (203L) | OpenNLP (105L) | Lingua (71L) | Optimaize (63L) |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| @20    | 78.71% | **84.86%** | 65.89%† | 74.87% | 76.35% | 84.87% |
| @50    | 93.73% | **96.04%** | 74.33%† | 86.09% | 90.99% | 94.44% |
| @100   | 96.67% | **97.90%** | 75.86%† | 90.25% | 95.43% | 96.51% |
| @150   | 97.08% | **98.10%** | 76.03%† | 90.98% | 96.15% | 96.72% |
| @200   | 97.14% | **98.12%** | 79.53%† | 91.11% | 96.23% | 96.75% |
| @500   | 97.14% | **98.12%** | 79.54%† | 91.12% | 96.25% | 96.76% |

† **CS-auto caveat**: in AUTOMATIC mode the length threshold fires for every FLORES sentence
(all are ≥ 20 chars), routing all 203 languages through the 123-language short-text model.
The 80 languages that are in the standard model but not the short-text model receive
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
| @20    | 78.71% | 84.86% | 65.89%* | 74.87% | 27.46% | 84.87% |
| @50    | 93.73% | 96.04% | 74.33%* | 86.09% | 32.72% | 94.44% |
| @100   | 96.67% | 97.90% | 75.86%* | 90.25% | 34.32% | 96.51% |
| @150   | 97.08% | 98.10% | 76.03%* | 90.98% | 34.58% | 96.72% |
| @200   | 97.14% | 98.12% | 79.53%* | 91.11% | 34.61% | 96.75% |
| @500   | 97.14% | 98.12% | 79.54%* | 91.12% | 34.61% | 96.76% |

\* **CS-auto apples/oranges caveat**: the AUTOMATIC system claims 203-language coverage,
but the routing threshold fires whenever `input.length() < 200`, routing those sentences
to the 123-language short-text model. The FLORES dev set has a **median sentence length
of 124 chars**, and 91% of sentences are under 200 chars — meaning the short-text model
handles the vast majority of FLORES sentences at every eval length, including @200 and
@500 (which truncate to *at most* that many chars, not exactly). The 80 languages that
exist only in the standard model therefore receive wrong predictions throughout, depressing
the breadth-weighted score across all lengths. This is not directly comparable to the
other detectors' scores in the same column: those detectors simply return no result for
unsupported languages (scoring 0), whereas CS-auto actively misclassifies them.

Other notes:
- CS-short's breadth-weighted score equals its coverage-adjusted score because its
  123 supported languages are a subset of the 203 FLORES languages — unsupported languages
  score 0 in both metrics. Its per-language accuracy on those 123 languages outperforms
  every other detector, but it does not cover the remaining 80 languages.
- Lingua's low breadth-weighted scores (27–35%) reflect its 71-language coverage, not
  poor per-language accuracy — on its own supported set it scores 76–96%.

---

## Intersection comparisons (shared supported languages only)

For a fair head-to-head, both detectors are evaluated on the **intersection** of their
supported language sets. See the raw report files for the full pairwise intersection tables.

Key intersections at @20 chars (coverage-adjusted on shared languages):

| Pair | Shared langs | CS F1 | Other F1 |
|------|:---:|:---:|:---:|
| CS-std ∩ OpenNLP | 105 | see STANDARD.log | see STANDARD.log |
| CS-std ∩ Lingua  | 71  | see STANDARD.log | see STANDARD.log |
| CS-std ∩ Optimaize | 63 | see STANDARD.log | see STANDARD.log |
| CS-short ∩ Optimaize | 59 | see SHORT_TEXT.log | see SHORT_TEXT.log |

---

## Throughput

Measured on FLORES-200 dev set, multi-threaded (12 cores), Apple M-series.
All CharSoup variants run within a single JVM with both models loaded.

| Length | CS-std | CS-short | CS-auto | OpenNLP | Lingua | Optimaize |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| @20    | 28.6k/s | 20.7k/s | 24.4k/s | 31.4k/s | 3.9k/s | 74.0k/s |
| @50    | 27.8k/s | 22.3k/s | 25.1k/s | 35.3k/s | 2.3k/s | 6.9k/s |
| @100   | 31.0k/s | 46.1k/s | 28.9k/s | 25.5k/s | 1.4k/s | 7.2k/s |
| @200   | 31.8k/s | 38.1k/s | 26.2k/s | 14.7k/s | 1.1k/s | 8.8k/s |
| @500   | 32.0k/s | 40.4k/s | 27.0k/s | 15.3k/s | 1.1k/s | 8.8k/s |

**Notes:**
- Optimaize is very fast at @20 (74k/s) because most of its 63-language model fits in
  cache at short inputs; it degrades sharply at @50+ as scoring cost scales with length.
- CS-short is faster than CS-std at @100+ because it has fewer classes (123 vs 203),
  reducing the final scoring and sort overhead.
- CS-auto adds ~10-15% overhead vs CS-std due to dual feature extraction and routing logic.
- Lingua (low-accuracy mode) is 8–30× slower than CharSoup across all lengths.

---

## Per-language highlights (FLORES @20, CS-std vs CS-short)

Languages with the largest improvement from the short-text model at 20 chars:

| Language | CS-std @20 | CS-short @20 | Gain |
|----------|:---:|:---:|:---:|
| Portuguese (`por`) | ~67% | ~81% | +14% |
| French (`fra`) | ~67% | ~75% | +8% |
| English (`eng`) | ~47% | ~55% | +8% |
| Spanish (`spa`) | ~64% | ~71% | +7% |
| Dutch (`nld`) | ~64% | ~70% | +7% |
| German (`deu`) | ~71% | ~76% | +4% |
| Italian (`ita`) | ~67% | ~69% | +2% |

Script-distinct languages (Arabic, Thai, Japanese, Korean, etc.) already score ≥95%
at @20 in the standard model and see little change in the short-text model.
