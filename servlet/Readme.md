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

Webapp configuration in `web.xml` file (if needed):

```xml
    <!-- RTMPT servlet -->
    <servlet>
        <servlet-name>rtmpt</servlet-name>
        <servlet-class>org.red5.server.net.rtmpt.RTMPTServlet</servlet-class>
        <load-on-startup>1</load-on-startup>
    </servlet>
    <servlet-mapping>
        <servlet-name>rtmpt</servlet-name>
        <url-pattern>/fcs/*</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
        <servlet-name>rtmpt</servlet-name>
        <url-pattern>/open/*</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
        <servlet-name>rtmpt</servlet-name>
        <url-pattern>/close/*</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
        <servlet-name>rtmpt</servlet-name>
        <url-pattern>/send/*</url-pattern>
    </servlet-mapping>
    <servlet-mapping>
        <servlet-name>rtmpt</servlet-name>
        <url-pattern>/idle/*</url-pattern>
    </servlet-mapping>
```
