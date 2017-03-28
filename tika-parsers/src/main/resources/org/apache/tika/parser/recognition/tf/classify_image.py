# Copyright 2015 The TensorFlow Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================

"""Simple image classification with Inception.

Run image classification with Inception trained on ImageNet 2012 Challenge data
set.

This program creates a graph from a saved GraphDef protocol buffer,
and runs inference on an input JPEG image. It outputs human readable
strings of the top 5 predictions along with their probabilities.

Change the --image_file argument to any jpg image to compute a
classification of that image.

Please see the tutorial and website for a detailed description of how
to use this script to perform image recognition.

https://tensorflow.org/tutorials/image_recognition/
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os.path
import re
import sys
import tarfile
from PIL import Image

import numpy as np
from six.moves import urllib
import tensorflow as tf
slim = tf.contrib.slim

from inception_v4 import *

#For converting class_id into human readable string.
import imagenet

FLAGS = tf.app.flags.FLAGS

# inception_v4.ckpt:
#   Checkpoint for Inception v4 model dated 09/09/2016

tf.app.flags.DEFINE_string(
    'model_dir', './tf-objectrec-model',
    """Path to checkpoint file of Inception v4.""")
tf.app.flags.DEFINE_string('image_file', '',
                           """Absolute path to image file.""")

# pylint: disable=line-too-long
DATA_URL = 'http://download.tensorflow.org/models/inception_v4_2016_09_09.tar.gz'
# pylint: enable=line-too-long


def run_inference_on_image(image):
  """Runs inference on an image.

  Args:
    image: Image file name.

  Returns:
    Nothing
  """
  if not tf.gfile.Exists(image):
    tf.logging.fatal('File does not exist %s', image)
  
  checkpoint_file = os.path.join(FLAGS.model_dir, 'inception_v4.ckpt')
  
  #Load the model
  sess = tf.Session()
  arg_scope = inception_v4_arg_scope()
  
  #For Inception_v4, the default image height and width is 299. Number of channels is 3
  image_size = inception_v4.default_image_size
  input_tensor = tf.placeholder(tf.float32, [None, image_size, image_size, 3])

  #Scale the input tensor
  scaled_input_tensor = tf.scalar_mul((1.0/255), input_tensor)
  scaled_input_tensor = tf.subtract(scaled_input_tensor, 0.5)
  scaled_input_tensor = tf.multiply(scaled_input_tensor, 2.0)


  with slim.arg_scope(arg_scope):
    logits, end_points = inception_v4(scaled_input_tensor, is_training=False)
  saver = tf.train.Saver()
  saver.restore(sess, checkpoint_file)
  
  #Resize and preprocess the image.
  im = Image.open(image).resize((image_size,image_size))
  im = np.array(im)
  im = im.reshape(-1,299,299,3)
  
  #Feed the preprocessed image  
  predict_values, logit_values = sess.run([end_points['Predictions'], logits], feed_dict={input_tensor: im})
  
  #Sort the classes based on probabilities. Top 5 are selected and printed.
  probabilities = predict_values[0, 0:]
  sorted_inds = [i[0] for i in sorted(enumerate(-probabilities),
                                            key=lambda x:x[1])]
  
  names = imagenet.create_readable_names_for_imagenet_labels()
  for i in range(5):
    index = sorted_inds[i]
    human_string = names[index]
    score = probabilities[index]
    print('%s (score = %.5f)' % (human_string, score))


def maybe_download_and_extract():
  """Download and extract model tar file."""
  dest_directory = FLAGS.model_dir
  if not os.path.exists(dest_directory):
    os.makedirs(dest_directory)
  filename = DATA_URL.split('/')[-1]
  filepath = os.path.join(dest_directory, filename)
  if not os.path.exists(filepath):
    def _progress(count, block_size, total_size):
      sys.stdout.write('\r>> Downloading %s %.1f%%' % (
          filename, float(count * block_size) / float(total_size) * 100.0))
      sys.stdout.flush()
    filepath, _ = urllib.request.urlretrieve(DATA_URL, filepath, _progress)
    print()
    statinfo = os.stat(filepath)
    print('Succesfully downloaded', filename, statinfo.st_size, 'bytes.')
  tarfile.open(filepath, 'r:gz').extractall(dest_directory)


def main(_):
  maybe_download_and_extract()
  image = (FLAGS.image_file if FLAGS.image_file else
           os.path.join(FLAGS.model_dir, 'cropped_panda.jpg'))  
  run_inference_on_image(image)


if __name__ == '__main__':
  tf.app.run()