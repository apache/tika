#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "Getting OpenNLP NER models"
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin" -O ner-person.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-location.bin" -O ner-location.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-organization.bin" -O ner-organization.bin

# Additional 4
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-date.bin" -O ner-date.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-money.bin" -O ner-money.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-time.bin" -O ner-time.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-percentage.bin" -O ner-percentage.bin