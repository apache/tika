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

import numpy as np
from six.moves import urllib
import tensorflow as tf

from datasets import imagenet, dataset_utils
from nets import inception
from preprocessing import inception_preprocessing

slim = tf.contrib.slim

FLAGS = tf.app.flags.FLAGS

# inception_v4.ckpt
#   Inception V4 checkpoint file.
# imagenet_metadata.txt
#   Map from synset ID to a human readable string.
# imagenet_lsvrc_2015_synsets.txt
#   Text representation of a protocol buffer mapping a label to synset ID.
tf.app.flags.DEFINE_string(
    'model_dir', '/tmp/imagenet',
    """Path to inception_v4.ckpt, """
    """imagenet_lsvrc_2015_synsets.txt, and """
    """imagenet_metadata.txt.""")
tf.app.flags.DEFINE_string('image_file', '',
                           """Absolute path to image file.""")
tf.app.flags.DEFINE_integer('num_top_predictions', 5,
                            """Display this many predictions.""")

# pylint: disable=line-too-long
DATA_URL = 'http://download.tensorflow.org/models/inception_v4_2016_09_09.tar.gz'
# pylint: enable=line-too-long

def create_readable_names_for_imagenet_labels():
  """Create a dict mapping label id to human readable string.

  Returns:
      labels_to_names: dictionary where keys are integers from to 1000
      and values are human-readable names.

  We retrieve a synset file, which contains a list of valid synset labels used
  by ILSVRC competition. There is one synset one per line, eg.
          #   n01440764
          #   n01443537
  We also retrieve a synset_to_human_file, which contains a mapping from synsets
  to human-readable names for every synset in Imagenet. These are stored in a
  tsv format, as follows:
          #   n02119247    black fox
          #   n02119359    silver fox
  We assign each synset (in alphabetical order) an integer, starting from 1
  (since 0 is reserved for the background class).

  Code is based on
  https://github.com/tensorflow/models/blob/master/inception/inception/data/build_imagenet_data.py#L463
  """

  # pylint: disable=line-too-long
  
  dest_directory = FLAGS.model_dir

  synset_list = [s.strip() for s in open(os.path.join(dest_directory, 'imagenet_lsvrc_2015_synsets.txt')).readlines()]
  num_synsets_in_ilsvrc = len(synset_list)
  assert num_synsets_in_ilsvrc == 1000

  synset_to_human_list = open(os.path.join(dest_directory, 'imagenet_metadata.txt')).readlines()
  num_synsets_in_all_imagenet = len(synset_to_human_list)
  assert num_synsets_in_all_imagenet == 21842

  synset_to_human = {}
  for s in synset_to_human_list:
    parts = s.strip().split('\t')
    assert len(parts) == 2
    synset = parts[0]
    human = parts[1]
    synset_to_human[synset] = human

  label_index = 1
  labels_to_names = {0: 'background'}
  for synset in synset_list:
    name = synset_to_human[synset]
    labels_to_names[label_index] = name
    label_index += 1

  return labels_to_names

def run_inference_on_image(image):
  """Runs inference on an image.

  Args:
    image: Image file name.

  Returns:
    Nothing
  """
  dest_directory = FLAGS.model_dir

  image_size = inception.inception_v4.default_image_size

  if not tf.gfile.Exists(image):
    tf.logging.fatal('File does not exist %s', image)
  image_string = tf.gfile.FastGFile(image, 'rb').read()

  with tf.Graph().as_default():
    image = tf.image.decode_jpeg(image_string, channels=3)
    processed_image = inception_preprocessing.preprocess_image(image, image_size, image_size, is_training=False)
    processed_images  = tf.expand_dims(processed_image, 0)
    
    # Create the model, use the default arg scope to configure the batch norm parameters.
    with slim.arg_scope(inception.inception_v4_arg_scope()):
        logits, _ = inception.inception_v4(processed_images, num_classes=1001, is_training=False)
    probabilities = tf.nn.softmax(logits)
    
    init_fn = slim.assign_from_checkpoint_fn(
        os.path.join(dest_directory, 'inception_v4.ckpt'),
        slim.get_model_variables('InceptionV4'))
    
    with tf.Session() as sess:
        init_fn(sess)
        np_image, probabilities = sess.run([image, probabilities])
        probabilities = probabilities[0, 0:]
        sorted_inds = [i[0] for i in sorted(enumerate(-probabilities), key=lambda x:x[1])]
        
    names = create_readable_names_for_imagenet_labels()
    top_k = FLAGS.num_top_predictions
    for i in range(top_k):
        index = sorted_inds[i]
        print('%s (score = %.5f)' % (names[index], probabilities[index]))


def util_download(url, dest_directory):
  """Downloads the file.

  Args:
    url: URL to download the file from.
    dest_directory: Destination directory
  Returns:
    Nothing
  """
  filename = url.split('/')[-1]
  filepath = os.path.join(dest_directory, filename)

  def _progress(count, block_size, total_size):
    sys.stdout.write('\r>> Downloading %s %.1f%%' % (
        filename, float(count * block_size) / float(total_size) * 100.0))
    sys.stdout.flush()
  filepath, _ = urllib.request.urlretrieve(url, filepath, _progress)
  print()
  statinfo = os.stat(filepath)
  print('Successfully downloaded', filename, statinfo.st_size, 'bytes.')

def util_download_tar(url, dest_directory):
  """Downloads a file and extracts it.

  Args:
    url: URL to download the file from.
    dest_directory: Destination directory
  Returns:
    Nothing
  """
  filename = url.split('/')[-1]
  filepath = os.path.join(dest_directory, filename)

  def _progress(count, block_size, total_size):
    sys.stdout.write('\r>> Downloading %s %.1f%%' % (
        filename, float(count * block_size) / float(total_size) * 100.0))
    sys.stdout.flush()
  filepath, _ = urllib.request.urlretrieve(url, filepath, _progress)
  print()
  statinfo = os.stat(filepath)
  print('Successfully downloaded', filename, statinfo.st_size, 'bytes.')
  tarfile.open(filepath, 'r:gz').extractall(dest_directory)


def maybe_download_and_extract():
  """Download and extract model tar file."""
  dest_directory = FLAGS.model_dir
  if not tf.gfile.Exists(dest_directory):
    tf.gfile.MakeDirs(dest_directory)
  if not tf.gfile.Exists(os.path.join(dest_directory, 'inception_v4.ckpt')):
    util_download_tar(DATA_URL, dest_directory)
  # pylint: disable=line-too-long
  if not tf.gfile.Exists(os.path.join(dest_directory, 'imagenet_lsvrc_2015_synsets.txt')):
    util_download('https://raw.githubusercontent.com/tensorflow/models/master/inception/inception/data/imagenet_lsvrc_2015_synsets.txt', dest_directory)
  if not tf.gfile.Exists(os.path.join(dest_directory, 'imagenet_metadata.txt')):
    util_download('https://raw.githubusercontent.com/tensorflow/models/master/inception/inception/data/imagenet_metadata.txt', dest_directory)
  # pylint: enable=line-too-long



def main(_):
  maybe_download_and_extract()
  image = FLAGS.image_file
  run_inference_on_image(image)


if __name__ == '__main__':
  tf.app.run()
