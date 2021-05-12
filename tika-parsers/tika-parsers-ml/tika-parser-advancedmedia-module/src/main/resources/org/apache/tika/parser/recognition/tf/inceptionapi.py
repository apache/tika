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
      pillow
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os
import tempfile
import json
import logging
import requests

from flask import Flask, request, Response, jsonify
from io import BytesIO
from logging.handlers import RotatingFileHandler
from PIL import Image
from time import time

import tensorflow as tf

from inception_v4 import default_image_size, inception_v4_arg_scope, inception_v4

try:
    # This import is placed inside here to ensure that video_util and OpenCV is not required for image recognition APIs
    from video_util import get_center_frame, get_frames_interval, get_n_frames
except:
    print("Can't import video libraries, No video functionality is available")

json.encoder.FLOAT_REPR = lambda o: format(o, '.2f')  # JSON serialization of floats
slim = tf.contrib.slim
FLAGS = tf.app.flags.FLAGS

tf.app.flags.DEFINE_string('model_dir',
                           '/usr/share/apache-tika/models/dl/image-video/recognition/',
                           """Path to inception_v4.ckpt & meta files""")
tf.app.flags.DEFINE_integer('port',
                            '8764',
                            """Server PORT, default:8764""")
tf.app.flags.DEFINE_string('log',
                           'inception.log',
                           """Log file name, default: inception.log""")


def preprocess_image(image, height, width, central_fraction=0.875, scope=None):
    """Prepare one image for evaluation.
    If height and width are specified it would output an image with that size by
    applying resize_bilinear.
    If central_fraction is specified it would crop the central fraction of the
    input image.
    Args:
      image: 3-D Tensor of image. If dtype is tf.float32 then the range should be
        [0, 1], otherwise it would converted to tf.float32 assuming that the range
        is [0, MAX], where MAX is largest positive representable number for
        int(8/16/32) data type (see `tf.image.convert_image_dtype` for details).
      height: integer
      width: integer
      central_fraction: Optional Float, fraction of the image to crop.
      scope: Optional scope for name_scope.
    Returns:
      3-D float Tensor of prepared image.
    """
    with tf.name_scope(scope, 'eval_image', [image, height, width]):
        if image.dtype != tf.float32:
            image = tf.image.convert_image_dtype(image, dtype=tf.float32)
        # Crop the central region of the image with an area containing 87.5% of
        # the original image.
        if central_fraction:
            image = tf.image.central_crop(image, central_fraction=central_fraction)

        if height and width:
            # Resize the image to the specified height and width.
            image = tf.expand_dims(image, 0)
            image = tf.image.resize_bilinear(image, [height, width],
                                             align_corners=False)
            image = tf.squeeze(image, [0])
        image = tf.subtract(image, 0.5)
        image = tf.multiply(image, 2.0)
        return image


def create_readable_names_for_imagenet_labels():
    """
        Create a dict mapping label id to human readable string.
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
        https://github.com/tensorflow/models/blob/master/inception/inception/data/build_imagenet_data.py
    """

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


def current_time():
    """Returns current time in milli seconds"""

    return int(1000 * time())


class Classifier(Flask):
    """Classifier Service class"""

    def __init__(self, name):
        super(Classifier, self).__init__(name)
        file_handler = RotatingFileHandler(FLAGS.log, maxBytes=1024 * 1024 * 100, backupCount=20)
        file_handler.setLevel(logging.INFO)
        formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
        file_handler.setFormatter(formatter)
        self.logger.addHandler(file_handler)
        self.names = create_readable_names_for_imagenet_labels()
        self.image_size = default_image_size

        self.image_str_placeholder = tf.placeholder(tf.string)
        image = tf.image.decode_jpeg(self.image_str_placeholder, channels=3)
        processed_image = preprocess_image(image, self.image_size, self.image_size)
        processed_images = tf.expand_dims(processed_image, 0)
        # create the model, use the default arg scope to configure the batch norm parameters.
        with slim.arg_scope(inception_v4_arg_scope()):
            logits, _ = inception_v4(processed_images, num_classes=1001, is_training=False)
        self.probabilities = tf.nn.softmax(logits)

        dest_directory = FLAGS.model_dir
        init_fn = slim.assign_from_checkpoint_fn(
            os.path.join(dest_directory, 'inception_v4.ckpt'),
            slim.get_model_variables('InceptionV4'))

        self.sess = tf.Session()
        init_fn(self.sess)

    def classify(self, image_string, topn, min_confidence):
        eval_probabilities = self.sess.run(self.probabilities, feed_dict={self.image_str_placeholder: image_string})
        eval_probabilities = eval_probabilities[0, 0:]
        sorted_inds = [i[0] for i in sorted(enumerate(-eval_probabilities), key=lambda x: x[1])]

        if topn is None:
            topn = len(sorted_inds)

        res = []
        for i in range(topn):
            index = sorted_inds[i]
            score = float(eval_probabilities[index])
            if min_confidence is None:
                res.append((index, self.names[index], score))
            else:
                if score >= min_confidence:
                    res.append((index, self.names[index], score))
                else:
                    # the scores are in sorted order, so we can break the loop whenever we get a low score object
                    break
        return res


app = Classifier(__name__)


@app.route("/")
def index():
    """The index page which provide information about other API end points"""

    return """
    <div>
    <h1> Inception REST API </h1>
    <h3> The following API end points are valid </h3>
        <ul>
            <h4> Inception V4 </h4>
            <li> <code>/inception/v4/ping </code> - <br/>
                <b> Description : </b> checks availability of the service. returns "pong" with status 200 when it is available
            </li>
            <li> <code>/inception/v4/classify/image</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a classifier service that can classify images</td></tr>
                <tr><td></td> <td>Query Params : <br/>
                   <code>topn </code>: type = int : top classes to get; default : 5 <br/>
                   <code>min_confidence </code>: type = float : minimum confidence that a label should have to exist in topn; default : 0.015 <br/>
                   <code>human </code>: type = boolean : human readable class names; default : true <br/>
                 </td></tr>
                <tr><th align="left"> How to supply Image Content </th></tr>
                <tr><th align="left"> With HTTP GET : </th> <td>
                    Include a query parameter <code>url </code> which is an http url of JPEG image <br/>
                    Example: <code> curl "localhost:8764/inception/v4/classify/image?url=http://xyz.com/example.jpg"</code>
                </td></tr>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST JPEG image content as binary data in request body. <br/>
                    Example: <code> curl -X POST "localhost:8764/inception/v4/classify/image?topn=5&min_confidence=0.015&human=false" --data-binary @example.jpg </code>
                </td></tr>
                </table>
            </li>
            <li> <code>/inception/v4/classify/video</code> - <br/>
                <table>
                <tr><th align="left"> Description </th><td> This is a classifier service that can classify videos</td></tr>
                <tr><td></td> <td>Query Params : <br/>
                   <code>topn </code>: type = int : top classes to get; default : 5 <br/>
                   <code>min_confidence </code>: type = float : minimum confidence that a label should have to exist in topn; default : 0.015 <br/>
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
                    Example: <code> curl "localhost:8764/inception/v4/classify/video?url=filesystem/path/to/video"</code><br/>
                </td></tr><br/>
                <tr><th align="left"> With HTTP POST :</th><td>
                    POST video content as binary data in request body. If video can be decoded by OpenCV it should be fine. It's tested on mp4 and avi on mac <br/>
                    Include a query parameter <code>ext </code>this extension is needed to tell OpenCV which decoder to use, default is ".mp4" </br>
                    Example: <code> curl -X POST "localhost:8764/inception/v4/classify/video?topn=5&min_confidence=0.015&human=false" --data-binary @example.mp4 </code>
                </td></tr>
                </table>
            </li>
        <ul>
    </div>
    """


@app.route("/inception/v4/ping", methods=["GET"])
def ping_pong():
    """API to do health check. If this says status code 200, then healthy"""

    return "pong"


@app.route("/inception/v4/classify/image", methods=["GET", "POST"])
def classify_image():
    """API to classify images"""

    image_format = "not jpeg"

    st = current_time()
    topn = int(request.args.get("topn", "5"))
    min_confidence = float(request.args.get("min_confidence", "0.015"))
    human = request.args.get("human", "true").lower() in ("true", "1", "yes")
    if request.method == 'POST':
        image_data = request.get_data()
    else:
        url = request.args.get("url")
        c_type, image_data = get_remote_file(url)
        if not image_data:
            return Response(status=400, response=jsonify(error="Could not HTTP GET %s" % url))
        if 'image/jpeg' in c_type:
            image_format = "jpeg"

    # use c_type to find whether image_format is jpeg or not
    # if jpeg, don't convert
    if image_format == "jpeg":
        jpg_image = image_data
    # if not jpeg
    else:
        # open the image from raw bytes
        image = Image.open(BytesIO(image_data))
        # convert the image to RGB format, otherwise will give errors when converting to jpeg, if the image isn't RGB
        rgb_image = image.convert("RGB")
        # convert the RGB image to jpeg
        image_bytes = BytesIO()
        rgb_image.save(image_bytes, format="jpeg", quality=95)
        jpg_image = image_bytes.getvalue()
        image_bytes.close()

    read_time = current_time() - st
    st = current_time()  # reset start time
    try:
        classes = app.classify(image_string=jpg_image, topn=topn, min_confidence=min_confidence)
    except Exception as e:
        app.logger.error(e)
        return Response(status=400, response=str(e))
    classids, classnames, confidence = zip(*classes)

    print(classnames, confidence)

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


@app.route("/inception/v4/classify/video", methods=["GET", "POST"])
def classify_video():
    """
        API to classify videos
        Request args -
         url - PATH of file
         topn - number of top scoring labels
         min_confidence - minimum confidence that a label should have to exist in topn
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
    topn = int(request.args.get("topn", "5"))
    min_confidence = float(request.args.get("min_confidence", "0.015"))
    human = request.args.get("human", "true").lower() in ("true", "1", "yes")

    mode = request.args.get("mode", "center").lower()
    if mode not in {"center", "interval", "fixed"}:
        '''
        Throw invalid request error
        '''
        return Response(status=400, response=jsonify(error="not a valid mode. Available mode %s" % str(ALLOWED_MODE)))

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
    st = current_time()  # reset start time

    if mode == "center":
        image_data_arr = [get_center_frame(url)]
    elif mode == "interval":
        image_data_arr = get_frames_interval(url, frame_interval)
    else:
        image_data_arr = get_n_frames(url, num_frame)

    classes = []
    for image_data in image_data_arr:
        try:
            _classes = app.classify(image_data, topn=None, min_confidence=None)
        except Exception as e:
            app.logger.error(e)
            return Response(status=400, response=str(e))

        _classes.sort()
        if len(classes) == 0:
            classes = _classes
        else:
            for idx, _c in enumerate(_classes):
                c = list(classes[idx])
                c[2] += _c[2]
                classes[idx] = tuple(c)

    top_classes = []
    for c in classes:
        c = list(c)
        # avg out confidence score
        avg_score = c[2] / len(image_data_arr)
        c[2] = avg_score
        if avg_score >= min_confidence:
            top_classes.append(tuple(c))

    top_classes = sorted(top_classes, key=lambda tup: tup[2])[-topn:][::-1]

    classids, classnames, confidence = zip(*top_classes)

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


def main(_):
    if not app.debug:
        print("Serving on port %d" % FLAGS.port)
    app.run(host="0.0.0.0", port=FLAGS.port)


if __name__ == '__main__':
    tf.app.run()
