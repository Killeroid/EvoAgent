package EvoAgent;
import java.util.List;
import com.sun.tools.attach.*;





public class ASM {

	private static Agent a;
	private static String PID;
	private static boolean Debug = true;


	public ASM() {
		
	}


	private static void printHelp() {
		System.out.println("AgentJ Usage Options " + System.getProperty("line.separator")
				
				+ System.getProperty("line.separator")
				+ "  -help             displays this message" + System.getProperty("line.separator")
				+ "  -pid              pid of process" + System.getProperty("line.separator")
				+ "                        \"-help pid\" for more help "
				+ System.getProperty("line.separator")
				+ "  -configuration    the configuration file path"
				+ System.getProperty("line.separator")
				+ "  -extinction       run extinction sequence"
				+ System.getProperty("line.separator")
				+ "  -relink           relink during extinction"
				+ System.getProperty("line.separator")
				+ "  -debug            print debug messages"
				+ System.getProperty("line.separator")
				
		);
		
	}
	
	private static void pidHelp() {
		System.out.println("To get full list of JVM processes, use command...\n");
		System.out.println(">>   jps [options] [hostid]\n");
		
		List<VirtualMachineDescriptor> vms = VirtualMachine.list();

		if (vms.size() > 0) {
			System.out.println("Sample of JVM processes currently running\n");
			System.out.printf("%-10s %-55s\n", "PROCESS ID", "VM DISPLAY NAME");
			System.out.printf("%-10s %-55s\n", "----------", "---------------");
			for(VirtualMachineDescriptor vm : vms){
				System.out.printf("%-10s %-55s\n", vm.id(), vm.displayName());
				//System.out.println(vm.id());
			}
		}
		
	}

	public static void main(String[] args) {

		if (args.length <= 0) {
			printHelp();
			System.exit(1);
		} else {
			boolean exit = false;
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("-help")) {
					if ((i + 1) < args.length && args[i+1].equals("pid")) {
						pidHelp();
						System.exit(0);
					}
					exit = true;
					break;
				} else if (args[i].equals("-pid")) {
					if ((((i + 1) < args.length) && args[i+1].startsWith("-")) ||
							(i + 1) >= args.length) {
						exit = true;
						System.out.println("[ERROR] Invalid PID given");
						System.out.println("[ERROR] Instrumentation NOT started\n");
						break;
					}
					PID = args[i + 1];
					a = new Agent(PID);
					i++;
					continue;
				} else if (args[i].equals("-nodebug")) {
					if ((i + 1) < args.length && !args[i+1].startsWith("-")) {
						exit = true;
						System.out.println("[ERROR] nodebug requires no options");
						System.out.println("[ERROR] Instrumentation NOT started\n");
						break;
					} 
					Debug = true;
					continue;
					
				} else {  //If arg not handled, print help and exit
					//printHelp();
					exit = true;
					System.out.println("[ERROR] UNKNOWN options passed");
					System.out.println("[ERROR] Instrumentation NOT started\n");
					break;
				}
			}

			if (exit) {
				printHelp();
				System.exit(1);
			}
		} 
		
		

		Agent.setDebug(Debug);
		boolean agentLoaded = a.loadAgent();
		
		if (agentLoaded && Debug) {
			System.out.println("[SUCCESS] Instrumentation started");
		} else if (!agentLoaded && Debug) {
			System.out.println("[ERROR] Instrumentation failed");
		}
		
		System.exit(0);
	}

}