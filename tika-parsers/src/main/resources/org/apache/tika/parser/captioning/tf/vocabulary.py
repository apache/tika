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

import tensorflow as tf


class Vocabulary(object):
    """
        Vocabulary class for an image-to-text model
    """

    def __init__(self,
                 vocab_file,
                 start_word="<S>",
                 end_word="</S>",
                 unk_word="<UNK>"):
        """Initializes the vocabulary"""

        if not tf.gfile.Exists(vocab_file):
            tf.logging.fatal("Vocab file %s not found.", vocab_file)
        tf.logging.info("Initializing vocabulary from file: %s", vocab_file)

        with tf.gfile.GFile(vocab_file, mode="r") as f:
            reverse_vocab = list(f.readlines())
        reverse_vocab = [line.split()[0] for line in reverse_vocab]
        assert start_word in reverse_vocab
        assert end_word in reverse_vocab
        if unk_word not in reverse_vocab:
            reverse_vocab.append(unk_word)
        vocab = dict([(x, y) for (y, x) in enumerate(reverse_vocab)])

        tf.logging.info("Created vocabulary with %d words" % len(vocab))

        # vocab[word] = id
        self.vocab = vocab
        # reverse_vocab[id] = word
        self.reverse_vocab = reverse_vocab

        # save special word ids
        self.start_id = vocab[start_word]
        self.end_id = vocab[end_word]
        self.unk_id = vocab[unk_word]

    def id_to_word(self, word_id):
        """Returns the word string of an integer word id"""

        if word_id >= len(self.reverse_vocab):
            return self.reverse_vocab[self.unk_id]
        else:
            return self.reverse_vocab[word_id]
