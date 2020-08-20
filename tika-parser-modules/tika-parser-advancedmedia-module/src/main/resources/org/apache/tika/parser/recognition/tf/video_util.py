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

import cv2
import ntpath
import numpy as np

print("cv2.__version__", cv2.__version__)

CV_FRAME_COUNT = None

if hasattr(cv2, "cv"):
    CV_FRAME_COUNT = cv2.cv.CV_CAP_PROP_FRAME_COUNT
else:
    CV_FRAME_COUNT = cv2.CAP_PROP_FRAME_COUNT


def _get_image_from_array(image_array):
    # JPG to support tensorflow
    byte_arr = cv2.imencode(".jpg", image_array)[1]
    return "".join(map(chr, byte_arr))


def _path_leaf(path):
    """
    Returns file name from path. Path should not end with slash(/)
    """
    head, tail = ntpath.split(path)
    return tail or ntpath.basename(head)


def get_center_frame(video_path):
    """
    Traverse till half of video and saves center snapshot
    @param video_path: Path to video file on system
    """
    cap = cv2.VideoCapture(video_path)

    length = int(cap.get(CV_FRAME_COUNT))

    success, image = cap.read()
    count = 0

    while success and count < length / 2:
        success, image = cap.read()
        count += 1

    return _get_image_from_array(image)


def get_frames_interval(video_path, frame_interval):
    """
    Selects one frames after every frame_interval
    @param video_path: Path to video file on system
    @param frame_interval: Interval after which frame should be picked. If frame_interval=10 then every 10th frame will be extracted
    """
    cap = cv2.VideoCapture(video_path)

    length = int(cap.get(CV_FRAME_COUNT))

    success, image = cap.read()
    count = 0

    image_arr = []
    while success and count < length:
        success, image = cap.read()
        if count % frame_interval == 0:
            image = _get_image_from_array(image)
            image_arr.append(image)

        count += 1

    return image_arr


def get_n_frames(video_path, num_frame):
    """
    Get N frames equidistant to each other in a video
    @param video_path: Path to video file on system
    @param num_frame: Number of frames to be extracted from video. If num_frame=10 then 10 frames equally distant from each other will be extracted
    """
    cap = cv2.VideoCapture(video_path)

    length = int(cap.get(CV_FRAME_COUNT))

    op_frame_idx = set(np.linspace(0, length - 2, num_frame, dtype=int))

    success, image = cap.read()
    count = 0

    image_arr = []
    while success and count < length:
        success, image = cap.read()
        if success and count in op_frame_idx:
            image = _get_image_from_array(image)
            image_arr.append(image)

        count += 1

    return image_arr
