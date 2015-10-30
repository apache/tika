#!/usr/bin/env bash

echo "Getting OpenNLP NER models"
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin" -O ner-person.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-location.bin" -O ner-location.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-organization.bin" -O ner-organization.bin

# Additional 4
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-date.bin" -O ner-date.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-money.bin" -O ner-money.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-time.bin" -O ner-time.bin
wget "http://opennlp.sourceforge.net/models-1.5/en-ner-percentage.bin" -O ner-percentage.bin