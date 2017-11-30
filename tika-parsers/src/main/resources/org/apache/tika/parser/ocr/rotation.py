"""
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
"""

from __future__ import division, print_function
import numpy
from skimage.transform import radon
from PIL import Image
from numpy import asarray, mean, array, blackman
from numpy.fft import rfft
import matplotlib.pyplot as plt
from matplotlib.mlab import rms_flat

import sys
import getopt

def main(argv):
	filename = ''
	
	if len(sys.argv) < 3:
		print('Usage: rotation.py -f <filename>')
		sys.exit()
	try:
	  opts, args = getopt.getopt(argv,"hf:",["file="])
	except getopt.GetoptError:
	  print('rotation.py -f <filename>')
	  sys.exit(2)
	for opt, arg in opts:
	  if opt == '-h':
	     print('Usage: rotation.py -f <filename>')
	     sys.exit()
	  elif opt in ("-f", "--file"):
	     filename = arg

	try:
	  from parabolic import parabolic

	  def argmax(x):
	   	return parabolic(x, numpy.argmax(x))[0]
	except ImportError:
	  from numpy import argmax

	# Load file, converting to grayscale
	I = asarray(Image.open(filename).convert('L'))
	I = I - mean(I)  # Demean; make the brightness extend above and below zero

	# Do the radon transform and display the result
	sinogram = radon(I)

	# Find the RMS value of each row and find "busiest" rotation,
	# where the transform is lined up perfectly with the alternating dark
	# text and white lines
	r = array([rms_flat(line) for line in sinogram.transpose()])
	rotation = argmax(r)

	print('{:.2f}'.format(-(90-rotation)))

if __name__ == "__main__":
	main(sys.argv[1:])
