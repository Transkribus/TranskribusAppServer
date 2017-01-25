# TranskribusAppServer

An application that is responsible of carrying out all kind of jobs (utility, layout analysis, text recognition) that are scheduled at the TranskribusServer. 

Requirements:
* Install OpenCV >= 3.1
	* http://docs.opencv.org/3.1.0/d7/d9f/tutorial_linux_install.html
		* cmake -D CMAKE_BUILD_TYPE=RELEASE -D CMAKE_INSTALL_PREFIX=/usr/local -D BUILD_SHARED_LIBS=OFF -D BUILD_EXAMPLES=OFF -D BUILD_TESTS=OFF -D BUILD_PERF_TESTS=OFF ..
	* for using it in Eclipse: 
		http://docs.opencv.org/2.4/doc/tutorials/introduction/java_eclipse/java_eclipse.html#java-eclipse
	
* Install exiftool (the command might be called exif on some linux distributions. Then create symlink to exiftool in /usr/local/bin)
* mount transkribus NAS and dea_scratch (at /mnt/transkribus and /mnt/dea_scratch)
