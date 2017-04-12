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

"""
Image classification with Inception.

This script exposes the tensorflow's inception classification service over REST API.

For more details, visit:
    https://tensorflow.org/tutorials/image_recognition/

Requirements :
  Flask
  tensorflow
  numpy
  requests
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

import requests
import json
json.encoder.FLOAT_REPR = lambda o: format(
    o, '.2f')  # JSON serialization of floats
from time import time


import tempfile
from video_util import get_center_frame, get_frames_interval, get_n_frames
import flask


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
tf.app.flags.DEFINE_integer('port', '8764', """Server PORT, default:8764""")
tf.app.flags.DEFINE_string('log', 'inception.log',
                           """Log file name, default: inception.log""")

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

    synset_list = [s.strip() for s in open(os.path.join(
        dest_directory, 'imagenet_lsvrc_2015_synsets.txt')).readlines()]
    num_synsets_in_ilsvrc = len(synset_list)
    assert num_synsets_in_ilsvrc == 1000

    synset_to_human_list = open(os.path.join(
        dest_directory, 'imagenet_metadata.txt')).readlines()
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
        util_download(
            'https://raw.githubusercontent.com/tensorflow/models/master/inception/inception/data/imagenet_lsvrc_2015_synsets.txt', dest_directory)
    if not tf.gfile.Exists(os.path.join(dest_directory, 'imagenet_metadata.txt')):
        util_download(
            'https://raw.githubusercontent.com/tensorflow/models/master/inception/inception/data/imagenet_metadata.txt', dest_directory)
    # pylint: enable=line-too-long


def current_time():
    """
        Returns current time in milli seconds
    """
    return int(1000 * time())


class Classifier(flask.Flask):
    '''
    Classifier Service class
    '''

    def __init__(self, name):
        super(Classifier, self).__init__(name)
        maybe_download_and_extract()
        import logging
        from logging.handlers import RotatingFileHandler
        file_handler = RotatingFileHandler(
            FLAGS.log, maxBytes=1024 * 1024 * 100, backupCount=20)
        file_handler.setLevel(logging.INFO)
        formatter = logging.Formatter(
            "%(asctime)s - %(name)s - %(levelname)s - %(message)s")
        file_handler.setFormatter(formatter)
        self.logger.addHandler(file_handler)
        self.names = imagenet.create_readable_names_for_imagenet_labels()
        self.image_size = inception.inception_v4.default_image_size

        self.image_str_placeholder = tf.placeholder(tf.string)
        image = tf.image.decode_jpeg(self.image_str_placeholder, channels=3)
        processed_image = inception_preprocessing.preprocess_image(
            image, self.image_size, self.image_size, is_training=False)
        processed_images = tf.expand_dims(processed_image, 0)
        # Create the model, use the default arg scope to configure the
        # batch norm parameters.
        with slim.arg_scope(inception.inception_v4_arg_scope()):
            logits, _ = inception.inception_v4(
                processed_images, num_classes=1001, is_training=False)
        self.probabilities = tf.nn.softmax(logits)

        dest_directory = FLAGS.model_dir
        init_fn = slim.assign_from_checkpoint_fn(
            os.path.join(dest_directory, 'inception_v4.ckpt'),
            slim.get_model_variables('InceptionV4'))

        self.sess = tf.Session()
        init_fn(self.sess)

    def classify(self, image_string, topk):
        eval_probabilities = self.sess.run(self.probabilities, feed_dict={
                                           self.image_str_placeholder: image_string})
        eval_probabilities = eval_probabilities[0, 0:]
        sorted_inds = [i[0] for i in sorted(
            enumerate(-eval_probabilities), key=lambda x:x[1])]

    def classify(self, image_data, topk):
        predictions = self.sess.run(self.softmax_tensor,
                                    {'DecodeJpeg/contents:0': image_data})
        predictions = np.squeeze(predictions)
        
        if topk:
            top_k = predictions.argsort()[-topk:][::-1]
        else :
            top_k = predictions.argsort()
        res = []
        for i in range(topk):
            index = sorted_inds[i]
            score = float(eval_probabilities[index])
            res.append((index, self.names[index], score))
        return res


from flask import Flask, request, abort, g, Response, jsonify
app = Classifier(__name__)


def get_remotefile(url, success=200, timeout=10):
    """
        Given HTTP URL, this api gets the content of it
        returns (Content-Type, image_content)
    """
    try:
        app.logger.info("GET: %s" % url)
        auth = None
        res = requests.get(url, stream=True, timeout=timeout, auth=auth)
        if res.status_code == success:
            return res.headers.get('Content-Type', 'application/octet-stream'), res.raw.data
    except:
        pass
    return None, None


@app.route("/")
def index():
    """
        The index page which provide information about other API end points
    """
    return """
    <div>
    <h1> Inception REST API </h1>
    <h3> The following API end points are valid </h3>
        <ul>
            <h4> Inception V4 </h4>
            <li> <code>/inception/v4/ping </code> - <br/>
                <b> Description : </b> checks availability of the service. returns "pong" with status 200 when it is available
            </li>
            <li> <code>/inception/v4/classify</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a classifier service that can classify images</td></tr>
                <tr><td></td> <td>Query Params : <br/>
                   <code>topk </code>: type = int : top classes to get; default : 10 <br/>
                   <code>human </code>: type = boolean : human readable class names; default : true <br/>
                 </td></tr>
                <tr><th align="left"> How to supply Image Content </th></tr>
                <tr><th align="left"> With HTTP GET : </th> <td>
                    Include a query parameter <code>url </code> which is an http url of JPEG image <br/>
                    Example: <code> curl "localhost:8764/inception/v4/classify?url=http://xyz.com/example.jpg"</code>
                </td></tr>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST JPEG image content as binary data in request body. <br/>
                    Example: <code> curl -X POST "localhost:8764/inception/v4/classify?topk=10&human=false" --data-binary @example.jpg </code>
                </td></tr>
                </table>
            </li>
            <li> <code>/inception/v3/classify/video</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a classifier service that can classify videos</td></tr>
                <tr><td></td> <td>Query Params : <br/>
                   <code>topk </code>: type = int : top classes to get; default : 10 <br/>
                   <code>human </code>: type = boolean : human readable class names; default : true <br/>
                   <code>mode </code>: options = <code>{"center", "interval", "fixed"}</code> : Modes of frame extraction; default : center <br/>
                    &emsp; <code>"center"</code> - Just one frame in center. <br/>
                    &emsp; <code>"interval"</code> - Extracts frames after fixed interval. <br/>
                    &emsp; <code>"fixed"</code> - Extract fixed number of frames.<br/>
                   <code>frame-interval </code>: type = int : Interval for frame extraction to be used with INTERVAL mode. If frame_interval=10 then every 10th frame will be extracted; default : 10 <br/>
                   <code>num-frame </code>: type = int : Number of frames to be extracted from video while using FIXED model. If num_frame=10 then 10 frames equally distant from each other will be extracted; default : 10 <br/>
      
                 </td></tr>
                <tr><th align="left"> How to supply Video Content </th></tr>
                <tr><th align="left"> With HTTP GET : </th> <td>
                    Include a query parameter <code>url </code> which is path on file system <br/>
                    Example: <code> curl "localhost:8764/inception/v3/classify/video?url=filesystem/path/to/video"</code><br/>
                </td></tr><br/>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST video content as binary data in request body. If video can be decoded by OpenCV it should be fine. It's tested on mp4 and avi on mac <br/>
                    Include a query parameter <code>ext </code>this extension is needed to tell OpenCV which decoder to use, default is ".mp4" </br>
                    Example: <code> curl -X POST "localhost:8764/inception/v3/classify?topk=10&human=false" --data-binary @example.mp4 </code>
                </td></tr>
                </table>
            </li>
        <ul>
    </div>
    """


@app.route("/inception/v4/ping", methods=["GET"])
def ping_pong():
    """API to do health check. If this says status code 200, then healthy
    """
    return "pong"


@app.route("/inception/v4/classify", methods=["GET", "POST"])
def classify_image():
    """
    API to classify images
    """
    st = current_time()
    topk = int(request.args.get("topk", "10"))
    human = request.args.get("human", "true").lower() in ("true", "1", "yes")
    if request.method == 'POST':
        image_data = request.get_data()
    else:
        url = request.args.get("url")
        c_type, image_data = get_remotefile(url)
        if not image_data:
            return flask.Response(status=400, response=jsonify(error="Couldnot HTTP GET %s" % url))
        if 'image/jpeg' not in c_type:
            return flask.Response(status=400, response=jsonify(error="Content of %s is not JPEG" % url))
    read_time = current_time() - st
    st = current_time()  # reset start time
    try:
        classes = app.classify(image_string=image_data, topk=topk)
    except Exception as e:
        app.logger.error(e)
        return Response(status=400, response=str(e))
    classids, classnames, confidence = zip(*classes)
    classifier_time = current_time() - st
    app.logger.info("Classifier time : %d" % classifier_time)
    res = {
        'classids': classids,
        'confidence': confidence,
        'time': {
            'read': read_time,
            'classification': classifier_time,
            'units': 'ms'
        }
    }
    if human:
        res['classnames'] = classnames
    return Response(response=json.dumps(res), status=200, mimetype="application/json")

CENTER = "center"
INTERVAL = "interval"
FIXED = "fixed"

ALLOWED_MODE = set([CENTER ,INTERVAL , FIXED])

@app.route("/inception/v3/classify/video", methods=["GET", "POST"])
def classify_video():
    """
    API to classify videos
    Request args -
     url - PATH of file
     topk - number of labels
     human - human readable or not
     mode - Modes of frame extraction {"center", "interval", "fixed"}
        "center" - Just one frame in center. <Default option>
        "interval" - Extracts frames after fixed interval. 
        "fixed" - Extract fixed number of frames.
     frame-interval - Interval for frame extraction to be used with INTERVAL mode. If frame_interval=10 then every 10th frame will be extracted.
     num-frame - Number of frames to be extracted from video while using FIXED model. If num_frame=10 then 10 frames equally distant from each other will be extracted
     
     ext - If video is sent in binary format, then ext is needed to tell OpenCV which decoder to use. eg ".mp4"
    """
    
    st = current_time()
    topk = int(request.args.get("topk", "10"))
    human = request.args.get("human", "true").lower() in ("true", "1", "yes")
    
    mode = request.args.get("mode", CENTER).lower()
    if mode not in ALLOWED_MODE:
        '''
        Throw invalid request error
        '''
        return flask.Response(status=400, response=jsonify(error="not a valid mode. Available mode %s" % str(ALLOWED_MODE)))
    
    frame_interval = int(request.args.get("frame-interval", "10"))
    num_frame = int(request.args.get("num-frame", "10"))
    
    if request.method == 'POST':
        video_data = request.get_data()
        ext = request.args.get("ext", ".mp4").lower()
        
        temp_file = tempfile.NamedTemporaryFile(suffix=ext)
        temp_file.file.write(video_data)
        temp_file.file.close()
        
        url = temp_file.name
        
    else:
        url = request.args.get("url")
    
    read_time = current_time() - st
    st = current_time() # reset start time
    
    if mode == CENTER:
        image_data_arr = [get_center_frame(url)]
    elif mode == INTERVAL:
        image_data_arr = get_frames_interval(url, frame_interval)
    else:
        image_data_arr = get_n_frames(url, num_frame)
    
    classes = []
    for image_data in image_data_arr:
        try:
            _classes = app.classify(image_data=image_data , topk=None)
        except Exception as e:
            app.logger.error(e)
            return Response(status=400, response=str(e))
        
        _classes.sort()
        if len(classes) == 0:
            classes = _classes
        else:
            for idx,_c in enumerate(_classes):
                c = list(classes[idx])
                c[2] += _c[2]
                classes[idx] = tuple(c)
                
    
    # avg out confidence score
    for idx,c in enumerate(classes):
        c = list(c)
        c[2] = c[2]/len(image_data_arr)
        
        classes[idx] = tuple(c)
    
    classes = sorted(classes, key=lambda tup: tup[2])[-topk:][::-1]

    classids, classnames, confidence = zip(*classes)
    
    
    classifier_time = current_time() - st
    app.logger.info("Classifier time : %d" % classifier_time)
    res = {
        'classids' : classids,
        'confidence': confidence,
        'time': {
            'read' : read_time,
            'classification': classifier_time,
            'units': 'ms'
        }
    }
    if human:
        res['classnames'] = classnames
    return Response(response=json.dumps(res), status=200, mimetype="application/json")

def main(_):
    if not app.debug:
        print("Serving on port %d" % FLAGS.port)
    app.run(host="0.0.0.0", port=FLAGS.port)

if __name__ == '__main__':
    tf.app.run()
