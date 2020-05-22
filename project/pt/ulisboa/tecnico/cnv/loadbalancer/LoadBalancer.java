package pt.ulisboa.tecnico.cnv.loadbalancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import java.util.UUID;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
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
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.util.EC2MetadataUtils;

public class LoadBalancer {

	private static ConcurrentHashMap<Instance, Boolean> _instances = new ConcurrentHashMap<Instance, Boolean>();
	private static Map<String, Map<String,AttributeValue>> _history = new HashMap<String, Map<String, AttributeValue>>();
	private static AmazonDynamoDB dynamoDB;

	private static long heuristicCoeficient = 6759716;
	public static long heuristic(long unassigned) {
		return heuristicCoeficient * unassigned;
	}

	public static void main(final String[] args) throws Exception {

		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
			"Cannot load the credentials from the credential profiles file. " +
			"Please make sure that your credentials file is at the correct " +
			"location (~/.aws/credentials), and is in valid format.",
			e);
		}

	        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            		.withCredentials(credentialsProvider)
            		.withRegion("us-east-1")
            		.build();

		//Initialize table in dynamoDB
		String tableName = "requests_data";

		CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
			.withKeySchema(new KeySchemaElement().withAttributeName("RequestId").withKeyType(KeyType.HASH))
			.withAttributeDefinitions(new AttributeDefinition().withAttributeName("RequestId").withAttributeType(ScalarAttributeType.S))
			.withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

		TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
		TableUtils.waitUntilActive(dynamoDB, tableName);

		//Initialize http handler
		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/sudoku", new RedirectHandler());

		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		//Initialize AutoScaler and run it periodically
		final AutoScaler as = new AutoScaler(_instances);

		System.out.println(server.getAddress().toString());


			
		Thread thread = new Thread(){	
			public void run(){
				try{		
					while(true) {	
						Thread.sleep(1000);
						as.run();
					}				
				} catch(InterruptedException e){	
					e.printStackTrace();
					System.exit(-1);
				}
			}
		};
		thread.start();
	}

	public static String parseRequestBody(InputStream is) throws IOException {
        InputStreamReader isr =  new InputStreamReader(is,"utf-8");
        BufferedReader br = new BufferedReader(isr);

        // From now on, the right way of moving from bytes to utf-8 characters:

        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);

        }

        br.close();
        isr.close();

        return buf.toString();
    }

    static class RedirectHandler implements HttpHandler {
      
        @Override
        public void handle(HttpExchange t) throws IOException {
		try {	
			String uniqueId = UUID.randomUUID().toString();
			URL url = null;
			
			synchronized(_instances) {
				List<Instance> keys = new ArrayList<>(_instances.keySet());	
				Instance instance = keys.get((int) (Math.random() * (keys.size() - 1)));

				//heuristic
				String query = t.getRequestURI().getQuery();
				String[] params = query.split("&");
				long unassigned = 0;
				for (String s : params) {
					String[] split = s.split("=");
					if(split[0].equals("un")) {
						unassigned = Integer.parseInt(split[1]);
						break;
					}
				}
				long estimate = heuristic(unassigned);
					
				url = new URL("http://" + instance.getPublicDnsName() + ":8000/sudoku?" + t.getRequestURI().getQuery() + "&e=" + estimate + "&k=" + uniqueId );
			}
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			//In order to request a server
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept", "*/*");
			con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");

			OutputStream outStream = con.getOutputStream();
			OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "UTF-8");
			String s = parseRequestBody(t.getRequestBody());
			System.out.println("Board: "+ s);
			outStreamWriter.write(s);
			outStreamWriter.flush();
			outStreamWriter.close();
			outStream.close();

			//In order to obtain the response of the server
			BufferedReader in = new BufferedReader(
			new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}
			in.close();
			System.out.println(content.toString());

			//Save request in cache
			HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
			expressionAttributeValues.put(":requestId", new AttributeValue(uniqueId)); 
			
			ScanRequest scanRequest = new ScanRequest()
				.withTableName("requests_data")
				.withFilterExpression(":requestId = RequestId")
				.withExpressionAttributeValues(expressionAttributeValues);
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			
			for (Map<String, AttributeValue> item : scanResult.getItems()){
     				for(String sItem : item.keySet())
					System.out.println(sItem);
				_history.put(uniqueId,item);
			}

			//In order to return to the client
			final Headers hdrs = t.getResponseHeaders();

			hdrs.add("Content-Type", "application/json");

			hdrs.add("Access-Control-Allow-Origin", "*");

			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

			t.sendResponseHeaders(200, content.toString().length());


			final OutputStream os = t.getResponseBody();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
			osw.write(content.toString());
			osw.flush();
			osw.close();

			os.close();
			

			con.disconnect();

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
        }
      }

	

	
}
