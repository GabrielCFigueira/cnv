.EXPORT_ALL_VARIABLES:
JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/
JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
PATH=/usr/lib/jvm/java-1.7.0-openjdk/bin:$$PATH
SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
#CLASSPATH=%CLASSPATH%:~/cnv/instrumented:~/cnv/project:~/cnv/BIT:~/cnv/BIT/samples:~/aws-java-sdk-1.11.764/lib/aws-java-sdk-1.11.764.jar:~/aws-java-sdk-1.11.764/third-party/lib/*:.
#_JAVA_OPTIONS='-XX:-UseSplitVerifier'
SHELL := /bin/bash

all:
	(echo $$CLASSPATH)
	(cd BIT/BIT; javac OurTool.java)
	(cd project/pt/ulisboa/tecnico/cnv/server; javac WebServer.java)
	(java -XX:-UseSplitVerifier BIT/OurTool project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver)
run:
	(java -XX:-UseSplitVerifier pt.ulisboa.tecnico.cnv.server.WebServer >> ola.log)

clean:
	(cd BIT/samples; $(RM) *.class)
	($(RM) instrumented/pt/ulisboa/tecnico/cnv/solver/*)

loadbalancer:
	(cd project/pt/ulisboa/tecnico/cnv/loadbalancer; javac *.java)
	(java -XX:-UseSplitVerifier pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer >> loadbalancer.log)
