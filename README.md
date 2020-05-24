# CNV checkpoint
Group 6:
Diogo Fernandes 86410, Gabriel Figueira 86426, João Margaço 86451

## System Architecture

Our system is divided into different folders:

1. The BIT folder, and more specifically the BIT package which contains the HighBIT and LowBIT folders and OurTool with the necessary code to instrument the Solver's code. OurTool will store metrics in a synchronous way (dependent on the Thread ID) and offer static methods to WebServer class to access those same metrics.
2. DatabaseScripts which contains our auxiliary tools in order for us to enumerate the DynamoDB tables and reset its state (dynamodb.local.py through BOTO3 installed with pip3) and in the case we need to test locally, we create and run the DynamoDB docker image through a script (dockerScript.sh).
3. The instrumented folder which will contain the instrumented code, which is done by running OurTool with the Solver classes as input.
4. The Project folder which will contain the project source code, and more specifically our changed version of the WebServer class, the LoadBalancer and the AutoScaler.

## System Configurations

### Instance
1. We used the Amazon-Linux AMI 2, subnet = us-east-1a.
2. Enable CloudWatch detailed monitoring.
3. Add the default storage.
4. Created a security group with SSH(port 22) and HTTP(port 80) rules.


In terms of the code:

5. We created a copy of our project and changed the dynamoDB communication from a local perspective with the IP to the dynamoDB of the AWS. 
6. Downloaded the latest version of AWS-java-sdk in the current moment.
7. Created a makefile with both make and make run instructions for ease of testing.
8. Edited the etc/rc.local to have this structure.

```
CLASSPATH=/home/ec2-user/cnv/instrumented:/home/ec2-user/cnv/project:/home/ec2-user/cnv/BIT:/home/ec2-user/cnv/BIT/samples:/home/ec2-user/aws-java-sdk-1.11.764/lib/aws-java-sdk-1.11.764.jar:/home/ec2-user/aws-java-sdk-1.11.764/third-party/lib/*:.
export CLASSPATH
cd /home/ec2-user/cnv
make
make run
```

### LoadBalancer & AutoScaler

The LoadBalancer and the AutoScaler share the same instance. To run them, execute `make scaler`. This will start them and create an initial WebServer instance.

