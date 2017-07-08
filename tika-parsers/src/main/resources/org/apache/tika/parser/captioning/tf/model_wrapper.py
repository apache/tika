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

import os.path

import tensorflow as tf
from tensorflow.contrib.slim.python.slim.nets.inception_v3 import inception_v3_base

slim = tf.contrib.slim


class ModelWrapper(object):
    """
        Model wrapper class to perform image captioning with a ShowAndTellModel
    """

    def __init__(self):
        super(ModelWrapper, self).__init__()

    def build_graph(self, checkpoint_path):
        """Builds the inference graph"""

        tf.logging.info("Building model.")
        ShowAndTellModel().build()
        saver = tf.train.Saver()

        return self._create_restore_fn(checkpoint_path, saver)

    def _create_restore_fn(self, checkpoint_path, saver):
        """Creates a function that restores a model from checkpoint file"""

        if tf.gfile.IsDirectory(checkpoint_path):
            checkpoint_path = tf.train.latest_checkpoint(checkpoint_path)
            if not checkpoint_path:
                raise ValueError("No checkpoint file found in: %s" % checkpoint_path)

        def _restore_fn(sess):
            tf.logging.info("Loading model from checkpoint: %s", checkpoint_path)
            saver.restore(sess, checkpoint_path)
            tf.logging.info("Successfully loaded checkpoint: %s",
                            os.path.basename(checkpoint_path))

        return _restore_fn

    def feed_image(self, sess, encoded_image):
        initial_state = sess.run(fetches="lstm/initial_state:0",
                                 feed_dict={"image_feed:0": encoded_image})
        return initial_state

    def inference_step(self, sess, input_feed, state_feed):
        softmax_output, state_output = sess.run(
            fetches=["softmax:0", "lstm/state:0"],
            feed_dict={
                "input_feed:0": input_feed,
                "lstm/state_feed:0": state_feed,
            })
        return softmax_output, state_output


class ShowAndTellModel(object):
    """
        Image captioning implementation based on the paper,

        "Show and Tell: A Neural Image Caption Generator"
        Oriol Vinyals, Alexander Toshev, Samy Bengio, Dumitru Erhan

        For more details, please visit : http://arxiv.org/abs/1411.4555
    """

    def __init__(self):

        # scale used to initialize model variables
        self.initializer_scale = 0.08

        # dimensions of Inception v3 input images
        self.image_height = 299
        self.image_width = 299

        # LSTM input and output dimensionality, respectively
        self.embedding_size = 512
        self.num_lstm_units = 512

        # number of unique words in the vocab (plus 1, for <UNK>)
        # the default value is larger than the expected actual vocab size to allow
        # for differences between tokenizer versions used in preprocessing, there is
        # no harm in using a value greater than the actual vocab size, but using a
        # value less than the actual vocab size will result in an error
        self.vocab_size = 12000

        # reader for the input data
        self.reader = tf.TFRecordReader()

        # to match the "Show and Tell" paper we initialize all variables with a
        # random uniform initializer
        self.initializer = tf.random_uniform_initializer(
            minval=-self.initializer_scale,
            maxval=self.initializer_scale)

        # a float32 Tensor with shape [batch_size, height, width, channels]
        self.images = None

        # an int32 Tensor with shape [batch_size, padded_length]
        self.input_seqs = None

        # an int32 Tensor with shape [batch_size, padded_length]
        self.target_seqs = None

        # an int32 0/1 Tensor with shape [batch_size, padded_length]
        self.input_mask = None

        # a float32 Tensor with shape [batch_size, embedding_size]
        self.image_embeddings = None

        # a float32 Tensor with shape [batch_size, padded_length, embedding_size]
        self.seq_embeddings = None

        # collection of variables from the inception submodel
        self.inception_variables = []

        # global step Tensor
        self.global_step = None

    def process_image(self, encoded_image, resize_height=346, resize_width=346, thread_id=0):
        """Decodes and processes an image string"""

        # helper function to log an image summary to the visualizer. Summaries are
        # only logged in thread 0
        def image_summary(name, img):
            if not thread_id:
                tf.summary.image(name, tf.expand_dims(img, 0))

        # decode image into a float32 Tensor of shape [?, ?, 3] with values in [0, 1)
        with tf.name_scope("decode", values=[encoded_image]):
            image = tf.image.decode_jpeg(encoded_image, channels=3)

        image = tf.image.convert_image_dtype(image, dtype=tf.float32)
        image_summary("original_image", image)

        # resize image
        assert (resize_height > 0) == (resize_width > 0)
        if resize_height:
            image = tf.image.resize_images(image,
                                           size=[resize_height, resize_width],
                                           method=tf.image.ResizeMethod.BILINEAR)

        # central crop, assuming resize_height > height, resize_width > width
        image = tf.image.resize_image_with_crop_or_pad(image, self.image_height, self.image_width)

        image_summary("resized_image", image)

        image_summary("final_image", image)

        # rescale to [-1,1] instead of [0, 1]
        image = tf.subtract(image, 0.5)
        image = tf.multiply(image, 2.0)
        return image

    def build_inputs(self):
        """Input prefetching, preprocessing and batching"""

        image_feed = tf.placeholder(dtype=tf.string, shape=[], name="image_feed")
        input_feed = tf.placeholder(dtype=tf.int64,
                                    shape=[None],  # batch_size
                                    name="input_feed")

        # process image and insert batch dimensions
        images = tf.expand_dims(self.process_image(image_feed), 0)
        input_seqs = tf.expand_dims(input_feed, 1)

        # no target sequences or input mask in inference mode
        target_seqs = None
        input_mask = None

        self.images = images
        self.input_seqs = input_seqs
        self.target_seqs = target_seqs
        self.input_mask = input_mask

    def build_image_embeddings(self):
        """Builds the image model(Inception V3) subgraph and generates image embeddings"""

        # parameter initialization
        batch_norm_params = {
            "is_training": False,
            "trainable": False,
            # decay for the moving averages
            "decay": 0.9997,
            # epsilon to prevent 0s in variance
            "epsilon": 0.001,
            # collection containing the moving mean and moving variance
            "variables_collections": {
                "beta": None,
                "gamma": None,
                "moving_mean": ["moving_vars"],
                "moving_variance": ["moving_vars"],
            }
        }

        stddev = 0.1,
        dropout_keep_prob = 0.8

        with tf.variable_scope("InceptionV3", "InceptionV3", [self.images]) as scope:
            with slim.arg_scope(
                    [slim.conv2d, slim.fully_connected],
                    weights_regularizer=None,
                    trainable=False):
                with slim.arg_scope(
                        [slim.conv2d],
                        weights_initializer=tf.truncated_normal_initializer(stddev=stddev),
                        activation_fn=tf.nn.relu,
                        normalizer_fn=slim.batch_norm,
                        normalizer_params=batch_norm_params):
                    net, end_points = inception_v3_base(self.images, scope=scope)
                    with tf.variable_scope("logits"):
                        shape = net.get_shape()
                        net = slim.avg_pool2d(net, shape[1:3], padding="VALID", scope="pool")
                        net = slim.dropout(
                            net,
                            keep_prob=dropout_keep_prob,
                            is_training=False,
                            scope="dropout")
                        net = slim.flatten(net, scope="flatten")

        # add summaries
        for v in end_points.values():
            tf.contrib.layers.summaries.summarize_activation(v)

        self.inception_variables = tf.get_collection(tf.GraphKeys.GLOBAL_VARIABLES, scope="InceptionV3")

        # map inception output(net) into embedding space
        with tf.variable_scope("image_embedding") as scope:
            image_embeddings = tf.contrib.layers.fully_connected(
                inputs=net,
                num_outputs=self.embedding_size,
                activation_fn=None,
                weights_initializer=self.initializer,
                biases_initializer=None,
                scope=scope)

        # save the embedding size in the graph
        tf.constant(self.embedding_size, name="embedding_size")

        self.image_embeddings = image_embeddings

    def build_seq_embeddings(self):
        """Builds the input sequence embeddings"""

        with tf.variable_scope("seq_embedding"), tf.device("/cpu:0"):
            embedding_map = tf.get_variable(
                name="map",
                shape=[self.vocab_size, self.embedding_size],
                initializer=self.initializer)
            seq_embeddings = tf.nn.embedding_lookup(embedding_map, self.input_seqs)

        self.seq_embeddings = seq_embeddings

    def build_model(self):

        # this LSTM cell has biases and outputs tanh(new_c) * sigmoid(o), but the
        # modified LSTM in the "Show and Tell" paper has no biases and outputs
        # new_c * sigmoid(o).

        lstm_cell = tf.contrib.rnn.BasicLSTMCell(
            num_units=self.num_lstm_units, state_is_tuple=True)

        with tf.variable_scope("lstm", initializer=self.initializer) as lstm_scope:
            # feed the image embeddings to set the initial LSTM state
            zero_state = lstm_cell.zero_state(
                batch_size=self.image_embeddings.get_shape()[0], dtype=tf.float32)
            _, initial_state = lstm_cell(self.image_embeddings, zero_state)

            # allow the LSTM variables to be reused
            lstm_scope.reuse_variables()

            # because this is inference mode,
            # use concatenated states for convenient feeding and fetching
            tf.concat(axis=1, values=initial_state, name="initial_state")

            # placeholder for feeding a batch of concatenated states
            state_feed = tf.placeholder(dtype=tf.float32,
                                        shape=[None, sum(lstm_cell.state_size)],
                                        name="state_feed")
            state_tuple = tf.split(value=state_feed, num_or_size_splits=2, axis=1)

            # run a single LSTM step
            lstm_outputs, state_tuple = lstm_cell(
                inputs=tf.squeeze(self.seq_embeddings, axis=[1]),
                state=state_tuple)

            # concatentate the resulting state
            tf.concat(axis=1, values=state_tuple, name="state")

        # stack batches vertically
        lstm_outputs = tf.reshape(lstm_outputs, [-1, lstm_cell.output_size])

        with tf.variable_scope("logits") as logits_scope:
            logits = tf.contrib.layers.fully_connected(
                inputs=lstm_outputs,
                num_outputs=self.vocab_size,
                activation_fn=None,
                weights_initializer=self.initializer,
                scope=logits_scope)

        tf.nn.softmax(logits, name="softmax")

    def setup_global_step(self):
        """Sets up the global step Tensor"""

        global_step = tf.Variable(
            initial_value=0,
            name="global_step",
            trainable=False,
            collections=[tf.GraphKeys.GLOBAL_STEP, tf.GraphKeys.GLOBAL_VARIABLES])

        self.global_step = global_step

    def build(self):
        self.build_inputs()
        self.build_image_embeddings()
        self.build_seq_embeddings()
        self.build_model()
        self.setup_global_step()
