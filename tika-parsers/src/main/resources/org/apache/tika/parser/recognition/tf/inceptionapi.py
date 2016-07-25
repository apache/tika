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
import requests
import json
json.encoder.FLOAT_REPR = lambda o: format(o, '.2f') # JSON serialization of floats
from time import time

import flask


FLAGS = tf.app.flags.FLAGS

# classify_image_graph_def.pb:
#   Binary representation of the GraphDef protocol buffer.
# imagenet_synset_to_human_label_map.txt:
#   Map from synset ID to a human readable string.
# imagenet_2012_challenge_label_map_proto.pbtxt:
#   Text representation of a protocol buffer mapping a label to synset ID.
tf.app.flags.DEFINE_string(
    'model_dir', '/tmp/imagenet',
    """Path to classify_image_graph_def.pb, """
    """imagenet_synset_to_human_label_map.txt, and """
    """imagenet_2012_challenge_label_map_proto.pbtxt.""")
tf.app.flags.DEFINE_integer('port', '8764', """Server PORT, default:8764""")
tf.app.flags.DEFINE_string('log', 'inception.log', """Log file name, default: inception.log""")

# pylint: disable=line-too-long
DATA_URL = 'http://download.tensorflow.org/models/image/imagenet/inception-2015-12-05.tgz'
# pylint: enable=line-too-long

class NodeLookup(object):
    """Converts integer node ID's to human readable labels."""

    def __init__(self,
                 label_lookup_path=None,
                 uid_lookup_path=None):
        if not label_lookup_path:
            label_lookup_path = os.path.join(
                FLAGS.model_dir, 'imagenet_2012_challenge_label_map_proto.pbtxt')
        if not uid_lookup_path:
            uid_lookup_path = os.path.join(
                FLAGS.model_dir, 'imagenet_synset_to_human_label_map.txt')
        self.node_lookup = self.load(label_lookup_path, uid_lookup_path)

    def load(self, label_lookup_path, uid_lookup_path):
        """Loads a human readable English name for each softmax node.

    Args:
      label_lookup_path: string UID to integer node ID.
      uid_lookup_path: string UID to human-readable string.

    Returns:
      dict from integer node ID to human-readable string.
    """
        if not tf.gfile.Exists(uid_lookup_path):
            tf.logging.fatal('File does not exist %s', uid_lookup_path)
        if not tf.gfile.Exists(label_lookup_path):
            tf.logging.fatal('File does not exist %s', label_lookup_path)

        # Loads mapping from string UID to human-readable string
        proto_as_ascii_lines = tf.gfile.GFile(uid_lookup_path).readlines()
        uid_to_human = {}
        p = re.compile(r'[n\d]*[ \S,]*')
        for line in proto_as_ascii_lines:
            parsed_items = p.findall(line)
            uid = parsed_items[0]
            human_string = parsed_items[2]
            uid_to_human[uid] = human_string

        # Loads mapping from string UID to integer node ID.
        node_id_to_uid = {}
        proto_as_ascii = tf.gfile.GFile(label_lookup_path).readlines()
        for line in proto_as_ascii:
            if line.startswith('  target_class:'):
                target_class = int(line.split(': ')[1])
            if line.startswith('  target_class_string:'):
                target_class_string = line.split(': ')[1]
                node_id_to_uid[target_class] = target_class_string[1:-2]

        # Loads the final mapping of integer node ID to human-readable string
        node_id_to_name = {}
        for key, val in node_id_to_uid.items():
            if val not in uid_to_human:
                tf.logging.fatal('Failed to locate: %s', val)
            name = uid_to_human[val]
            node_id_to_name[key] = name

        return node_id_to_name

    def id_to_string(self, node_id):
        if node_id not in self.node_lookup:
            return ''
        return self.node_lookup[node_id]

def create_graph():
    """Creates a graph from saved GraphDef file and returns a saver."""
    # Creates graph from saved graph_def.pb.
    with tf.gfile.FastGFile(os.path.join(
            FLAGS.model_dir, 'classify_image_graph_def.pb'), 'rb') as f:
        graph_def = tf.GraphDef()
        graph_def.ParseFromString(f.read())
        _ = tf.import_graph_def(graph_def, name='')


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
        create_graph()
        self.sess = tf.Session()
        self.softmax_tensor = self.sess.graph.get_tensor_by_name('softmax:0')
        self.node_lookup = NodeLookup()
        print("Logs are directed to %s" % FLAGS.log)
        import logging
        from logging.handlers import RotatingFileHandler
        file_handler = RotatingFileHandler(FLAGS.log, maxBytes=1024 * 1024 * 100, backupCount=20)
        file_handler.setLevel(logging.INFO)
        formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
        file_handler.setFormatter(formatter)
        self.logger.addHandler(file_handler)

    def classify(self, image_data, topk):
        predictions = self.sess.run(self.softmax_tensor,
                                    {'DecodeJpeg/contents:0': image_data})
        predictions = np.squeeze(predictions)
        top_k = predictions.argsort()[-topk:][::-1]
        res = []
        for node_id in top_k:
            class_name = self.node_lookup.id_to_string(node_id)
            score = float(predictions[node_id])
            res.append((node_id, class_name, score))
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
            <h4> Inception V3 </h4>
            <li> <code>/inception/v3/classes </code> - <br/>
                <b> Description : </b> This API gets all classes/object types known to the current model
            </li>
            <li> <code>/inception/v3/ping </code> - <br/>
                <b> Description : </b> checks availability of the service. returns "pong" with status 200 when it is available
            </li>
            <li> <code>/inception/v3/classify</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a classifier service that can classify images</td></tr>
                <tr><td></td> <td>Query Params : <br/>
                   <code>topk </code>: type = int : top classes to get; default : 10 <br/>
                   <code>human </code>: type = boolean : human readable class names; default : true <br/>
                 </td></tr>
                <tr><th align="left"> How to supply Image Content </th></tr>
                <tr><th align="left"> With HTTP GET : </th> <td>
                    Include a query parameter <code>url </code> which is an http url of JPEG image <br/>
                    Example: <code> curl "localhost:8764/inception/v3/classify?url=http://xyz.com/example.jpg"</code>
                </td></tr>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST JPEG image content as binary data in request body. <br/>
                    Example: <code> curl -X POST "localhost:8764/inception/v3/classify?topk=10&human=false" --data-binary @example.jpg </code>
                </td></tr>
                </table>
            </li>
        <ul>
    </div>
    """

@app.route("/inception/v3/classes", methods=["GET"])
def get_classes():
    """API to list all known classes
    """
    return jsonify(app.node_lookup.node_lookup)

@app.route("/inception/v3/ping", methods=["GET"])
def ping_pong():
    """API to do health check. If this says status code 200, then healthy
    """
    return "pong"

@app.route("/inception/v3/classify", methods=["GET", "POST"])
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
    st = current_time() # reset start time
    try:
        classes = app.classify(image_data=image_data, topk=topk)
    except Exception as e:
        app.logger.error(e)
        return Response(status=400, response=str(e))
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
    app.run(port=FLAGS.port)

if __name__ == '__main__':
    tf.app.run()
