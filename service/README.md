# Red5 service

Herein you will find the service / daemon portion of the project. We utilize the [Apache Commons Daemon project](https://commons.apache.org/proper/commons-daemon/) for our service process on all supported platforms; currently [Linux](https://commons.apache.org/proper/commons-daemon/jsvc.html) and [Windows](https://commons.apache.org/proper/commons-daemon/procrun.html) are supported.

Apache Daemon [Windows binaries download](http://www.apache.org/dist/commons/daemon/binaries/windows/)


## Daemon Details

To hook-in to the daemon, one would get an instance of the [`EngineLauncher` class](https://github.com/Red5/red5-service/blob/master/src/main/java/org/red5/daemon/EngineLauncher.java) and call the `start()` method to start the server process and `stop()` to terminate it. The windows implementation using `procrun` is only slightly different in that it calls `windowsService()` for both start and stop (The string `start` must be supplied via args with windows).

### Shutdown
The Server / service shutdown is performed via socket connection and the _shutdown_ token must be supplied via `args` when making the request; if its not supplied, the `shutdown.token` file will be opened, if it exists and is readable.

## Setup

### Linux
Linux daemon uses __jsvc__ and the `init.d` script `red5`.

1. Set / export __RED5_HOME__ environmental variable
2. Edit the variables in the init.d script to match your server
3. Install jsvc
 * Debian `sudo apt-get install jsvc`
 * CentOS `sudo yum ??`
4. Copy the `red5` script to your init.d directory, ex. `/etc/init.d/`
5. This step is for Systemd enabled operating systems __only__ such as CentOS 7. Copy the `red5.service` file into the `/etc/systemd/system/` directory. Modify the file as needed, ensure the `ExecStart` path is correctly pointing to the init script.
6. Install the init.d script
 * Debian
```sh
sudo update-rc.d red5 defaults
sudo update-rc.d red5 enable
```
 * CentOS
```sh
systemctl daemon-reload
systemctl enable red5.service
```
6. Start the service
 * Debian `service red5 start`
 * CentOS `systemctl start red5.service`
7. Stop the service
 * Debian `service red5 stop`
 * CentOs `systemctl stop red5.service`
 
#### CentOS
Init script specific to CentOS
```sh
#!/bin/sh

### BEGIN INIT INFO
# Provides:             red5
# Required-Start:       $remote_fs $syslog
# Required-Stop:        $remote_fs $syslog
# Default-Start:        2 3 4 5
# Default-Stop:         0 1 6
# Short-Description:    Red5 server
### END INIT INFO

start() {
  cd /opt/red5-server && nohup ./red5.sh > /dev/null 2>&1 &
  echo 'Service started' >&2
}

stop() {
 cd /opt/red5-server && ./red5-shutdown.sh > /dev/null 2>&1 &
 echo 'Service stopped' >&2
}

case "$1" in
start)
    start
    ;;
stop)
    stop
;;
restart)
    stop
    start
    ;;
 *)
    echo "Usage: $0 {start|stop|restart}"
 esac
```
 
### Windows
Windows daemon uses __procrun__ (whose executable is named prunsrv.exe).

**Install**

1. Set the __RED5_HOME__ environmental variable (see below _Managing Windows Environment Variables_ for more detail)
2. Edit the variables in the `install-service.bat` script to match your server, ONLY if you have special requirements
3. Download the [windows binaries](http://www.apache.org/dist/commons/daemon/binaries/windows/)
 * The most current daemon archive (_since 2013_) to use is named: `commons-daemon-1.0.15-bin-windows.zip` 
4. Unzip the daemon archive into your red5 directory
5. Ensure `prunsrv.exe` is in your red5 home directory alongside `red5-service.jar`
6. Execute `install-service.bat` to install the service (See Install Errors below for resolutions, if you encounter an error)
7. Open the windows services panel `services.msc`
8. Scroll down to `Red5 Media Server`
9. Start the service by clicking the start button in the UI
10. Stop the service by clicking the stop button in the UI

**Install Errors**
If you have a JRE installed and have pointed __JAVA_HOME__ at its location, you will see this error:
```
C:\red5>install-service.bat
Processor Architecture: "AMD64"
Using Daemon:           "C:\red5\\amd64\prunsrv.exe"
The JAVA_HOME environment variable is not defined correctly
This environment variable is needed to run this program
NB: JAVA_HOME should point to a JDK not a JRE
```
To resolve this, remove the __JAVA_HOME__ variable and create one for __JRE_HOME__. Now go back to step 6 and try again.



**Uninstall**

1. To uninstall the service execute `uninstall-service.bat`

**Managing Windows Environment Variables**

The __RED5_HOME__ variable is used by Red5 and the service daemon; it is used to locate the Red5 installation. The variable must point to the Red5 install location, which may be `C:\Program Files\Red5` or something similar depending on how you installed it.

*System Variables*

You must be an administrator to modify a system environment variable. System environment variables are defined by Windows and apply to all computer users. Changes to the system environment are written to the registry, and usually require a restart to become effective.

*User Variables for User Name*

Any user can add, modify, or remove a user environment variable. These variables are established by Windows Setup, by some programs, and by users. The changes are written to the registry, and are usually effective immediately. However, after a change to user environment variables is made, any open software programs should be restarted to force them to read the new registry values. The common reason to add variables is to provide data that is required for variables that you want to use in scripts.

To view or change environment variables:
 * Right-click `My Computer`, and then click `Properties`.
  * If no `My Computer` exists (Windows 10), Right-click windows logo (Start button) and select `System` 
 * Click the `Advanced` tab or `Advanced system settings` option
 * Click `Environment variables`.
 * Click one the following options, for either a user or a system variable:
  * Click New to add a new variable name and value.
  * Click an existing variable, and then click Edit to change its name or value.

