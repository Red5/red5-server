# RTMPS

RTMPS is a secure version of RTMP that uses TLS/SSL to encrypt the data. This is a guide to setting up RTMPS with Red5. An example keystore and truststore creation process will be explained as these files are required for the RTMPS feature. Examples will be provided for both the server and client side which will demonstrate how to use RTMPS and PKCS12 type keystores; JKS keystores can also be used, but are not covered here.

## Keystore and Truststore Creation

The following commands will create the necessary files for the RTMPS feature. The keystore will contain the server certificate and private key, while the truststore will contain the CA certificate. The client will use the truststore to verify the server certificate. Self-signed certificates are used in this example and are not expected to prevent the client from connecting to the server; in testing, the `ffplay` worked without issue. Examples show sample input for the certificate creation process.

* Create our CA key and certificate for self-signing:

```bash
openssl ecparam -name prime256v1 -genkeopenssl ecparam -name prime256v1 -genkey -noout -out ca.key

openssl req -new -x509 -sha256 -key ca.key -out ca.crt -days 3650

You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) [AU]:US
State or Province Name (full name) [Some-State]:Nevada
Locality Name (eg, city) []:Henderson
Organization Name (eg, company) [Internet Widgits Pty Ltd]:Red5
Organizational Unit Name (eg, section) []:dev
Common Name (e.g. server FQDN or YOUR name) []:Paul Gregoire
Email Address []:mondain@gmail.com
```

* Create the server key and certificate request:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out server.key

openssl req -new -sha256 -key server.key -out server.csr

You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) [AU]:US
State or Province Name (full name) [Some-State]:Nevada
Locality Name (eg, city) []:Henderson
Organization Name (eg, company) [Internet Widgits Pty Ltd]:Red5
Organizational Unit Name (eg, section) []:dev
Common Name (e.g. server FQDN or YOUR name) []:mondain-XPS-8930
Email Address []:mondain@gmail.com

Please enter the following 'extra' attributes
to be sent with your certificate request
A challenge password []:
An optional company name []:
```

* CA sign the server certificate request:

```bash
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 3650

Certificate request self-signature ok
subject=C = US, ST = Nevada, L = Henderson, O = Red5, OU = dev, CN = mondain-XPS-8930, emailAddress = mondain@gmail.com
```

* Create the client key and certificate request:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out client.key

openssl req -new -sha256 -key client.key -out client.csr

You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) [AU]:US
State or Province Name (full name) [Some-State]:Nevada
Locality Name (eg, city) []:Henderson
Organization Name (eg, company) [Internet Widgits Pty Ltd]:Red5
Organizational Unit Name (eg, section) []:dev
Common Name (e.g. server FQDN or YOUR name) []:mondain-XPS-8930
Email Address []:mondain@gmail.com

Please enter the following 'extra' attributes
to be sent with your certificate request
A challenge password []:
An optional company name []:
```

* CA sign the client certificate request:

```bash
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt -days 3650

Certificate request self-signature ok
subject=C = US, ST = Nevada, L = Henderson, O = Red5, OU = dev, CN = mondain-XPS-8930, emailAddress = mondain@gmail.com
```

* Add the server certificate to the keystore (_Make sure to use the same password for the key and store_):

```bash
keytool -genkey -dname "CN=mondain-XPS-8930, OU=dev, O=Red5, L=Henderson, S=Nevada, C=US" -keystore rtmps_keystore.jks -storepass password123 -keypass password123 -alias server -keyalg RSA -file server.crt

Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 90 days
	for: CN=mondain-XPS-8930, OU=dev, O=Red5, L=Henderson, ST=Nevada, C=US
```

* Add the self-signed CA root certificate to the truststore (_Make sure to use the same password for the store_):

```bash
keytool -import -trustcacerts -file ca.crt -alias CARoot -keystore rtmps_truststore.jks -storepass password123

Owner: EMAILADDRESS=mondain@gmail.com, CN=Paul Gregoire, OU=dev, O=Red5, L=Henderson, ST=Nevada, C=US
Issuer: EMAILADDRESS=mondain@gmail.com, CN=Paul Gregoire, OU=dev, O=Red5, L=Henderson, ST=Nevada, C=US
Serial number: 7139dce6b44a5e3d50ace573849cf88e63366153
Valid from: Mon Mar 04 18:10:14 PST 2024 until: Thu Mar 02 18:10:14 PST 2034
Certificate fingerprints:
	 SHA1: 48:CC:8A:65:5B:96:5B:7B:39:6C:55:27:30:84:24:B8:67:B0:91:6A
	 SHA256: C0:41:37:4C:DB:49:12:6B:14:C5:B4:8E:4A:28:1C:33:A0:C2:38:C7:76:44:97:6B:5E:A0:7B:20:01:0F:C9:2C
Signature algorithm name: SHA256withECDSA
Subject Public Key Algorithm: 256-bit EC (secp256r1) key
Version: 3

Extensions: 

#1: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: FF 05 5E DA 39 EB B5 40   E2 0D 5F 6A 90 DC C3 0B  ..^.9..@.._j....
0010: 12 B2 6D F6                                        ..m.
]
]

#2: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen: no limit
]

#3: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: FF 05 5E DA 39 EB B5 40   E2 0D 5F 6A 90 DC C3 0B  ..^.9..@.._j....
0010: 12 B2 6D F6                                        ..m.
]
]

Trust this certificate? [no]:  yes
Certificate was added to keystore
```

* Last step is to convert the keystore and truststore to PKCS12 format (_Make sure to use the same passwords_):

```bash

keytool -importkeystore -srckeystore rtmps_keystore.jks -destkeystore rtmps_keystore.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password123 -deststorepass password123 -srcalias server -destalias server -noprompt

keytool -importkeystore -srckeystore rtmps_truststore.jks -destkeystore rtmps_truststore.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass password123 -deststorepass password123
```

## Configuration

The following configuration changes are required to enable RTMPS in Red5.

### Server

On a server where RTMPS will be employed, two files in `conf` must be updated: `red5.properties` and `red5-core.xml`. This is in-addition to the keystore and truststore proceedure.

* In `red5-core.xml` uncomment the beans named `rtmpsMinaIoHandler` and `rtmpsTransport` which may be updated as required, otherwise their values come from the `red5.properties` file.  Note that the previous property names `keyStoreFile` and `trustStoreFile` have been replaced with `keystorePath` and `truststorePath`. 

```xml
<bean id="rtmpsMinaIoHandler" class="org.red5.server.net.rtmps.RTMPSMinaIoHandler">
    <property name="handler" ref="rtmpHandler" />
    <property name="keystorePassword" value="${rtmps.keystorepass}" />
    <property name="keystorePath" value="${rtmps.keystorefile}" />
    <property name="truststorePassword" value="${rtmps.truststorepass}" />
    <property name="truststorePath" value="${rtmps.truststorefile}" />
</bean>
```

To modify the ciphers and / or protocols in the  `rtmpsMinaIoHandler` bean in `red5-core.xml`, see the example below:

```xml
<bean id="rtmpsMinaIoHandler" class="org.red5.server.net.rtmps.RTMPSMinaIoHandler">
    <property name="handler" ref="rtmpHandler" />
    <property name="keystorePassword" value="${rtmps.keystorepass}" />
    <property name="keystorePath" value="${rtmps.keystorefile}" />
    <property name="truststorePassword" value="${rtmps.truststorepass}" />
    <property name="truststorePath" value="${rtmps.truststorefile}" />
    <property name="cipherSuites">
        <array>
            <value>TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256</value>
            <value>TLS_RSA_WITH_AES_128_CBC_SHA256</value>
        </array>
    </property>
    <property name="protocols">
        <array>
            <value>TLSv1.2</value>
            <value>TLSv1.3</value>
        </array>
    </property>
</bean>
```

* The `rtmpsTransport` is not expected to need modification, but can be updated as required. The `rtmps.host` and `rtmps.port` properties are required to be set in `red5.properties` and are used in the `rtmpsTransport` bean:

```xml
    <bean id="rtmpsTransport" class="org.red5.server.net.rtmp.RTMPMinaTransport" init-method="start" destroy-method="stop">
        <property name="ioHandler" ref="rtmpsMinaIoHandler" />
        <property name="addresses">
            <list>
                 <value>${rtmps.host}:${rtmps.port}</value>
            </list>
        </property>
        <property name="ioThreads" value="${rtmp.io_threads}" />
        <property name="tcpNoDelay" value="${rtmp.tcp_nodelay}" />
    </bean>
```

* In `red5.properties`, update these properties to utilize your values; especially for store passwords and locations:

```properties
# RTMPS
rtmps.host=0.0.0.0
rtmps.port=8443
rtmps.ping_interval=5000
rtmps.max_inactivity=60000
rtmps.max_keep_alive_requests=-1
rtmps.max_threads=8
rtmps.acceptor_thread_count=2
rtmps.processor_cache=20
# RTMPS Key and Trust store parameters
rtmps.keystorepass=password123
rtmps.keystorefile=conf/rtmps_keystore.p12
rtmps.truststorepass=password123
rtmps.truststorefile=conf/rtmps_truststore.p12
```

### Client

When connecting to a server that uses RTMPS, the client must have the server's certificate in its truststore. The following example demonstrates how to use the truststore with the Red5 client. Before connecting to the server, the client must set the keystore and truststore paths with password.

* Using full paths to the keystore and truststore files:

```java
TLSFactory.setKeystorePath("/workspace/client/conf/rtmps_keystore.p12");
TLSFactory.setTruststorePath("/workspace/client/conf/rtmps_truststore.p12");
```

* When the keystore and truststore are contained within a jar file, use the following format: `jar:file:/path/to/your.jar!/path/to/file/in/jar` for the keystore and truststore paths. This example assumes the jar file which is named `my_rtmps_client.jar` file is contained in a `lib` sub-directory of the application client launch location and the keystore and truststore are in the root:

```java
String jarKeystorePath = String.format("jar:file:%s/lib/my_rtmps_client.jar!/rtmps_%s.p12", Paths.get(System.getProperty("user.dir"), "keystore");
TLSFactory.setKeystorePath(jarKeystorePath);
String jarTruststorePath = String.format("jar:file:%s/lib/my_rtmps_client.jar!/rtmps_%s.p12", Paths.get(System.getProperty("user.dir"), "truststore");
TLSFactory.setTruststorePath(jarTruststorePath);
```

## Testing

Using ffplay to test playback, issue the following, but make sure to update the command for your server IP and stream name: `ffplay rtmps://localhost:8443/live/stream1` (this assumes a stream named `stream1` is being published already).

### Useful System Properties

* To enable SSL debugging, add the following system property to the JVM: `-Djavax.net.debug=SSL`
* To enable more detailed SSL debugging, add the following system property to the JVM: `-Djavax.net.debug=SSL,handshake,verbose,trustmanager,keymanager,record,plaintext`
