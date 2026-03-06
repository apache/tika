#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Simulated annealing over ByteNgramFeatureExtractor hyperparameters.

Search space
------------
  numBuckets  : one of BUCKET_CHOICES (log-scale discrete)
  useUnigrams : bool
  useBigrams  : bool
  useTrigrams : bool
  useAnchored : bool  (anchored bigrams, default off)

Each trial:
  1. Train  → TrainCharsetModel  (Java, via mvn exec:java or fat JAR)
  2. Eval   → EvalCharsetDetectors --score-only  (parses "SCORE <pct>" line)
  3. SA accept/reject

Usage
-----
  # 1. Build the fat JAR once (from the repo root):
  ./mvnw package -pl tika-ml/tika-ml-chardetect -am -Ptrain -DskipTests \\
      -Dmaven.repo.local=.local_m2_repo -Dcheckstyle.skip=true

  # 2. Run the annealing study:
  python anneal.py \\
      --train-data   ~/datasets/madlad/charset-detect2/train \\
      --devtest-data ~/datasets/madlad/charset-detect2/devtest \\
      --jar          tika-ml/tika-ml-chardetect/target/tika-ml-chardetect-*-tools.jar \\
      --work-dir     /tmp/anneal \\
      --best-output  tika-encoding-detectors/tika-encoding-detector-mojibuster/\\
                       src/main/resources/org/apache/tika/ml/chardetect/chardetect.bin \\
      [--trials 80] [--t-start 8.0] [--t-end 0.5] [--seed 42] \\
      [--epochs 3] [--lr 0.05] [--java java]
"""

import argparse
import csv
import math
import os
import random
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path

# ---------------------------------------------------------------------------
# Search space
# ---------------------------------------------------------------------------

BUCKET_CHOICES = [512, 1024, 2048, 4096, 8192, 16384, 32768]

TRAIN_CLASS = "org.apache.tika.ml.chardetect.tools.TrainCharsetModel"
EVAL_CLASS  = "org.apache.tika.ml.chardetect.tools.EvalCharsetDetectors"


# ---------------------------------------------------------------------------
# State representation
# ---------------------------------------------------------------------------

class Config:
    __slots__ = ("bucket_idx", "uni", "bi", "tri", "anchored", "stride2")

    def __init__(self, bucket_idx=2, uni=True, bi=True, tri=True, anchored=False,
                 stride2=True):
        self.bucket_idx = bucket_idx   # index into BUCKET_CHOICES
        self.uni      = uni
        self.bi       = bi
        self.tri      = tri
        self.anchored = anchored
        self.stride2  = stride2

    @property
    def num_buckets(self):
        return BUCKET_CHOICES[self.bucket_idx]

    def copy(self):
        return Config(self.bucket_idx, self.uni, self.bi, self.tri, self.anchored,
                      self.stride2)

    def train_flags(self):
        """Return extra CLI flags for TrainCharsetModel."""
        flags = []
        if not self.uni:     flags.append("--no-uni")
        if not self.bi:      flags.append("--no-bi")
        if not self.tri:     flags.append("--no-tri")
        if self.anchored:    flags.append("--anchored")
        if not self.stride2: flags.append("--no-stride2")
        return flags

    def __str__(self):
        feats = "".join([
            "U" if self.uni      else "-",
            "B" if self.bi       else "-",
            "T" if self.tri      else "-",
            "A" if self.anchored else "-",
            "S" if self.stride2  else "-",
        ])
        return f"buckets={self.num_buckets:>6}  features={feats}"

    def as_dict(self):
        return {
            "num_buckets": self.num_buckets,
            "uni":         self.uni,
            "bi":          self.bi,
            "tri":         self.tri,
            "anchored":    self.anchored,
            "stride2":     self.stride2,
        }


def random_neighbor(cfg: Config, rng: random.Random) -> Config:
    """Return a neighbouring config: flip one random dimension."""
    n = cfg.copy()
    # 6 dimensions: bucket_idx (±1 step) + 5 boolean flags
    dim = rng.randint(0, 5)
    if dim == 0:
        # Step bucket_idx up or down by 1, clamped
        delta = rng.choice([-1, 1])
        n.bucket_idx = max(0, min(len(BUCKET_CHOICES) - 1, n.bucket_idx + delta))
    elif dim == 1:
        n.uni = not n.uni
    elif dim == 2:
        n.bi = not n.bi
    elif dim == 3:
        n.tri = not n.tri
    elif dim == 4:
        n.anchored = not n.anchored
    else:
        n.stride2 = not n.stride2
    return n


# ---------------------------------------------------------------------------
# Train + eval
# ---------------------------------------------------------------------------

def run_train(cfg: Config, train_data: Path, model_path: Path,
              java: str, jar: str, epochs: int, lr: float,
              max_samples: int) -> bool:
    cmd = [
        java, "-cp", jar, TRAIN_CLASS,
        "--data",    str(train_data),
        "--output",  str(model_path),
        "--buckets", str(cfg.num_buckets),
        "--epochs",  str(epochs),
        "--lr",      str(lr),
        "--max-samples-per-class", str(max_samples),
    ] + cfg.train_flags()

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  [TRAIN ERROR]\n{result.stderr[-2000:]}", file=sys.stderr)
        return False
    return True


def run_eval(model_path: Path, devtest_data: Path,
             java: str, jar: str) -> float | None:
    cmd = [
        java, "-cp", jar, EVAL_CLASS,
        "--model",      str(model_path),
        "--data",       str(devtest_data),
        "--score-only",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  [EVAL ERROR]\n{result.stderr[-2000:]}", file=sys.stderr)
        return None
    for line in result.stdout.splitlines():
        if line.startswith("SCORE "):
            return float(line.split()[1])
    print(f"  [EVAL] no SCORE line found in output:\n{result.stdout[-1000:]}", file=sys.stderr)
    return None


def evaluate(cfg: Config, train_data: Path, devtest_data: Path,
             work_dir: Path, java: str, jar: str,
             epochs: int, lr: float, max_samples: int,
             trial_idx: int) -> float | None:
    model_path = work_dir / f"trial_{trial_idx:04d}.bin"
    ok = run_train(cfg, train_data, model_path, java, jar, epochs, lr, max_samples)
    if not ok:
        return None
    score = run_eval(model_path, devtest_data, java, jar)
    # Keep only the best model to save disk space
    try:
        model_path.unlink()
    except OSError:
        pass
    return score


# ---------------------------------------------------------------------------
# Simulated annealing
# ---------------------------------------------------------------------------

def temperature(step: int, total: int, t_start: float, t_end: float) -> float:
    """Exponential cooling schedule."""
    ratio = step / max(1, total - 1)
    return t_start * (t_end / t_start) ** ratio


def accept(delta: float, temp: float, rng: random.Random) -> bool:
    """Metropolis criterion: always accept improvements, probabilistically accept worsening."""
    if delta >= 0:
        return True
    return rng.random() < math.exp(delta / temp)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Simulated annealing over ByteNgramFeatureExtractor hyperparameters",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--train-data",   type=Path, required=True,
                        help="Directory of train/*.bin.gz files")
    parser.add_argument("--devtest-data", type=Path, required=True,
                        help="Directory of devtest/*.bin.gz files")
    parser.add_argument("--work-dir",     type=Path, default=Path(tempfile.mkdtemp()),
                        help="Working directory for intermediate models and log")
    parser.add_argument("--jar",          required=True,
                        help="Path to the tika-ml-chardetect-*-tools.jar fat JAR")
    parser.add_argument("--java",         default="java",
                        help="Path to java executable")
    parser.add_argument("--trials",       type=int, default=80,
                        help="Total SA trials (train+eval calls)")
    parser.add_argument("--t-start",      type=float, default=8.0,
                        help="Initial SA temperature")
    parser.add_argument("--t-end",        type=float, default=0.5,
                        help="Final SA temperature")
    parser.add_argument("--seed",         type=int, default=42)
    parser.add_argument("--epochs",       type=int, default=3,
                        help="SGD epochs per trial")
    parser.add_argument("--lr",           type=float, default=0.05,
                        help="SGD learning rate")
    parser.add_argument("--max-samples",  type=int, default=500_000,
                        help="Max training samples per class")
    parser.add_argument("--best-output",  type=Path, default=None,
                        help="Save the best model to this path")
    parser.add_argument("--min-buckets",  type=int, default=None,
                        help="Restrict bucket search to this minimum (e.g. 8192)")
    args = parser.parse_args()

    args.work_dir.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)

    # Optionally restrict the bucket search space to high values only.
    global BUCKET_CHOICES
    if args.min_buckets is not None:
        BUCKET_CHOICES = [b for b in BUCKET_CHOICES if b >= args.min_buckets]
        if not BUCKET_CHOICES:
            print(f"ERROR: --min-buckets {args.min_buckets} excludes all choices", file=sys.stderr)
            sys.exit(1)
        print(f"Bucket search space restricted to: {BUCKET_CHOICES}")

    # Resolve glob in jar path (e.g. target/tika-ml-chardetect-*-tools.jar)
    jar = str(args.jar)
    if "*" in jar:
        import glob as _glob
        matches = _glob.glob(jar)
        if not matches:
            print(f"ERROR: no file matches --jar {jar}", file=sys.stderr)
            sys.exit(1)
        jar = matches[0]
    print(f"JAR: {jar}")

    log_path = args.work_dir / "anneal_log.csv"
    print(f"Annealing log: {log_path}")
    print(f"Trials: {args.trials}  T: {args.t_start}→{args.t_end}  seed: {args.seed}")
    print(f"Train:   {args.train_data}")
    print(f"Devtest: {args.devtest_data}")
    print()

    csv_fields = ["trial", "timestamp", "num_buckets", "uni", "bi", "tri", "anchored",
                  "stride2", "score", "best_score", "accepted", "temperature"]

    with open(log_path, "w", newline="") as log_fh:
        writer = csv.DictWriter(log_fh, fieldnames=csv_fields)
        writer.writeheader()

        # Start from the middle of the (possibly restricted) bucket range.
        start_bucket_idx = len(BUCKET_CHOICES) // 2
        current = Config(bucket_idx=start_bucket_idx, uni=True, bi=True, tri=True,
                         anchored=False, stride2=True)
        current_score = None
        best = current.copy()
        best_score = -1.0
        best_model_path = args.work_dir / "best.bin"

        for trial in range(args.trials):
            temp = temperature(trial, args.trials, args.t_start, args.t_end)

            if trial == 0:
                candidate = current
            else:
                candidate = random_neighbor(current, rng)

            ts = datetime.now().strftime("%H:%M:%S")
            print(f"[{trial+1:3d}/{args.trials}]  T={temp:.2f}  {candidate}  …", flush=True)

            score = evaluate(
                candidate, args.train_data, args.devtest_data, args.work_dir,
                args.java, jar, args.epochs, args.lr, args.max_samples,
                trial,
            )

            if score is None:
                print(f"         → FAILED, skipping")
                writer.writerow({
                    "trial": trial + 1, "timestamp": ts,
                    **candidate.as_dict(),
                    "score": "", "best_score": best_score,
                    "accepted": False, "temperature": round(temp, 4),
                })
                log_fh.flush()
                continue

            delta = score - (current_score if current_score is not None else score)
            accepted = (current_score is None) or accept(delta, temp, rng)

            if score > best_score:
                best_score = score
                best = candidate.copy()
                # Re-train best config and save the model
                best_model_path_tmp = args.work_dir / f"trial_{trial:04d}_best.bin"
                run_train(candidate, args.train_data, best_model_path_tmp,
                          args.java, jar, args.epochs, args.lr, args.max_samples)
                best_model_path_tmp.rename(best_model_path)
                print(f"         → score={score:.4f}%  *** NEW BEST ***  accepted", flush=True)
            else:
                status = "accepted" if accepted else "rejected"
                print(f"         → score={score:.4f}%  (best={best_score:.4f}%)  {status}",
                      flush=True)

            if accepted:
                current = candidate
                current_score = score

            writer.writerow({
                "trial": trial + 1, "timestamp": ts,
                **candidate.as_dict(),
                "score": round(score, 4), "best_score": round(best_score, 4),
                "accepted": accepted, "temperature": round(temp, 4),
            })
            log_fh.flush()

    print()
    print("=" * 60)
    print(f"Best score:  {best_score:.4f}%")
    print(f"Best config: {best}")
    print(f"Best model:  {best_model_path}")
    if args.best_output:
        import shutil
        shutil.copy(best_model_path, args.best_output)
        print(f"Copied to:   {args.best_output}")


if __name__ == "__main__":
    main()
