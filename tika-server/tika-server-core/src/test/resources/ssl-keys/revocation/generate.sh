#!/usr/bin/env bash
#
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
#
# Regenerates the fixtures for TikaServerClientRevocationTest: a CA, client certificates it
# issued, and a CRL revoking one of them. Run from this directory; needs openssl and keytool.
# The CRL distribution point is http://127.0.0.1:18181/ca.crl, which the test serves.
set -euo pipefail
cd "$(dirname "$0")"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/ca.cnf" <<CNF
[ ca ]
default_ca = testca
[ testca ]
dir = $work
database = $work/index.txt
new_certs_dir = $work
serial = $work/serial
crlnumber = $work/crlnumber
default_md = sha256
policy = anything
default_days = 36500
default_crl_days = 36500
[ anything ]
commonName = supplied
[ v3ca ]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
[ client ]
basicConstraints = CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = clientAuth
[ client_cdp ]
basicConstraints = CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = clientAuth
crlDistributionPoints = URI:http://127.0.0.1:18181/ca.crl
[ forged_cdp ]
basicConstraints = CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = clientAuth
crlDistributionPoints = URI:http://127.0.0.1:18181/forged.crl
CNF
touch "$work/index.txt"; echo 1000 > "$work/serial"; echo 1000 > "$work/crlnumber"

openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/ca.key" -out "$work/ca.crt" -days 36500 \
    -subj "/CN=Tika Test CA" -config "$work/ca.cnf" -extensions v3ca
rm -f ca-truststore.p12
keytool -importcert -noprompt -alias ca -file "$work/ca.crt" -keystore ca-truststore.p12 \
    -storetype PKCS12 -storepass tika-secret

issue() { # name extensions
    openssl req -newkey rsa:2048 -nodes -keyout "$work/$1.key" -out "$work/$1.csr" -subj "/CN=client-$1"
    openssl ca -batch -config "$work/ca.cnf" -extensions "$2" -in "$work/$1.csr" -out "$work/$1.crt" \
        -cert "$work/ca.crt" -keyfile "$work/ca.key"
    openssl pkcs12 -export -in "$work/$1.crt" -inkey "$work/$1.key" -certfile "$work/ca.crt" -name client \
        -out "$1.p12" -passout pass:tika-secret
}
issue good client_cdp
issue revoked client_cdp
issue no-cdp client

# same issuer name as the CA, signed by another key: must be rejected before any CRL fetch
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/fake-ca.key" -out "$work/fake-ca.crt" -days 36500 \
    -subj "/CN=Tika Test CA" -config "$work/ca.cnf" -extensions v3ca
openssl req -newkey rsa:2048 -nodes -keyout "$work/forged.key" -out "$work/forged.csr" -subj "/CN=client-forged"
openssl x509 -req -in "$work/forged.csr" -CA "$work/fake-ca.crt" -CAkey "$work/fake-ca.key" -set_serial 7 \
    -days 36500 -extfile "$work/ca.cnf" -extensions forged_cdp -out "$work/forged.crt"
openssl pkcs12 -export -in "$work/forged.crt" -inkey "$work/forged.key" -certfile "$work/fake-ca.crt" -name client \
    -out forged.p12 -passout pass:tika-secret

openssl ca -config "$work/ca.cnf" -revoke "$work/revoked.crt" -cert "$work/ca.crt" -keyfile "$work/ca.key"
openssl ca -config "$work/ca.cnf" -gencrl -cert "$work/ca.crt" -keyfile "$work/ca.key" -out "$work/ca.crl.pem"
openssl crl -in "$work/ca.crl.pem" -outform DER -out ca.crl
