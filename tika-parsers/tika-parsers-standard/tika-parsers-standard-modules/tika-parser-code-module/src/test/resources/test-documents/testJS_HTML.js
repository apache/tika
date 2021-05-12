/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
var places = new Array();

places[0] = {
   'name': 'Oxford', lat: 51.75222, lng: -1.25596,
   'id': 'map_1',
}
places[1] = {
   'name': 'Oxford', lat: 41.43399, lng: -73.11678,
   'id': 'map_2',
}
places[2] = {
   'name': 'Oxford', lat: -43.3, lng: 172.18333,
   'id': 'map_3',
}
places[3] = {
   'name': 'Oxford', lat: 33.619, lng: -83.86741,
   'id': 'map_4',
}
places[4] = {
   'name': 'Oxford', lat: 44.13174, lng: -70.49311,
   'id': 'map_5',
}
places[5] = {
   'name': 'Oxford', lat: 39.78539, lng: -75.97883,
   'id': 'map_6',
}
places[6] = {
   'name': 'Oxford', lat: 40.51976, lng: -87.24779,
   'id': 'map_7',
}
places[7] = {
   'name': 'Oxford', lat: 45.73345, lng: -63.86542,
   'id': 'map_8',
}
places[8] = {
   'name': 'Oxford', lat: 42.44202, lng: -75.59769,
   'id': 'map_9',
}
places[9] = {
   'name': 'Oxford', lat: 40.80315, lng: -74.98962,
   'id': 'map_10',
}

function drawMaps() {
   if (GBrowserIsCompatible()) {
      for(var i in places) {
         var p = places[i];
         var div = document.getElementById(p['id']);

         div.style.display = "block";
         div.parentNode.style.marginBottom = "35px";

         var map = new GMap2(div);
         map.setCenter(new GLatLng(p['lat'], p['lng']), 8);

         var m = new GMarker( 
            new GLatLng(p['lat'], p['lng']),
            {title: p['name']}
         );
         map.addOverlay(m);
      }
   } else {
      document.write("<!doctype><html><body><h1>Unsupported Browser</h1></body></html>");
   }
}

var t;
$(document).ready(function(){
      t = setTimeout(function() {
         clearTimeout(t);
         drawMaps();
      }, 15*1000);
});
