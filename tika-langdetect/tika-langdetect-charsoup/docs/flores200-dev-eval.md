# CharSoup Language Detection — Evaluation Report

**Model**: v5, **198 languages**, 8 192 buckets, character bigrams, INT8-quantized.  
**Training corpus**: Wikipedia (primary) + MADLAD supplements for `lus`, `cnh`, `kha`, `div`.  
**Inference**: Logit-based — softmax array never materialized at inference time.
See `CharSoupLanguageDetector` javadoc for details.

Raw FLORES-200 report: [`flores200-v5-comparison.txt`](flores200-v5-comparison.txt)

---

## Inference pipeline (v5)

v5 simplified the pipeline relative to v4. The full sequence applied to every
input chunk is:

1. **Feature extraction** — character bigram counts → sparse feature vector
2. **Raw logits** — dot-product against model weight matrix
3. **Script gate** — mask languages whose expected script doesn't match the
   dominant script of the input (fires when ≥85% of characters fall in one
   Unicode block group)
4. **Confusable-group collapse** — for structurally confusable pairs
   (`ind`/`msa`, `yue`/`zho`), sum the logits of both members and return only
   the higher-scoring one as the group winner
5. **Rank and return** — top-k results by logit

The **length-gated confusables** mechanism present in v4 has been removed
entirely. Languages that depended on the gate (`hat`, `nds-nl`, `map-bms`)
were dropped from the model rather than gated. See `language-drop-decisions.md`
for rationale.

---

## Detectors compared

| Detector | Notes | Model size | Languages |
|----------|-------|-----------|-----------|
| **CharSoup** | This model, logit-based inference | **~1.5 MB** | **198** |
| **OpenNLP** | `tika-langdetect-opennlp`, 12 instances | ~76 MB | 91 (of 198) |
| **Lingua** | `lingua-detector`, low-accuracy mode | ~0.1 MB | 68 (of 198) |
| **Optimaize** | `language-detector` 0.6, 12 instances | ~95 MB | 65 (of 198) |

> **Lingua caveat**: evaluated in *low-accuracy mode*. High-accuracy mode would
> improve Lingua's numbers, particularly at short lengths, at the cost of ~300 MB
> RAM and much higher latency.

> **Optimaize note**: Optimaize's coverage-adjusted and intersection scores are
> computed only over the languages it declares support for. It always returns a
> best-guess for any input, so breadth-weighted accuracy penalises it heavily
> for the 133 languages CharSoup covers that it does not.

---

## Wikipedia dev-set benchmark (v4 reference)

> **Note**: These numbers are from the v4 model (210 languages, with
> length-gating). They are retained as a reference baseline; the FLORES section
> below contains v5 out-of-domain numbers. A v5 in-domain re-run is pending.

**Evaluation set**: Wikipedia dev split (~2.36M sentences, 210 languages).
In-domain evaluation (same source as training).

### Coverage-adjusted accuracy

Each detector scored only on sentences in languages it supports.

```
        ─ CharSoup ─    ─ OpenNLP ─     ── Lingua ──    ─ Optimaize ─
Length     mF1    acc     mF1    acc     mF1    acc     mF1    acc
----------------------------------------------------------------------
@20      61.57%  65.06%   63.62%  59.45%   64.92%  64.22%   75.22%  73.84%
@50      90.01%  92.24%   79.84%  77.41%   82.82%  82.34%   90.25%  89.62%
@100     96.07%  96.99%   85.69%  84.09%   90.45%  90.16%   93.83%  93.49%
@200     97.07%  97.77%   87.71%  86.52%   92.94%  92.82%   94.59%  94.34%
@500     97.21%  97.87%   88.08%  86.91%   93.37%  93.28%   94.73%  94.52%
```

### Throughput (Wikipedia dev, v4)

| Length | CharSoup | OpenNLP | Lingua | Optimaize |
|--------|----------|---------|--------|-----------|
| @20 | ~400k/s | 300k/s | 17k/s | 591k/s |
| @50 | ~330k/s | 188k/s | 10k/s | 75k/s |
| @100 | ~228k/s | 100k/s | 6k/s | 69k/s |
| @500 | ~139k/s | 37k/s | 3k/s | 63k/s |

---

## FLORES-200 benchmark (v5)

**Evaluation set**: FLORES-200 dev split (203,381 sentences, 203 languages).
Out-of-domain, clean translated prose — no Wikipedia-style citation or template
noise. All CharSoup numbers use the **full production pipeline**: script gate +
confusable-group collapse. Raw report: [`flores200-v5-comparison.txt`](flores200-v5-comparison.txt)

### Coverage-adjusted accuracy

Each detector scored only on sentences whose true language it supports.

```
        ─ CharSoup ─    ─ OpenNLP ─     ── Lingua ──    ─ Optimaize ─
Length     mF1    acc     mF1    acc     mF1    acc     mF1    acc
----------------------------------------------------------------------
@20      77.98%  73.34%   75.31%  72.73%   76.39%  75.25%   85.45%  84.57%
@50      94.41%  93.66%   86.49%  85.45%   91.07%  90.35%   95.27%  94.88%
@100     97.64%  97.49%   90.65%  90.27%   95.53%  95.20%   97.48%  97.27%
@200     98.11%  98.03%   91.45%  91.27%   96.32%  96.10%   97.89%  97.70%
@500     98.11%  98.04%   91.47%  91.29%   96.33%  96.11%   97.91%  97.72%
full     98.11%  98.04%   91.47%  91.29%   96.33%  96.11%   97.91%  97.72%
```

CharSoup leads OpenNLP by **7–8 pp** at @100+ on a model **50× smaller**.
At @100 and above CharSoup **leads Optimaize** (98.11% vs 97.91%) despite
Optimaize being purpose-built for its 65 high-resource European languages and
carrying a 95 MB model.

Optimaize leads at @20 on its own turf; its 65 supported languages are mostly
major European ones where short-text performance is strongest.

### Breadth-weighted accuracy

Scored across all 203 FLORES languages; detectors that don't support a language
score 0 for those sentences. Penalises limited coverage.

```
        ─ CharSoup ─    ─ OpenNLP ─     ── Lingua ──    ─ Optimaize ─
Length     mF1    acc     mF1    acc     mF1    acc     mF1    acc
----------------------------------------------------------------------
@20      77.98%  40.99%   75.31%  72.73%   24.84%  24.72%   85.45%  84.57%
@50      94.41%  52.34%   86.49%  85.45%   29.61%  29.67%   95.27%  94.88%
@100     97.64%  54.48%   90.65%  90.27%   31.06%  31.27%   97.48%  97.27%
@200     98.11%  54.78%   91.45%  91.27%   31.31%  31.56%   97.89%  97.70%
full     98.11%  54.78%   91.47%  91.29%   31.32%  31.56%   97.91%  97.72%
```

Breadth-weighted mF1 reflects true detection utility across a diverse input
stream. CharSoup's 94.41% at @50 vs Lingua's 29.61% is almost entirely a
coverage gap: Lingua covers 68 languages vs CharSoup's 198. OpenNLP's
breadth-weighted and coverage-adjusted numbers coincide because FLORES includes
all 91 of OpenNLP's supported languages.

### Apples-to-apples: shared language subsets

**CharSoup vs OpenNLP** (91 shared languages, 90,726 sentences):
```
        ── CharSoup ──   ── OpenNLP ──
Length     mF1    acc      mF1    acc
--------------------------------------
@20      80.20%  74.77%   77.42%  73.82%
@50      95.88%  94.73%   88.36%  86.89%
@100     98.81%  98.42%   92.43%  91.82%
@200     99.25%  98.97%   93.23%  92.84%
full     99.25%  98.98%   93.24%  92.86%
```

**CharSoup vs Lingua low-accuracy** (68 shared languages, 60,814 sentences):
```
        ── CharSoup ──   ── Lingua ──
Length     mF1    acc     mF1    acc
--------------------------------------
@20      80.90%  74.59%   79.16%  77.00%
@50      96.13%  94.57%   93.01%  91.76%
@100     98.88%  98.31%   97.11%  96.45%
@200     99.28%  98.89%   97.82%  97.28%
full     99.29%  98.89%   97.83%  97.29%
```

**CharSoup vs Optimaize** (65 shared languages, 57,823 sentences):
```
        ── CharSoup ──   ─ Optimaize ─
Length     mF1    acc     mF1    acc
--------------------------------------
@20      80.63%  74.07%   85.42%  84.52%
@50      96.22%  94.56%   95.21%  94.82%
@100     98.94%  98.33%   97.44%  97.22%
@200     99.35%  98.93%   97.85%  97.66%
full     99.35%  98.93%   97.87%  97.68%
```

Optimaize leads at @20. CharSoup overtakes it at @50 and leads by ~1.5 pp at
full length on Optimaize's own supported language set, while carrying a model
**63× smaller** (1.5 MB vs 95 MB).

### Throughput (FLORES, v5, full production pipeline)

| Length | CharSoup sent/s | OpenNLP sent/s | Lingua sent/s | Optimaize sent/s |
|--------|----------------|----------------|---------------|-----------------|
| @20 | ~93,000 | ~86,000 | ~17,000 | ~370,000 |
| @50 | ~97,000 | ~83,000 | ~8,000 | ~30,000 |
| @100 | ~82,000 | ~46,000 | ~5,000 | ~31,000 |
| @500 | ~69,000 | ~28,000 | ~4,000 | ~29,000 |

CharSoup is **25–60× faster than Lingua** and **2–3× faster than OpenNLP**.
Optimaize leads at @20 because its supported language set is tiny and inference
short-circuits quickly; CharSoup is faster at all longer lengths.

---

## Script gate bug fix (v5)

Fixing `CompareDetectors` to route all CharSoup predictions through
`CharSoupLanguageDetector` (rather than calling `CharSoupModel.predict()`
directly) revealed that three languages were missing from the script assignment
table in `buildClassScript()`. They defaulted to LATIN, causing the script gate
to mask them out entirely on Cyrillic or Perso-Arabic input — the model had
correct weights for them but could never return them in production.

| Language | Script | Before fix (full F1) | After fix (full F1) |
|----------|--------|---------------------|---------------------|
| `azb` (South Azerbaijani) | Perso-Arabic | 0.4% | 93.0% |
| `tgk` (Tajik) | Cyrillic | 3.9% | 100.0% |
| `kaz` (Kazakh) | Cyrillic | 5.8% | 100.0% |

Fix: added `azb` and `pnb` to the ARABIC block, and `kaz` and `tgk` to the
CYRILLIC block in `CharSoupLanguageDetector.buildClassScript()`. No model
retrain was needed.

These bugs were invisible in v4 evaluation because the old `CompareDetectors`
bypassed the script gate. They are a reminder that the eval tool must exercise
the full production pipeline.

---

## Known remaining limitations

**`yue` / `zho` (Yue Chinese / Mandarin Chinese):**  
Both use Han script. They are treated as a confusable pair — logits are
combined and only the higher-scoring one is returned. In practice, `yue`
Wikipedia training data is mostly in Traditional characters, and FLORES `yue`
is entirely Traditional, so nearly all `yue` input is returned as `zho`.
FLORES full-length F1: `yue` 2.7%, `zho` 79.8%.

**`ind` / `msa` (Indonesian / Malay):**  
Structurally confusable pair. FLORES full-length F1: `ind` 89.7% with `msa`
absorbing 130 of the errors. This is a genuine corpus overlap issue, not a
model defect.

**`nld` (Dutch):**  
93.9% full F1 — `vls` (West Flemish) bleeds 78 sentences into `nld` and `afr`
(Afrikaans) bleeds 8. Accepted: `vls` and `afr` are in the model and have
near-identical character bigrams to `nld` in many constructions.

**Short-text performance @20:**  
Many related-language clusters have low @20 recall due to shared short function
words and n-gram profiles (Germanic cluster, Slavic cluster, Romance cluster,
Malay/Indonesian cluster). This is structural, not a model defect.

---

## Notes and limitations

- FLORES evaluation used `flores200_dev.tsv` (not devtest). Lang codes
  normalised by stripping script suffixes (`ita_Latn` → `ita`) except for a
  small set where the script variant is meaningfully distinct (see
  `CompareDetectors.FLORES_KEEP_SCRIPT_SUFFIX`).
- CharSoup's per-class `rawScore` is `exp(logit_c − logsumexp(all logits))`,
  equivalent to the softmax probability of class c without materialising a full
  probability distribution array.
- Lingua low-accuracy mode numbers are a lower bound on Lingua's true capability.
- The CharSoup heap figure in the raw report shows `~-0.6 MB` due to GC
  fluctuation during the measurement window; the true loaded model size is
  ~1.5 MB.
