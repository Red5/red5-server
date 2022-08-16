/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.utils;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;
import org.junit.Ignore;

public class XMLUtilsTest {

    private static final String XML_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><red5 version=\"0.8.0\"><note>Red5 is awesome</note><emptynode/></red5>";

    private static final String XML_STRING_HUGE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ivy-module version=\"1.3\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://ant.apache.org/ivy/schemas/ivy.xsd\"> <info organisation=\"red5\" module=\"server\" /> <configurations>  <conf name=\"default\"/> <conf name=\"java5\" extends=\"default\" description=\"Java 5 dependencies\"/> <conf name=\"java6\" extends=\"default\" description=\"Java 6 dependencies\"/> <conf name=\"utest\" extends=\"eclipse\" description=\"Unit testing dependencies\"/> <conf name=\"eclipse\" description=\"Special dependencies in Eclipse\"/> </configurations> <dependencies> <!-- J2EE --> <dependency org=\"tomcat\" name=\"jasper\" rev=\"6.0.18\" /> <dependency org=\"tomcat\" name=\"jasper-jdt\" rev=\"6.0.18\" /> <!-- <dependency org=\"tomcat\" name=\"jasper-el\" rev=\"6.0.18\" /> <dependency org=\"tomcat\" name=\"el-api\" rev=\"\" /> --> <dependency org=\"javax\" name=\"jsp-api\" rev=\"2.1\" /> <dependency org=\"javax\" name=\"servlet-api\" rev=\"2.5\" /> <dependency org=\"javax\" name=\"ejb3-persistence\" rev=\"\" /> <dependency name=\"naming-factory\" rev=\"\" /> <dependency name=\"naming-resources\" rev=\"\" /> <!-- Spring --> <dependency org=\"spring\" name=\"spring-aop\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"spring-orm\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"spring-beans\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"spring-context\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"spring-core\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"spring-web\" rev=\"2.5.5\" /> <dependency org=\"spring\" name=\"aopalliance\" rev=\"\" /> <!-- Tomcat --> <dependency org=\"tomcat\" name=\"catalina\" rev=\"6.0.18\" /> <dependency org=\"tomcat\" name=\"tomcat-coyote\" rev=\"6.0.18\" /> <dependency org=\"tomcat\" name=\"annotations-api\" rev=\"\" /> <dependency org=\"commons\" name=\"commons-modeler\" rev=\"2.0.1\" /> <!-- Jetty --> <dependency org=\"jetty\" name=\"jetty\" rev=\"6.1.9\" /> <dependency org=\"jetty\" name=\"jetty-util\" rev=\"6.1.9\" /> <dependency org=\"jetty\" name=\"jetty-xbean\" rev=\"6.1.9\" /> <!-- Logging --> <dependency name=\"slf4j-api\" rev=\"1.5.3\" /> <dependency name=\"jcl-over-slf4j\" rev=\"1.5.3\" /> <dependency name=\"log4j-over-slf4j\" rev=\"1.5.3\" /> <dependency name=\"jul-to-slf4j\" rev=\"1.5.3\" /> <dependency name=\"tomcat-juli-slf4j\" rev=\"1.5.0\" /> <dependency name=\"logback-core\" rev=\"0.9.10-SNAPSHOT\" /> <dependency name=\"logback-classic\" rev=\"0.9.10-SNAPSHOT\" /> <!-- General --> <dependency org=\"cglib\" name=\"cglib-nodep\" rev=\"2.1_3\" /> <dependency org=\"commons\" name=\"commons-beanutils\" rev=\"1.8.0\" /> <dependency org=\"commons\" name=\"commons-codec\" rev=\"1.3\" /> <dependency org=\"commons\" name=\"commons-collections\" rev=\"3.2.1\" /> <dependency org=\"commons\" name=\"commons-httpclient\" rev=\"3.1\" /> <dependency org=\"commons\" name=\"commons-lang\" rev=\"2.4\" /> <dependency org=\"commons\" name=\"commons-pool\" rev=\"1.3\" /> <dependency name=\"quartz\" rev=\"1.6.1-RC1\" /> <dependency org=\"javax\" name=\"jta\" rev=\"1.0.1B\" /> <dependency name=\"ehcache\" rev=\"1.4.1\" /> <dependency org=\"javax\" name=\"activation\" rev=\"1.1\" /> <!-- XML --> <dependency name=\"xercesImpl\" rev=\"2.9.0\" /> <dependency name=\"xml-apis\" rev=\"2.9.0\" /> <dependency name=\"xmlrpc\" rev=\"2.0.1\" /> <dependency name=\"stax2\" rev=\"2.1\" /> <dependency name=\"wstx-lgpl\" rev=\"3.2.7\" /> <!-- JMX --> <dependency org=\"jmx\" name=\"jmxremote\" rev=\"1.0.1\" /> <dependency org=\"jmx\" name=\"jmxtools\" rev=\"1.2.1\" /> <dependency org=\"jmx\" name=\"rmissl\" rev=\"1.0.1\" /> <!-- Mina --> <dependency org=\"mina\" name=\"mina-core\" rev=\"1.1.7\" /> <dependency org=\"mina\" name=\"mina-filter-ssl\" rev=\"1.1.7\" /> <dependency org=\"mina\" name=\"mina-integration-spring\" rev=\"1.1.7\" /> <dependency org=\"mina\" name=\"mina-integration-jmx\" rev=\"1.1.7\" /> <!-- Scripting --> <dependency org=\"asm\" name=\"asm\" rev=\"2.2.3\" /> <dependency org=\"asm\" name=\"asm-commons\" rev=\"2.2.3\" /> <dependency org=\"antlr\" name=\"antlr\" rev=\"2.7.6\" /> <dependency name=\"bsh\" rev=\"2.0b4\" /> <dependency name=\"groovy\" rev=\"1.0\" /> <dependency name=\"jruby\" rev=\"1.0.3\" conf=\"java6->*\"/> <dependency name=\"jython\" rev=\"2.5\" /> <dependency org=\"spring\" name=\"spring-context-support\" rev=\"2.5.5\" /> <!-- Java5 support --> <dependency org=\"rhino\" name=\"js\" rev=\"1.6R7\" conf=\"java5->*\"/> <dependency name=\"jsr173_1.0_api\" rev=\"\" conf=\"java5->*\"/> <dependency name=\"jsr-223\" rev=\"1.0\" conf=\"java5->*\"/> <dependency name=\"js-engine\" rev=\"\" conf=\"java5->*\"/> <dependency name=\"jython-engine\" rev=\"\" conf=\"java5->*\"/> <dependency name=\"groovy-engine\" rev=\"\" conf=\"java5->*\"/> <dependency name=\"jruby\" rev=\"1.0.1\" conf=\"java5->*\"/> <dependency name=\"jruby-engine\" rev=\"\" conf=\"java5->*\"/> <!-- Crypto --> <dependency name=\"bcprov-jdk16\" rev=\"139\" conf=\"java6->*\"/> <dependency name=\"bcprov-jdk15\" rev=\"139\" conf=\"java5->*\"/> <!-- MP3 --> <dependency name=\"jaudiotagger\" rev=\"1.0.8\" /> <!-- Testing support --> <dependency name=\"junit\" rev=\"4.0\" conf=\"utest->eclipse\"/> <dependency name=\"GroboUtils\" rev=\"4-core\" conf=\"eclipse->*\"/> <dependency org=\"spring\" name=\"spring-test\" rev=\"2.5.5\" conf=\"eclipse->*\"/> <dependency name=\"jython-engine\" rev=\"\" conf=\"eclipse->*\"/> <dependency name=\"groovy-engine\" rev=\"\" conf=\"eclipse->*\"/> <dependency name=\"jruby-engine\" rev=\"\" conf=\"eclipse->*\"/> </dependencies></ivy-module>";

    @Test
    public void testStringToDoc() throws IOException {
        XMLUtils.stringToDoc(XML_STRING);
        XMLUtils.stringToDoc(XML_STRING_HUGE);

    }

    @Test
    @Ignore
    public void testDocToString() {
        fail("Not yet implemented");
    }

    @Test
    @Ignore
    public void testDocToString1() {
        fail("Not yet implemented");
    }

    @Test
    @Ignore
    public void testDocToString2() {
        fail("Not yet implemented");
    }

}
