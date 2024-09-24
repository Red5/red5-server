Setting test properties is accomplished by creating a file here named `test.properties` whose contents look like this
```
# common test props

# RTMP / Default
# server host or ip address
server=localhost
# the connection port (rtmp/e=1935, rtmpt=5080, rtmps=8443)
port=1935
# application
app=live
# stream or file name
name=test123
# live (true) or vod (false)
live=true

# RTMPT
rtmpt.port=5080

# RTMPS
# server host or ip address
rtmps.server=my.ssl.server.name
rtmps.port=8443
rtmps.app=oflaDemo
rtmps.name=Derezzed.flv
# live (true) or vod (false)
rtmps.live=false
```
