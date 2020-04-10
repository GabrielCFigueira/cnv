
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

	private int currentRequestId = 0;

	private int dyn_method_count = 0;
	private int dyn_bb_count = 0;
	private int dyn_instr_count = 0;
	
	private int newcount = 0;
	private int newarraycount = 0;
	private int anewarraycount = 0;
	private int multianewarraycount = 0;

	private int loadcount = 0;
	private int storecount = 0;
	private int fieldloadcount = 0;
	private int fieldstorecount = 0;

	private int branchtaken;

	}

	static AtomicInteger globalRequestId = new AtomicInteger();

	private static Map<String, AttributeValue> newItem(long threadId, int allocations, int loadsStores,
			int ninstructions, int branchtaken, int requestId) {
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("RequestId", new AttributeValue().withN(Integer.toString(requestId)));
		item.put("ThreadId", new AttributeValue(Long.toString(threadId)));
		item.put("allocations", new AttributeValue().withN(Integer.toString(allocations)));
		item.put("loadsStores", new AttributeValue().withN(Integer.toString(loadsStores)));
		item.put("lines", new AttributeValue().withN(Integer.toString(0)));
		item.put("columns", new AttributeValue().withN(Integer.toString(0)));
		item.put("Unassigned", new AttributeValue().withN(Integer.toString(0)));
		item.put("Algorithm", new AttributeValue("empty"));
		item.put("InstructionCount", new AttributeValue().withN(Integer.toString(ninstructions)));
		item.put("BranchesTaken", new AttributeValue().withN(Integer.toString(branchtaken)));
		return item;
	}

	private static ConcurrentMap<Long, StatisticsData> _data = new ConcurrentHashMap<Long, StatisticsData>();

	public static void printUsage() {
		System.out.println("Syntax: java OurTool in_path out_path");
		System.out.println("        where stat_type can be:");
		System.out.println();
		System.out.println("        in_path:  directory from which the class files are read");
		System.out.println("        out_path: directory to which the class files are written");
		System.out.println("        Both in_path and out_path are required unless stat_type is static");
		System.out.println("        in which case only in_path is required");
		System.exit(-1);
	}

	public static void initialize(String dummy) {
		_data.put(Thread.currentThread().getId(), new StatisticsData());
		_data.get(Thread.currentThread().getId()).currentRequestId = globalRequestId.incrementAndGet();
	}
	
    public static synchronized void printDynamic(String foo) 
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			System.out.println("Dynamic information summary:");
			System.out.println("Number of methods:      " + data.dyn_method_count);
			System.out.println("Number of basic blocks: " + data.dyn_bb_count);
			System.out.println("Number of instructions: " + data.dyn_instr_count);
		
			if (data.dyn_method_count == 0) {
				return;
			}
		
			float instr_per_bb = (float) data.dyn_instr_count / (float) data.dyn_bb_count;
			float instr_per_method = (float) data.dyn_instr_count / (float) data.dyn_method_count;
			float bb_per_method = (float) data.dyn_bb_count / (float) data.dyn_method_count;
		
			System.out.println("Average number of instructions per basic block: " + instr_per_bb);
			System.out.println("Average number of instructions per method:      " + instr_per_method);
			System.out.println("Average number of basic blocks per method:      " + bb_per_method);
		}
    

    public static synchronized void dynInstrCount(int incr) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.dyn_instr_count += incr;
			data.dyn_bb_count++;
		}

    public static synchronized void dynMethodCount(int incr) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.dyn_method_count++;
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
						ci.addBefore("OurTool", "initialize", "null");

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();

						if(routine.getMethodName().equals("solveSudoku")) {
							routine.addAfter("OurTool", "printDynamic", "null");
							routine.addAfter("OurTool", "printLoadStore", "null");
							routine.addAfter("OurTool", "printAlloc", "null");
							routine.addAfter("OurTool", "printBranch", "null");
						}
						else if(routine.getMethodName().equals("main"))
							routine.addBefore("OurTool", "initialize", "null");
						
						routine.addBefore("OurTool", "dynMethodCount", new Integer(1)); 

						InstructionArray instructions = routine.getInstructionArray();
						
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("OurTool", "dynInstrCount", new Integer(bb.size()));
							
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION)
								instr.addBefore("OurTool", "updateBranchOutcome", "BranchOutcome");
						}

		  
						for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
							Instruction instr = (Instruction) instrs.nextElement();
							int opcode=instr.getOpcode();
							if ((opcode==InstructionTable.NEW) ||
								(opcode==InstructionTable.newarray) ||
								(opcode==InstructionTable.anewarray) ||
								(opcode==InstructionTable.multianewarray)) {
								instr.addBefore("OurTool", "allocCount", new Integer(opcode));
							}
							else if (opcode == InstructionTable.getfield)
								instr.addBefore("OurTool", "LSFieldCount", new Integer(0));
							else if (opcode == InstructionTable.putfield)
								instr.addBefore("OurTool", "LSFieldCount", new Integer(1));
							else {
								short instr_type = InstructionTable.InstructionTypeTable[opcode];
								if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
									instr.addBefore("OurTool", "LSCount", new Integer(0));
								}
								else if (instr_type == InstructionTable.STORE_INSTRUCTION) {
									instr.addBefore("OurTool", "LSCount", new Integer(1));
								}
							}
						}
					}
					ci.write(out_filename);
				}
			}
		}

	public static synchronized void printAlloc(String s) 
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			System.out.println("Allocations summary:");
			System.out.println("new:            " + data.newcount);
			System.out.println("newarray:       " + data.newarraycount);
			System.out.println("anewarray:      " + data.anewarraycount);
			System.out.println("multianewarray: " + data.multianewarraycount);
		}

	public static synchronized void allocCount(int type)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			switch(type) {
			case InstructionTable.NEW:
				data.newcount++;
				break;
			case InstructionTable.newarray:
				data.newarraycount++;
				break;
			case InstructionTable.anewarray:
				data.anewarraycount++;
				break;
			case InstructionTable.multianewarray:
				data.multianewarraycount++;
				break;
			}
		}
	

	public static synchronized void printLoadStore(String s) 
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			System.out.println("Load Store Summary:");
			System.out.println("Field load:    " + data.fieldloadcount);
			System.out.println("Field store:   " + data.fieldstorecount);
			System.out.println("Regular load:  " + data.loadcount);
			System.out.println("Regular store: " + data.storecount);
		}

	public static synchronized void LSFieldCount(int type) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			if (type == 0)
				data.fieldloadcount++;
			else
				data.fieldstorecount++;
		}

	public static synchronized void LSCount(int type) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			if (type == 0)
				data.loadcount++;
			else
				data.storecount++;
		}

	

	public static synchronized void updateBranchOutcome(int br_outcome)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			if (br_outcome != 0)
				data.branchtaken++;
			
			
		}

	public static synchronized void printBranch(String foo)
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			System.out.println("Branch summary:");
			System.out.println("Branches taken: " + data.branchtaken);
			

        int allocations = data.newcount + data.newarraycount + data.anewarraycount + data.multianewarraycount;
        long threadId = Thread.currentThread().getId();
        int loadsStores = data.storecount + data.fieldstorecount + data.loadcount + data.fieldloadcount;
		int ninstructions = data.dyn_instr_count;
		int branchtaken = data.branchtaken;
		int requestId = data.currentRequestId;

        try{
            AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration("http://localhost:8043", "eu-west-1"))
                .build();
    
            String tableName = "requests_data";

            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName("ThreadId").withKeyType(KeyType.HASH), new KeySchemaElement().withAttributeName("RequestId").withKeyType(KeyType.RANGE))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName("ThreadId").withAttributeType(ScalarAttributeType.S), new AttributeDefinition().withAttributeName("RequestId").withAttributeType(ScalarAttributeType.N))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));
    
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            TableUtils.waitUntilActive(dynamoDB, tableName);
    
            Map<String, AttributeValue> item = newItem(threadId,allocations,loadsStores,ninstructions,branchtaken,requestId);
            PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
            PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);

        } catch (AmazonServiceException ase) {
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
        catch(InterruptedException e){
			System.out.println("It may have been interrupted");
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
