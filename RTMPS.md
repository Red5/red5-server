# RTMPS

RTMPS is a secure version of RTMP that uses TLS/SSL to encrypt the data. This is a guide to setting up RTMPS with Red5. An example keystore and truststore creation process will be explained as these files are required for the RTMPS feature. Examples will be provided for both the server and client side which will demonstrate how to use RTMPS and PKCS12 type keystores; JKS keystores can also be used, but are not covered here.

## Configuration

### Server

On a server where RTMPS will be employed, two files in `conf` must be updated: `red5.properties` and `red5-core.xml`. This is in-addition to the keystore and truststore proceedure.

* In `red5-core.xml` uncomment the beans named `rtmpsMinaIoHandler` and `rtmpsTransport` which may be updated as required, otherwise their values come from the `red5.properties` file. See [Advanced-configuration](#advanced-configuration) for more information.

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
rtmps.keystorefile=conf/server.p12
rtmps.truststorepass=password123
rtmps.truststorefile=conf/truststore.p12
```

### Client


```java
TLSFactory.setKeystorePath("/workspace/client/conf/rtmps_server.p12");
TLSFactory.setTruststorePath("/workspace/client/conf/rtmps_truststore.p12");
```

## Testing

Using ffplay issue the following, update for your server IP and stream name as needed: `ffplay rtmps://localhost:8443/live/stream1`


### Useful System Properties


`-Djavax.net.debug=SSL,handshake,verbose,trustmanager,keymanager,record,plaintext`

## Advanced configuration

* The ciphers and protocols can be modified in the  `rtmpsMinaIoHandler` bean in `red5-core.xml` to suit any special needs.

```xml
<bean id="rtmpsMinaIoHandler" class="org.red5.server.net.rtmps.RTMPSMinaIoHandler">
    <property name="handler" ref="rtmpHandler" />
    <property name="keystorePassword" value="${rtmps.keystorepass}" />
    <property name="keystoreFile" value="${rtmps.keystorefile}" />
    <property name="truststorePassword" value="${rtmps.truststorepass}" />
    <property name="truststoreFile" value="${rtmps.truststorefile}" />
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
