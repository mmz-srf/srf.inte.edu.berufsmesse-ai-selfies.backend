## Create self-signed certificate for HTTPS

```
keytool -genkeypair \
        -alias mac-mini  \
        -keyalg RSA  \
        -keysize 2048  \
        -storetype PKCS12  \
        -keystore keystore.p12  \
        -validity 365  \
        -storepass secret  \
        -dname "CN=192.168.2.1, OU=SRF, O=SRG, L=Zurich, C=CH"   -ext "SAN=IP:192.168.2.1,DNS:localhost"
```