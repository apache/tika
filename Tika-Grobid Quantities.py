from collections import defaultdict
import tika
import json
import os
import sys
from tika import parser

arg1=str(sys.argv[1])

def printMap(tag,filename):
    if os.path.exists(filename):
        os.remove(filename)
    with open(filename,'a+') as fopen:
        json.dump(tag,fopen)
count=0

output='/Users/anirbanmishra/Downloads/GrobidQuantitiesOutput'

for root, dirs, files in os.walk(arg1):
    for file in files:
        count+=1
        print count
        path=''
        if(file!='.DS_Store'):
            count+=1
            print count
            path=os.path.join(root, file)
            tika.initVM()
            try:
                parsed = parser.from_file(path)
                print parsed
            except:
                continue
            if("content" in parsed.keys()):
                type=parsed.get("metadata").get("Content-Type")
                print type
                content=parsed["content"]
                if(content is not None and ('application/pdf' in type or 'application/xml' in type or 'text/plain' in type)):
                    p=os.popen('curl -G --data-urlencode'+' '+'"text='+content+'"'+' '+'localhost:8080/processQuantityText'+'>>'+' '+output+'/'+file+'.txt').read()
