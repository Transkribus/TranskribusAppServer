# TranskribusAppServer

An application that is responsible of carrying out all kind of jobs (utility, layout analysis, text recognition) that are scheduled at the TranskribusServer. 

Requirements:
- Install and start ntpd (for syncing the system clock. required by quartz scheduler)
- Install OpenCV >= 3.1
	-http://docs.opencv.org/3.1.0/d7/d9f/tutorial_linux_install.html
	-for using it in Eclipse: 
		http://docs.opencv.org/2.4/doc/tutorials/introduction/java_eclipse/java_eclipse.html#java-eclipse