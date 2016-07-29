#!/bin/bash
java -DHOST_NAME=`hostname` -jar -Xss32m ${appName}-${project.version}.jar
