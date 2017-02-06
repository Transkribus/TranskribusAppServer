all:
	mvn clean install -f ../TranskribusCore/pom.xml
	mvn clean install -f ../TranskribusSearch
	#mvn clean install -f ../interfaces/pom.xml
	mvn clean install -f ../CITlabModule/pom.xml
	mvn clean install -f ../TranskribusPersistence/pom.xml
	mvn clean install

all-wo-modules:
	mvn clean install -f ../TranskribusCore/pom.xml
	mvn clean install -f ../TranskribusSearch
	mvn clean install -f ../interfaces/pom.xml
	mvn clean install -f ../TranskribusPersistence/pom.xml
	mvn clean install

core:
	mvn clean install -f ../TranskribusCore/pom.xml
	
persistence:
	mvn clean install -f ../TranskribusPersistence/pom.xml

server:
	mvn clean install
	
#deploy-only:
#	mvn tomcat7:redeploy-only
	
#deploy:
#	mvn clean install -f ../TranskribusCore/pom.xml
#	mvn clean install -f ../interfaces/pom.xml
#	mvn clean install -f ../CITlabModule/pom.xml
#	mvn clean install -f ../TranskribusPersistence/pom.xml
#	mvn clean install
	
clean:
	mvn clean
