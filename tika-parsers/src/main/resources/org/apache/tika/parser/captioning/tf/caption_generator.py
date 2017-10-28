#!/usr/bin/env python
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#    http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import heapq
import math
import numpy as np


class Caption(object):
    """
        A complete or partial caption object
    """

    def __init__(self, sentence, state, logprob, score):
        """Initializes the Caption"""

        # list of word_ids in the caption
        self.sentence = sentence
        # model state after generating the previous word
        self.state = state
        # log probability of the caption
        self.logprob = logprob
        # score of the caption
        self.score = score

    def __cmp__(self, other):
        """Compares Captions by score"""

        assert isinstance(other, Caption)
        if self.score == other.score:
            return 0
        elif self.score < other.score:
            return -1
        else:
            return 1

    # for Python 3 compatibility (__cmp__ is deprecated).
    def __lt__(self, other):
        assert isinstance(other, Caption)
        return self.score < other.score

    # also for Python 3 compatibility.
    def __eq__(self, other):
        assert isinstance(other, Caption)
        return self.score == other.score


class TopN(object):
    """Maintains the top N elements of an incrementally provided set"""

    def __init__(self, n):
        self._n = n
        self._data = []

    def size(self):
        assert self._data is not None
        return len(self._data)

    def push(self, x):
        """Pushes a new element"""

        assert self._data is not None
        if len(self._data) < self._n:
            heapq.heappush(self._data, x)
        else:
            heapq.heappushpop(self._data, x)

    def extract(self, sort=False):
        """
            Extracts all elements from the TopN. This is a destructive operation,
            The only method that can be called immediately after extract() is reset()
        """
        assert self._data is not None
        data = self._data
        self._data = None
        if sort:
            data.sort(reverse=True)
        return data

    def reset(self):
        """Returns the TopN to an empty state"""

        self._data = []


class CaptionGenerator(object):
    """
        Class to generate captions from an image-to-text model
    """

    def __init__(self,
                 model,
                 vocab,
                 beam_size,
                 max_caption_length,
                 length_normalization_factor=0.0):

        self.vocab = vocab
        self.model = model

        self.beam_size = beam_size
        self.max_caption_length = max_caption_length
        self.length_normalization_factor = length_normalization_factor

    def beam_search(self, sess, encoded_image):
        """Runs beam search caption generation on a single image"""

        # feed in the image to get the initial state.
        initial_state = self.model.feed_image(sess, encoded_image)

        initial_beam = Caption(
            sentence=[self.vocab.start_id],
            state=initial_state[0],
            logprob=0.0,
            score=0.0)
        partial_captions = TopN(self.beam_size)
        partial_captions.push(initial_beam)
        complete_captions = TopN(self.beam_size)

        # run beam search.
        for _ in range(self.max_caption_length - 1):
            partial_captions_list = partial_captions.extract()
            partial_captions.reset()
            input_feed = np.array([c.sentence[-1] for c in partial_captions_list])
            state_feed = np.array([c.state for c in partial_captions_list])

            softmax, new_states = self.model.inference_step(sess, input_feed, state_feed)

            for i, partial_caption in enumerate(partial_captions_list):
                word_probabilities = softmax[i]
                state = new_states[i]
                # for this partial caption, get the beam_size most probable next words.
                words_and_probs = list(enumerate(word_probabilities))
                words_and_probs.sort(key=lambda x: -x[1])
                words_and_probs = words_and_probs[0:self.beam_size]
                # each next word gives a new partial caption.
                for w, p in words_and_probs:
                    if p < 1e-12:
                        continue  # avoid log(0).
                    sentence = partial_caption.sentence + [w]
                    logprob = partial_caption.logprob + math.log(p)
                    score = logprob

                    if w == self.vocab.end_id:
                        if self.length_normalization_factor > 0:
                            score /= len(sentence) ** self.length_normalization_factor
                        beam = Caption(sentence, state, logprob, score)
                        complete_captions.push(beam)
                    else:
                        beam = Caption(sentence, state, logprob, score)
                        partial_captions.push(beam)
            if partial_captions.size() == 0:
                # we have run out of partial candidates; happens when beam_size = 1.
                break

        # if we have no complete captions then fall back to the partial captions,
        # but never output a mixture of complete and partial captions because a
        # partial caption could have a higher score than all the complete captions
        if not complete_captions.size():
            complete_captions = partial_captions

        return complete_captions.extract(sort=True)
