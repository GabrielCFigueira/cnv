package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.waiters.*; 

import java.util.Map;
import java.util.List;
import java.util.HashMap;


public class AutoScaler {

	private Map<Instance, Boolean> _instances;
	private AmazonEC2 ec2;
    	private AmazonCloudWatch cloudWatch;

	public void init() {
		AWSCredentials credentials = null;
        	try {
			credentials = new ProfileCredentialsProvider().getCredentials();			
    		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. " +
					"Please make sure that your credentials file is at the correct " +
					"location (~/.aws/credentials), and is in valid format.",
					e);
		}

		ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();	
	}

	public AutoScaler(Map<Instance, Boolean> instances) {
		init();
		_instances = instances;
		createInstance();
													
	}

	public void run() {

		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();		
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. Please make sure that your credentials file is at the correct location (~/.aws/credentials), and is in valid format.", e);
		}
	
		AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()	
			.withCredentials(credentialsProvider)
			.withRegion("us-east-1")
			.build();

		HashMap<String, Long> systemLoad = new HashMap<String, Long>();
		long nInstances = 0;
		long minLoad = Long.MAX_VALUE;
		Instance minInstance = null;
		
		for (Instance instance : _instances.keySet()) {
			HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			Condition condition1 = new Condition()
				.withComparisonOperator(ComparisonOperator.EQ.toString())
				.withAttributeValueList(new AttributeValue("0"));
			Condition condition2 = new Condition()
				.withComparisonOperator(ComparisonOperator.EQ.toString())
				.withAttributeValueList(new AttributeValue(instance.getInstanceId()));
		        scanFilter.put("InstanceId", condition2);
		        scanFilter.put("Finished", condition1);
			ScanRequest scanRequest = new ScanRequest("requests_data").withScanFilter(scanFilter);
		
			System.out.println(instance.getInstanceId() + ":");			
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			
			boolean hasRequests = false;
			long load = 0;
			for (Map<String, AttributeValue> item: scanResult.getItems()){
				hasRequests = true;
				long progress = Long.parseLong(item.get("InstructionCount").getN());
				long estimate = Long.parseLong(item.get("Estimate").getN());
				if (estimate > progress)
					load += estimate - progress;
				System.out.println("progress " + progress);
				System.out.println("estimate " + estimate);
			}
			if (minLoad > load) {
				minLoad = load;
				minInstance = instance;
			}	
			if(_instances.get(instance) == false && !hasRequests)
				destroyInstance(instance);
			else if (_instances.get(instance) == true) {
				nInstances++;
				System.out.println("load " + load);
				systemLoad.put(instance.getInstanceId(), load);
			}
		}
	
		long totalLoad = 0;
		for(long load : systemLoad.values())
			totalLoad += load;


		System.out.println("totalLoad " + totalLoad + "\n");
		if (totalLoad / nInstances > 100000000000L)
			createInstance();
		else if (totalLoad / nInstances < 10000000000L && nInstances > 1)
			_instances.put(minInstance, false);
	}

	public void createInstance() {

		System.out.println("Starting a new instance.");
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-01962f036a9d3e618")
			.withInstanceType("t2.micro")							
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-lab-AWS")		
			.withSecurityGroups("CNV-ssh+http");	
					
   		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		String instanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

		DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
		ec2.waiters().instanceRunning().run(new WaiterParameters().withRequest(request));


		DescribeInstancesResult describeInstancesResult = ec2.describeInstances((new DescribeInstancesRequest()).withInstanceIds(instanceId));
		List<Reservation> reservations = describeInstancesResult.getReservations();
		_instances.put(reservations.get(0).getInstances().get(0), true);
	}


	public void destroyInstance(Instance instance) {

		System.out.println("Terminating instance.");
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		TerminateInstancesRequest request = new TerminateInstancesRequest()
			    .withInstanceIds(instance.getInstanceId());

		ec2.terminateInstances(request);
		_instances.remove(instance);
	}
}
