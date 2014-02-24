#!/bin/bash

if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root" 1>&2
   exit 1
fi

cp service.sh /etc/init.d/watchfuleye
cp watchfuleye.conf /etc/.

chmod +x /etc/init.d/watchfuleye
chkconfig --add watchfuleye

vi /etc/watchfuleye.conf
service watchfuleye restart

