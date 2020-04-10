.EXPORT_ALL_VARIABLES:
JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/
JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
PATH=/usr/lib/jvm/java-7-openjdk-amd64/bin/:$$(PATH)
SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
#_JAVA_OPTIONS='-XX:-UseSplitVerifier'

all:
	(cd BIT/samples; javac  *.java)
	(cd project/pt/ulisboa/tecnico/cnv/server; javac WebServer.java)
	(java -XX:-UseSplitVerifier OurTool project/pt/ulisboa/tecnico/cnv/solver/ instrumented/pt/ulisboa/tecnico/cnv/solver)

run:
	(java -XX:-UseSplitVerifier pt.ulisboa.tecnico.cnv.server.WebServer)

clean:
	(cd BIT/samples; /bin/rm *.class)
	(/bin/rm instrumented/pt/ulisboa/tecnico/cnv/solver/*)
