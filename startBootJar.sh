#!/bin/bash

java_home=
app=app
log_file_path=logs/data.log
config_file_path=app.yml

pid=$($java_home/bin/jps | grep $app | grep -v grep | awk '{print $1}')
if [ $pid ]; then
  echo --------app is running pid=$pid
  kill -9 $pid
fi

if [ -f "$config_file_path" ]; then
  echo --------find config file、use [-Dspring.config.additional-location=$config_file_path]
  #采用增量覆盖方式的配置文件、不用复制整个配置文件
  nohup $java_home/bin/java -jar -Dspring.config.additional-location=$config_file_path $app >/dev/null 2>&1  &
else
  echo --------no config file
  nohup $java_home/bin/java -jar $app >/dev/null 2>&1  &
fi

echo --------nohup jar
while [ ! -f "$log_file_path" ]
do
  echo --------log[$log_file_path] not exist, wait 1s for tail
  sleep 1s
done
tail -f $log_file_path