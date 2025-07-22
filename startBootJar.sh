#!/bin/bash
app='app'
pid=`ps -ef | grep $app | grep -v grep |awk '{print $2}'`
if [ $pid ]; then
  echo :App  is  running pid=$pid
  kill -9 $pid
fi
nohup java -jar -Dspring.config.location=app.yml  $app > /dev/null 2>&1  &
echo :start $app succeed