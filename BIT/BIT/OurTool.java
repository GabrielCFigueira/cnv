package BIT;

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.HashMap;
import java.util.Map;

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

public class OurTool 
{

	private static class StatisticsData {

	private long dyn_instr_count = 0;
	
	private boolean isItFinished = false;

	}

	public static synchronized String getStatisticsData(long threadId){
		StatisticsData data = _data.get(threadId);

		long ninstructions = data.dyn_instr_count;
		boolean isItFinished = data.isItFinished;
		String arguments = "" + ninstructions;
		return arguments;
	} 

	public static synchronized boolean hasTaskFinished(long threadId){
		return _data.get(threadId).isItFinished;
	}

	private static ConcurrentMap<Long, StatisticsData> _data = new ConcurrentHashMap<Long, StatisticsData>();

	public static void printUsage() {
		System.out.println("Syntax: java OurTool in_path out_path");
		System.out.println("        where stat_type can be:");
		System.out.println();
		System.out.println("        in_path:  directory from which the class files are read");
		System.out.println("        out_path: directory to which the class files are written");
		System.exit(-1);
	}

	public static void initialize(String dummy) {
		_data.put(Thread.currentThread().getId(), new StatisticsData());
	}

	public static void end(String dummy) {
			StatisticsData data = _data.get(Thread.currentThread().getId());
			data.isItFinished = true;
	}
	
    public static synchronized void printDynamic(String foo) 
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			System.out.println("Number of instructions: " + data.dyn_instr_count);
		}
    

    public static synchronized void dynInstrCount(int incr) 
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			data.dyn_instr_count += incr;
		}

	public static void doInstrumentation(File in_dir, File out_dir)
		{
			String filelist[] = in_dir.list();

			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					if(ci.getClassName().matches(".*SolverArgumentParser"))
						ci.addBefore("BIT/OurTool", "initialize", "null");

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();

						if(routine.getMethodName().equals("solveSudoku")) {
							routine.addAfter("BIT/OurTool", "end", "null");
							routine.addAfter("BIT/OurTool", "printDynamic", "null");
						}
						else if(routine.getMethodName().equals("main"))
							routine.addBefore("BIT/OurTool", "initialize", "null");
						

						
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("BIT/OurTool", "dynInstrCount", new Integer(bb.size()));
							
						}

					}
					ci.write(out_filename);
				}
			}
		}

			
	public static void main(String argv[]) 
		{
			if (argv.length != 2 || argv[0].startsWith("-")) {
				printUsage();
			}
	
			try {
				File in_dir = new File(argv[0]);
				File out_dir = new File(argv[1]);
	
				if (in_dir.isDirectory() && out_dir.isDirectory()) {
					doInstrumentation(in_dir, out_dir);
				
				}	
				else {	
					printUsage();
				}
			} catch (NullPointerException e) {
				printUsage();
			}
		
		}
}
