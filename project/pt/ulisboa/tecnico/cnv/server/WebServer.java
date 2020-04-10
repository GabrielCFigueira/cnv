package pt.ulisboa.tecnico.cnv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

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


public class WebServer {

	public static void main(final String[] args) throws Exception {

		//final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);



		server.createContext("/sudoku", new MyHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
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
	static class MyHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {

			// Get the query.
			final String query = t.getRequestURI().getQuery();
			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);
			}
			newArgs.add("-b");
			newArgs.add(parseRequestBody(t.getRequestBody()));

			newArgs.add("-d");

			// Store from ArrayList into regular String[].
			final String[] args = new String[newArgs.size()];
			int i = 0;
			for(String arg: newArgs) {
				args[i] = arg;
				i++;
			}
			// Get user-provided flags.
			final SolverArgumentParser ap = new SolverArgumentParser(args);

			// Create solver instance from factory.
			final Solver s = SolverFactory.getInstance().makeSolver(ap);

			//Solve sudoku puzzle
			JSONArray solution = s.solveSudoku();


			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

            //t.sendResponseHeaders(200, responseFile.length());


			///hdrs.add("Content-Type", "image/png");
            hdrs.add("Content-Type", "application/json");

			hdrs.add("Access-Control-Allow-Origin", "*");

            hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

            t.sendResponseHeaders(200, solution.toString().length());


            final OutputStream os = t.getResponseBody();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            osw.write(solution.toString());
            osw.flush();
            osw.close();

			os.close();


			try{
				AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(
					new AwsClientBuilder.EndpointConfiguration("http://localhost:8043", "eu-west-1"))
					.build();
				DynamoDB dynamoDB = new DynamoDB(client);

				//Query for the greatest requestId for the thread
				Table table = dynamoDB.getTable("requests_data");
				QuerySpec spec = new QuerySpec().withKeyConditionExpression("ThreadId = :t_id and RequestId > :r_id").withValueMap(new ValueMap()
									.withString(":t_id",Long.toString(Thread.currentThread().getId())).withNumber(":r_id",0))
									.withScanIndexForward(false);

				ItemCollection<QueryOutcome> items = table.query(spec);

				Iterator<Item> iterator = items.iterator();
				Item item = null;
				int highestRequestId = 0; 
				while (iterator.hasNext()){
					item = iterator.next();
					highestRequestId = Integer.parseInt(item.getString("RequestId"));
					System.out.println("Id:" + highestRequestId);
					break;
				}

				//Update the entry with the requestId and threadId
				Map<String, AttributeValue> item_key = new HashMap<String, AttributeValue>();
				item_key.put("ThreadId",new AttributeValue(Long.toString(Thread.currentThread().getId())));
				item_key.put("RequestId",new AttributeValue().withN(Integer.toString(highestRequestId)));

				Map<String, AttributeValueUpdate> expressionAttributeValues = new HashMap<String, AttributeValueUpdate>();
				expressionAttributeValues.put("lines", new AttributeValueUpdate(new AttributeValue().withN(args[5]),AttributeAction.PUT));  // update lines
				expressionAttributeValues.put("columns", new AttributeValueUpdate(new AttributeValue().withN(args[7]),AttributeAction.PUT));  // update columns
				expressionAttributeValues.put("Unassigned", new AttributeValueUpdate(new AttributeValue().withN(args[3]),AttributeAction.PUT));  // update unassigned
				expressionAttributeValues.put("Algorithm", new AttributeValueUpdate(new AttributeValue(args[1]),AttributeAction.PUT));  // update algorithm

				client.updateItem("requests_data",item_key,expressionAttributeValues);

				//Scan for the tables data
				HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
				Condition condition = new Condition()
					.withComparisonOperator(ComparisonOperator.GE.toString())
					.withAttributeValueList(new AttributeValue("1"));
				scanFilter.put("ThreadId", condition);
				ScanRequest scanRequest = new ScanRequest("requests_data").withScanFilter(scanFilter);
				ScanResult scanResult = client.scan(scanRequest);
				System.out.println("Result: " + scanResult);
			}
			catch (AmazonServiceException ase) {
				System.out.println("Caught an AmazonServiceException, which means your request made it "
						+ "to AWS, but was rejected with an error response for some reason.");
				System.out.println("Error Message:    " + ase.getMessage());
				System.out.println("HTTP Status Code: " + ase.getStatusCode());
				System.out.println("AWS Error Code:   " + ase.getErrorCode());
				System.out.println("Error Type:       " + ase.getErrorType());
				System.out.println("Request ID:       " + ase.getRequestId());
			} catch (AmazonClientException ace) {
				System.out.println("Caught an AmazonClientException, which means the client encountered "
						+ "a serious internal problem while trying to communicate with AWS, "
						+ "such as not being able to access the network.");
				System.out.println("Error Message: " + ace.getMessage());
			}

			System.out.println("> Sent response to " + t.getRemoteAddress().toString());
		}
	}
}
