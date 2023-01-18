To generate these, I followed
https://bhashineen.medium.com/steps-to-create-keystores-and-truststores-to-be-used-in-mutual-ssl-of-a-server-and-a-client-e0b75ca3ea42

1. Create a keystore for the client
keytool -genkey -alias tika-client -keyalg RSA -keystore tika-client-keystore.p12 -keysize 2048 -storeType PKCS12 -validity 9999 -ext SAN=DNS:localhost,IP:127.0.0.1 -dname "CN=localhost, OU=Tika Testing"

2. Export the public cert of the client
keytool -export -keystore tika-client-keystore.p12 -alias tika-client -file tika-client.crt

3. Create a keystore for the server
keytool -genkey -alias tika-server -keyalg RSA -keystore tika-server-keystore.p12 -keysize 2048 -storeType PKCS12 -validity 9999 -ext SAN=DNS:localhost,IP:127.0.0.1 -dname "CN=localhost, OU=Tika Testing"

4. Export the public cert of the server
keytool -export -keystore tika-server-keystore.p12 -alias tika-server -file tika-server.crt

5. Create a truststore for the client
keytool -genkey -alias tika-client-trust -keyalg RSA -keystore tika-client-truststore.p12 -keysize 2048 -storeType PKCS12 -validity 9999 -ext SAN=DNS:localhost,IP:127.0.0.1 -dname "CN=localhost, OU=Tika Testing"

6. Create a truststore for the server
keytool -genkey -alias tika-server-trust -keyalg RSA -keystore tika-server-truststore.p12 -keysize 2048 -storeType PKCS12 -validity 9999 -ext SAN=DNS:localhost,IP:127.0.0.1 -dname "CN=localhost, OU=Tika Testing"

7. Import the client public cert into the server truststore
keytool -import -keystore tika-server-truststore.p12 -alias tika-client -file tika-client.crt

8. Import the server public cert into the client truststore
keytool -import -keystore tika-client-truststore.p12 -alias tika-server -file tika-server.crt

NOTE: I did not then delete the private keys because I wanted to leave them in in case we needed to do something else.