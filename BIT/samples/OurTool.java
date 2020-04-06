
import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class OurTool 
{

	private static class StatisticsData {

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

	private StatisticsBranch[] branch_info;
	private int branch_number;
	private int branch_pc;
	private String branch_class_name;
	private String branch_method_name;

	}

	private static ConcurrentMap<Long, StatisticsData> _data = new ConcurrentHashMap<Long, StatisticsData>();

	public static void printUsage() 
		{
			System.out.println("Syntax: java OurTool -stat_type in_path [out_path]");
			System.out.println("        where stat_type can be:");
			System.out.println("        dynamic:    dynamic properties");
			System.out.println("        alloc:      memory allocation instructions");
			System.out.println("        load_store: loads and stores (both field and regular)");
			System.out.println("        branch:     gathers branch outcome statistics");
			System.out.println();
			System.out.println("        in_path:  directory from which the class files are read");
			System.out.println("        out_path: directory to which the class files are written");
			System.out.println("        Both in_path and out_path are required unless stat_type is static");
			System.out.println("        in which case only in_path is required");
			System.exit(-1);
		}


	public static void initialize(String dummy) {
		long id = Thread.currentThread().getId();

		if(!_data.containsKey(id))
			_data.put(id, new StatisticsData());
	}


	public static void doDynamic(File in_dir, File out_dir) 
		{
			String filelist[] = in_dir.list();
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);
					
					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
					
						if(routine.getMethodName().equals("solveSudoku"))
							routine.addAfter("OurTool", "printDynamic", "null");
						else if(routine.getMethodName().equals("main") || routine.getMethodName().equals("SolverArgumentParser") || routine.getMethodName().equals("<init>") || routine.getMethodName().equals("<clinit>"))
							routine.addBefore("OurTool", "initialize", "null");
						
						routine.addBefore("OurTool", "dynMethodCount", new Integer(1));
                    
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("OurTool", "dynInstrCount", new Integer(bb.size()));
						}
					}
					ci.write(out_filename);
				}
			}
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
	
	public static void doAlloc(File in_dir, File out_dir)
		{
			String filelist[] = in_dir.list();
			int k = 0;
			int total = 0;
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						InstructionArray instructions = routine.getInstructionArray();
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								total++;
							}
						}
					}
				}
			}

			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);
					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
		
						if(routine.getMethodName().equals("solveSudoku")) {
							routine.addAfter("OurTool", "printDynamic", "null");
							routine.addAfter("OurTool", "printLoadStore", "null");
							routine.addAfter("OurTool", "printAlloc", "null");
							routine.addAfter("OurTool", "printBranch", "null");
						}
						else if(routine.getMethodName().equals("main") || routine.getMethodName().equals("SolverArgumentParser") || routine.getMethodName().equals("<init>") || routine.getMethodName().equals("<clinit>"))
							routine.addBefore("OurTool", "initialize", "null");
						
						routine.addBefore("OurTool", "dynMethodCount", new Integer(1)); 
						routine.addBefore("OurTool", "setBranchMethodName", routine.getMethodName());

						InstructionArray instructions = routine.getInstructionArray();
						
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("OurTool", "dynInstrCount", new Integer(bb.size()));
							
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								instr.addBefore("OurTool", "setBranchPC", new Integer(instr.getOffset()));
								instr.addBefore("OurTool", "updateBranchNumber", new Integer(k));
								instr.addBefore("OurTool", "updateBranchOutcome", "BranchOutcome");
								k++;
							}
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
					ci.addBefore("OurTool", "setBranchClassName", ci.getClassName());
					ci.addBefore("OurTool", "branchInit", new Integer(total));
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
	
	public static void doLoadStore(File in_dir, File out_dir) 
		{
			String filelist[] = in_dir.list();
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);
					
					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						
						if(routine.getMethodName().equals("solveSudoku"))
							routine.addAfter("OurTool", "printDynamic", "null");
						else if(routine.getMethodName().equals("main") || routine.getMethodName().equals("SolverArgumentParser") || routine.getMethodName().equals("<init>") || routine.getMethodName().equals("<clinit>"))
							routine.addBefore("OurTool", "initialize", null);
						
						for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); ) {
							Instruction instr = (Instruction) instrs.nextElement();
							int opcode=instr.getOpcode();
							if (opcode == InstructionTable.getfield)
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
	
	public static void doBranch(File in_dir, File out_dir) 
		{
			String filelist[] = in_dir.list();
			int k = 0;
			int total = 0;
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						InstructionArray instructions = routine.getInstructionArray();
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								total++;
							}
						}
					}
				}
			}
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						
						if(routine.getMethodName().equals("solveSudoku"))
							routine.addAfter("OurTool", "printDynamic", "null");
						else if(routine.getMethodName().equals("main") || routine.getMethodName().equals("SolverArgumentParser") || routine.getMethodName().equals("<init>") || routine.getMethodName().equals("<clinit>"))
							routine.addBefore("OurTool", "initialize", null);
						
						routine.addBefore("OurTool", "setBranchMethodName", routine.getMethodName());
						InstructionArray instructions = routine.getInstructionArray();
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								instr.addBefore("OurTool", "setBranchPC", new Integer(instr.getOffset()));
								instr.addBefore("OurTool", "updateBranchNumber", new Integer(k));
								instr.addBefore("OurTool", "updateBranchOutcome", "BranchOutcome");
								k++;
							}
						}
					}
					ci.addBefore("OurTool", "setBranchClassName", ci.getClassName());
					ci.addBefore("OurTool", "branchInit", new Integer(total));
					ci.write(out_filename);
				}
			}	
		}

	public static synchronized void setBranchClassName(String name)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.branch_class_name = name;
		}

	public static synchronized void setBranchMethodName(String name) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.branch_method_name = name;
		}
	
	public static synchronized void setBranchPC(int pc)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.branch_pc = pc;
		}
	
	public static synchronized void branchInit(int n) 
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			if (data.branch_info == null) {
				data.branch_info = new StatisticsBranch[n];
			}
		}

	public static synchronized void updateBranchNumber(int n)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			data.branch_number = n;
			
			if (data.branch_info[data.branch_number] == null) {
				data.branch_info[data.branch_number] = new StatisticsBranch(data.branch_class_name, data.branch_method_name, data.branch_pc);
			}
		}

	public static synchronized void updateBranchOutcome(int br_outcome)
		{
			
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			if (br_outcome == 0) {
				data.branch_info[data.branch_number].incrNotTaken();
			}
			else {
				data.branch_info[data.branch_number].incrTaken();
			}
		}

	public static synchronized void printBranch(String foo)
		{
			StatisticsData data = _data.get(Thread.currentThread().getId());
			
			System.out.println("Branch summary:");
			System.out.println("CLASS NAME" + '\t' + "METHOD" + '\t' + "PC" + '\t' + "TAKEN" + '\t' + "NOT_TAKEN");
			
			for (int i = 0; i < data.branch_info.length; i++) {
				if (data.branch_info[i] != null) {
					data.branch_info[i].print();
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
					doAlloc(in_dir, out_dir);
				
				}	
				else {	
					printUsage();
				}
			} catch (NullPointerException e) {
				printUsage();
			}
		
		}
}
