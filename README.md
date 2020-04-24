# CNV checkpoint
Group 6:
Diogo Fernandes 86410, Gabriel Figueira 86426, João Margaço 86451

## System Architecture

Our system is divided into different folders:

1. The BIT folder, and more specifically the BIT package which contains the HighBIT and LowBIT folders and OurTool with the necessary code to instrument the Solver's code. OurTool will store metrics in a synchronous way (dependent on the Thread ID) and offer static methods to WebServer class to access those same metrics.
1. DatabaseScripts which contains our auxiliary tools in order for us to enumerate the DynamoDB tables and reset its state (dynamodb.local.py through BOTO3 installed with pip3) and in the case we need to test locally we create and run the DynamoDB docker image through a script (dockerScript.sh).
1. The instrumented folder which will contain the instrumented code, which is done by running OurTool with the Solver classes as input.
1. The Project folder which will contain the project source code, and more specifically our changed version of the WebServer class, which periodically requests (statically) OurTool for one specificic request in a synchronous way, and stores it in the DynamoDB database.

## System Configurations

### Instance
1. We used the Amazon-Linux AMI 2, subnet = us-east-1a.
1. Enable CloudWatch detailed monitoring.
1. Add the default storage.
1. Created a security group with SSH(port 22) and HTTP(port 80) rules.


In terms of the code:
1. We created a copy of our project and changed the dynamoDB communication from a local perspective with the IP to the dynamoDB of the AWS. 
1. Downloaded the latest version of AWS-java-sdk in the current moment.
1. Created a makefile with both make and make run instructions for ease of testing.
1. Edited the etc/rc.local to have this structure

### LoadBalancer
1. Traffic: Load balancer protocol = HTTP, Load Balancer Port = 80, Instance Protocol = HTTP and Instance Port = 8000
In order to forward traffic from port 80 to 8000.
1. Subnet: us-east-1a.
1. Security Groups: TCP with Port Range = 80.
1. Configured health check to Ping Path = /test, Ping Protocol = HTTP and Ping Port = 8000. And created a new handler in the WebServer code in order to be compatible with this health check.
1. We didnt add extra instances.
1. We didnt choose any tags.

### AutoScaler

1. We have chosen the previously created image of the instance containing the project code.
1. We enabled CloudWatch detailed monitoring
1. We used the default storage given by AWS, with 8 gb.
1. We picked the previously security group created for the instance, with a rule for SSH and HTTP.
1. We set the group size to 1 and the max size to 10, with the same subnet as the subnet given to the other instances, set the previous Load Balancer and set the Heath Check Type to ELB with a grace period of 60 seconds.
1. Created two group rules, one Increase Group Rule with an alarm that creates a new instance after the CPU utilization increases behond 60%. And one Decrease Group Rule with an alartm that destroys an instance after the CPU utilization reduces to a level smaller than 40%.

