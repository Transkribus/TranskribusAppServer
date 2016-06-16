#!/bin/bash
java -jar -Xss32m -Djava.library.path=/usr/local/share/OpenCV/java/ ${appName}-${project.version}.jar
