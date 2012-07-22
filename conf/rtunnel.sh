#! /bin/sh
# Copyright (c) 2011-2012 www.skybility.com.
# All rights reserved.
#
# Author: Jeremy Xu
#
# /etc/init.d/rtunnel
#
### BEGIN INIT INFO
# Provides:       rtunneld
# Required-Start: $network $remote_fs
# Required-Stop: $network $remote_fs
# Default-Start:  2 3 4 5
# Default-Stop:   0 1 6
# Short-Description: RTunnel
# Description:    RTunnel.
### END INIT INFO

# First reset status of this service
#. /etc/rc.status
#rc_reset

# Return values acc. to LSB for all commands but status:
# 0 - success
# 1 - failed

# set default options
export RTUNNEL_HOME=/opt/rtunnel
export JAVA_HOME=/usr/lib/jvm/java
export CLASSPATH=$RTUNNEL_HOME/commons-cli-1.2.jar:$RTUNNEL_HOME/logback-classic-1.0.1.jar:$RTUNNEL_HOME/logback-core-1.0.1.jar:$RTUNNEL_HOME/slf4j-api-1.6.4.jar:$RTUNNEL_HOME/rtunnel.jar:$RTUNNEL_HOME/conf:$JAVA_HOME/lib:$CLASSPATH
export PATH=$JAVA_HOME/bin:$PATH
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8

# set default PID variables
RTUNNELD_PID="${RTUNNEL_HOME}/rtunneld.pid"

case "$1" in
  start)
    echo -n "Starting RTunnel daemon."
    java -Dlogback.configurationFile=/opt/rtunnel/conf/rtunnel_logback.xml com.skybility.cloudsoft.rtunnel.server.RTunnelServer -p 8322 > /dev/null 2>&1 &
    echo $! > $RTUNNELD_PID
    exit $?
#	rc_status -v
  ;;
  stop)
    if test -e $RTUNNELD_PID
    then
        echo "Shutting down RTunnel daemon. "
        kill -TERM `cat $RTUNNELD_PID`
    else
        echo "Maybe RTunnel daemon is not running."
#		rc_failed 1
    fi
    exit $?
#	rc_status -v
    rm -f ${RTUNNELD_PID} 2>/dev/null
  ;;
  restart)
    $0 stop
    $0 start
#	rc_status
  ;;
  status)
    echo "Checking for RTunnel daemon."
    pid=`cat $RTUNNELD_PID`
    test `ps -ef|grep RTunnelServer|grep $pid|grep -v grep|wc -l` -gt 0
    exit $?
#	rc_status -v
  ;;
  *)
    echo "Usage: $0 {start|stop|status|restart}"
    exit 1
  ;;
esac
exit 0
#rc_exit
