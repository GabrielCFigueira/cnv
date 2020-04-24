# CNV checkpoint
Diogo Fernandes 86410, Gabriel Figueira 86426, João Margaço 86451

## System Architecture

Our system is divided into different folders:

1. The BIT folder, and more specifically the BIT package which contains the HighBIT and LowBIT folders and OurTool with the necessary code to instrument the Solver's code. OurTool will store metrics in a synchronous way (dependent on the Thread ID) and offer static methods to WebServer class to access those same metrics.
1. DatabaseScripts which contains our auxiliary tools in order for us to enumerate the DynamoDB tables and reset its state (dynamodb.local.py through BOTO3 installed with pip3) and in the case we need to test locally we create and run the DynamoDB docker image through a script (dockerScript.sh).
1. The instrumented folder which will contain the instrumented code, which is done by running OurTool with the Solver classes as input.
1. The Project folder which will contain the project source code, and more specifically our changed version of the WebServer class, which periodically requests (statically) OurTool for one specificic request in a synchronous way, and stores it in the DynamoDB database.

## System Configurations

### AutoScaler



### LoadBalancer
