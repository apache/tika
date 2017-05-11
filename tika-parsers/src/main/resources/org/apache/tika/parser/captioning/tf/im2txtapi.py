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
    This script exposes image captioning service over a REST API. Image captioning implementation based on the paper,

        "Show and Tell: A Neural Image Caption Generator"
        Oriol Vinyals, Alexander Toshev, Samy Bengio, Dumitru Erhan

    For more details, please visit :
        http://arxiv.org/abs/1411.4555
    Requirements :
      Flask
      tensorflow
      numpy
      requests
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import math
import requests
import xml.etree.ElementTree as ET
from time import time

import flask
import tensorflow as tf

import model_wrapper
import vocabulary
import caption_generator

info = ET.parse('/usr/local/models/model_info.xml').getroot()
model_main = info.find('model_main')

checkpoint_path = model_main.find('checkpoint_path').text
vocab_file = model_main.find('vocab_file').text
port = info.get('port')

FLAGS = tf.flags.FLAGS
tf.flags.DEFINE_string("checkpoint_path", checkpoint_path, """Directory containing the model checkpoint file.""")
tf.flags.DEFINE_string('vocab_file', vocab_file, """Text file containing the vocabulary.""")
tf.flags.DEFINE_integer('port', port, """Server PORT, default:8764""")

tf.logging.set_verbosity(tf.logging.INFO)


class Initializer(flask.Flask):
    """
        Class to initialize the REST API, this class loads the model from the given checkpoint path in model_info.xml
        and prepares a caption_generator object
    """

    def __init__(self, name):
        super(Initializer, self).__init__(name)
        # build the inference graph
        g = tf.Graph()
        with g.as_default():
            model = model_wrapper.ModelWrapper()
            restore_fn = model.build_graph(FLAGS.checkpoint_path)
        g.finalize()

        # make the model globally available
        self.model = model

        # create the vocabulary
        self.vocab = vocabulary.Vocabulary(FLAGS.vocab_file)

        self.sess = tf.Session(graph=g)
        # load the model from checkpoint
        restore_fn(self.sess)


def current_time():
    """Returns current time in milli seconds"""

    return int(1000 * time())


from flask import request, Response, jsonify
import json

app = Initializer(__name__)


def get_remote_file(url, success=200, timeout=10):
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
    """The index page which provide information about other API end points"""

    return """
    <div>
    <h1> Image Captioning REST API </h1>
    <h3> The following API end points are valid </h3>
        <ul>
            <h4> Inception V3 </h4>
            <li> <code>/ping </code> - <br/>
                <b> Description : </b> checks availability of the service. returns "pong" with status 200 when it is available
            </li>
            <li> <code>/getcaptions</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a service that can caption images</td></tr>
                <tr><th align="left"> How to supply Image Content </th></tr>
                <tr><th align="left"> With HTTP GET : </th> <td>
                    Include a query parameter <code>url </code> which is an http url of JPEG image <br/>
                    Example: <code> curl "localhost:8764/getcaptions?url=http://xyz.com/example.jpg"</code>
                </td></tr>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST JPEG image content as binary data in request body. <br/>
                    Example: <code> curl -X POST "localhost:8764/getcaptions" --data-binary @example.jpg </code>
                </td></tr>
                </table>
            </li>
        <ul>
    </div>
    """


@app.route("/ping", methods=["GET"])
def ping_pong():
    """API to do health check. If this says status code 200, then healthy"""

    return "pong"


@app.route("/getcaptions", methods=["GET", "POST"])
def get_captions():
    """API to caption images"""

    st = current_time()
    # get beam_size
    beam_size = int(request.args.get("beam_size", "3"))
    # get max_caption_length
    max_caption_length = int(request.args.get("max_caption_length", "20"))
    # get image_data
    if request.method == 'POST':
        image_data = request.get_data()
    else:
        url = request.args.get("url")
        c_type, image_data = get_remote_file(url)
        if not image_data:
            return flask.Response(status=400, response=jsonify(error="Could not HTTP GET %s" % url))
        if 'image/jpeg' not in c_type:
            return flask.Response(status=400, response=jsonify(error="Content of %s is not JPEG" % url))
    read_time = current_time() - st

    # restart counter
    st = current_time()

    generator = caption_generator.CaptionGenerator(app.model,
                                                   app.vocab,
                                                   beam_size=beam_size,
                                                   max_caption_length=max_caption_length)
    captions = generator.beam_search(app.sess, image_data)

    captioning_time = current_time() - st
    app.logger.info("Captioning time : %d" % captioning_time)

    array_captions = []
    for caption in captions:
        sentence = [app.vocab.id_to_word(w) for w in caption.sentence[1:-1]]
        sentence = " ".join(sentence)
        array_captions.append({
            'sentence': sentence,
            'confidence': math.exp(caption.logprob)
        })

    response = {
        'beam_size': beam_size,
        'max_caption_length': max_caption_length,
        'captions': array_captions,
        'time': {
            'read': read_time,
            'captioning': captioning_time,
            'units': 'ms'
        }
    }

    return Response(response=json.dumps(response), status=200, mimetype="application/json")


def main(_):
    if not app.debug:
        print("Serving on port %d" % FLAGS.port)
    app.run(host="0.0.0.0", port=FLAGS.port)


if __name__ == '__main__':
    tf.app.run()
