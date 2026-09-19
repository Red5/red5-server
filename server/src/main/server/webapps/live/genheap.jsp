<%@ page import="
java.io.File,
java.lang.management.ManagementFactory,
java.text.SimpleDateFormat,
java.util.Date,
javax.management.MBeanServer,
com.sun.management.HotSpotDiagnosticMXBean" %>
<html>
    <head>
        <title>Generate JVM Heap Dump</title>
    </head>
    <body>
        <h1>Generate JVM Heap Dump</h1>
<%
String liveParam = request.getParameter("live");
boolean live = liveParam == null || Boolean.parseBoolean(liveParam);
String dumpDirParam = request.getParameter("dir");
File dumpDir = dumpDirParam == null || dumpDirParam.trim().isEmpty()
        ? new File(System.getProperty("java.io.tmpdir"), "red5-heapdumps")
        : new File(dumpDirParam.trim());

try {
    if (!dumpDir.exists() && !dumpDir.mkdirs()) {
        throw new IllegalStateException("Unable to create dump directory: " + dumpDir.getAbsolutePath());
    }
    if (!dumpDir.isDirectory()) {
        throw new IllegalStateException("Heap dump path is not a directory: " + dumpDir.getAbsolutePath());
    }

    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
    File dumpFile = new File(dumpDir, "red5-heap-" + timestamp + ".hprof");

    MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    HotSpotDiagnosticMXBean hotSpotDiagnostic = ManagementFactory.newPlatformMXBeanProxy(
            mBeanServer,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean.class);
    hotSpotDiagnostic.dumpHeap(dumpFile.getAbsolutePath(), live);
%>
        <p>Heap dump created.</p>
        <ul>
            <li>File: <%= dumpFile.getAbsolutePath() %></li>
            <li>Live objects only: <%= live %></li>
        </ul>
<%
} catch (Throwable t) {
%>
        <p>Heap dump failed.</p>
        <pre><%= t.getClass().getName() %>: <%= t.getMessage() %></pre>
<%
}
%>
    </body>
</html>
