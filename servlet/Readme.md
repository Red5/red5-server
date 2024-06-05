# Servlet

## RTMPT

Using RTMPT requires adding this to the `red5-common.xml` file:

```xml
    <!-- RTMPT codec factory -->
    <bean id="rtmptCodecFactory" class="org.red5.server.net.rtmpt.codec.RTMPTCodecFactory" autowire="byType" init-method="init">
        <property name="baseTolerance" value="${rtmpt.encoder_base_tolerance}" />
        <property name="dropLiveFuture" value="${rtmpt.encoder_drop_live_future}" />
    </bean>
```
