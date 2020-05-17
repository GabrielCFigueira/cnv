package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
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

import java.util.Map;

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

		System.out.println("Starting a new instance.");
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		runInstancesRequest.withImageId("ami-0ed269018a8846ae4")
			.withInstanceType("t2.micro")							
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-lab-AWS")		
			.withSecurityGroups("CNV-ssh+http");	
					
   		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		_instances.put(runInstancesResult.getReservation().getInstances().get(0), true);
													
	}

	public void run() {



	}
}
