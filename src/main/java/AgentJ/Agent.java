package AgentJ;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import AgentJ.Transformers.*;
import AgentJ.Utils.*;

import com.sun.tools.attach.*;
import com.sun.tools.attach.spi.*;

import sun.tools.attach.*;

import org.objectweb.asm.*;
import org.apache.commons.io.*;







/**
 * Simple agent to get access to the Instrumentation API.
 */
public final class Agent {

	private static String jarFilePath;
	public static volatile Instrumentation INSTRUMENTATION;
	private final String JVM_ID;

	private boolean Debug;


	//How to check if agent is loaded
	//http://stackoverflow.com/questions/482633/in-java-is-it-possible-to-know-whether-a-class-has-already-been-loaded

	/*	// From https://community.oracle.com/thread/2536831  Classes not to instrument
	java.lang.String
	java.lang.Class
	java.lang.Throwable
	java.lang.ref.Reference
	java.lang.ref.SoftReference
	java.lang.ClassLoader
	java.lang.System
	java.lang.StackTraceElement
	 */

	//public static int curGen = 0;
	//public static int oldAge = 10;
	//public static ConcurrentHashMap<String, AtomicInteger> classGen = new ConcurrentHashMap<String, AtomicInteger>();


	//private final static ConcurrentHashMap<ClassLoader, Set<String>> loadedClasses = new ConcurrentHashMap<ClassLoader, Set<String>>();




	/*
	 * Class to hold Virtual Machines
	 */
	private static final AttachProvider ATTACH_PROVIDER = new AttachProvider()
	{
		@Override
		public String name() { return null; }

		@Override
		public String type() { return null; }

		@Override
		public VirtualMachine attachVirtualMachine(String id) { return null; }

		@Override
		public List<VirtualMachineDescriptor> listVirtualMachines() { return null; }
	};

	Agent() {
		jarFilePath = getAgentPath();
		JVM_ID = getPid();
	}

	Agent(String id) {
		jarFilePath = getAgentPath();
		JVM_ID = id;
	}

	Agent(String jarFile, String id) {
		jarFilePath = jarFile;
		JVM_ID = id;
	}


	public void setDebug(boolean debug) {
		Debug = debug;
	}

	private static void onAgentStartup(boolean vmStartup) {

		boolean printDebug = true;


		if (printDebug) {
			System.out.println("\n----------------Inserting Visitor Counter----------------\n");
			System.out.printf("%-55s %-55s %-45s\n", "NAME", "CANONICAL NAME", "CLASSLOADER");
		}


		ClassRedefinition redefs = new ClassRedefinition(new HitCounter());
		INSTRUMENTATION.addTransformer(redefs, true);


		//for (@SuppressWarnings("rawtypes") Class c : INSTRUMENTATION.getInitiatedClasses(ClassLoader.getSystemClassLoader())) {
		for (Class<?> c : INSTRUMENTATION.getAllLoadedClasses()) {
			if (canInstrument(c)) {
				try {
					System.out.printf("%-55s %-55s %-45s\n", c.getName(), c.getCanonicalName(), c.getClassLoader());
					INSTRUMENTATION.retransformClasses(new Class<?>[] { c });
				} catch (Exception e) {
					e.printStackTrace();
					//System.exit(1);
				}
			} 
		}
		try {
			redefs.redefineClasses(INSTRUMENTATION);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnmodifiableClassException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
					INSTRUMENTATION.retransformClasses(Integer.class);
					System.out.println("Retransforming the proxy class");
				}
				catch (UnmodifiableClassException e) { }
				catch (InterruptedException e) { }
			}
		}.start();*/

	}

	public static void agentmain(String args, Instrumentation instr) throws IOException {
		//premain(args, instr);
		INSTRUMENTATION = instr;
		onAgentStartup(true);
	}

	public static void premain(String args, Instrumentation instr) {
		INSTRUMENTATION = instr;
		onAgentStartup(true);
	}

	private String getAgentPath() {
		String jarPath = "";
		try {
			String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
			jarPath = URLDecoder.decode(path, "UTF-8");
		} catch (Exception e) {
			System.err.println("Could not determine location of this agent");
			throw new RuntimeException(e);
		}
		return jarPath;
	}



	static String getPid() {
		String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
		int p = nameOfRunningVM.indexOf('@');
		return nameOfRunningVM.substring(0, p);
	}


	public boolean loadAgent() {
		System.out.println("Inserting into Process " + JVM_ID);
		return initAgentInProcess();
	}



	/**
	 * Programmatic hook to dynamically load javaagent at runtime.
	 */
	public void initAtRuntime() {
		if (INSTRUMENTATION == null) {
			try {
				initAgentInProcess();

			} catch (Exception e) {
				System.err.println("Could not dynamically load Java Agent");
				e.printStackTrace();
			}
		}
	}

	private boolean initAgentInProcess() {
		VirtualMachine vm;
		try {

			if (AttachProvider.providers().isEmpty()) {
				vm = getVirtualMachineImplementationFromEmbeddedOnes(JVM_ID);
			}
			else {
				vm = VirtualMachine.attach(JVM_ID);
			}

			//vm.getAgentProperties()  Add agent name to properties and check if id is present, if yes,

			if (vm != null) {
				vm.loadAgent(jarFilePath, "");
				vm.detach();
			}		

		} catch (Exception e) {
			System.err.println("Could not add agent to process");
			throw new RuntimeException(e);
		}

		if (vm != null) {
			System.out.println("Agent was successfully inserted into process");
			return true;
		} else {
			System.out.println("[FAILURE] Agent was not successfully inserted into process");
			return false;
		}
	}

	public void redefineClasses(ClassDefinition ...defs) throws Exception {
		INSTRUMENTATION.redefineClasses(defs);
	}

	public static Instrumentation getInstrumentation() {
		return INSTRUMENTATION;
	}

	public static void setInstrumentation(Instrumentation instr) {
		INSTRUMENTATION = instr;
	}

	public static boolean canInstrument(Class<?> c) {
		if (INSTRUMENTATION.isModifiableClass(c) && c.getClassLoader() != null) {
			//if (INSTRUMENTATION.isModifiableClass(c)) {
			return AgentData.canInstrument(c.getName());	
		} else {
			return false;
		}
	}

	public static boolean canInstrument(String className) {
		return AgentData.canInstrument(className);
	}

	private VirtualMachine getVirtualMachineImplementationFromEmbeddedOnes(String pid)
	{
		try {
			if (File.separatorChar == '\\') {
				return new WindowsVirtualMachine(ATTACH_PROVIDER, pid);
			}

			String osName = System.getProperty("os.name");

			if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
				return new LinuxVirtualMachine(ATTACH_PROVIDER, pid);
			}
			else if (osName.startsWith("Mac OS X")) {
				return new BsdVirtualMachine(ATTACH_PROVIDER, pid);
			}
			else if (osName.startsWith("Solaris")) {
				return new SolarisVirtualMachine(ATTACH_PROVIDER, pid);
			}
		}
		catch (AttachNotSupportedException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (UnsatisfiedLinkError e) {
			throw new IllegalStateException("Native library for Attach API not available in this JRE", e);
		}

		return null;
	}

	/*public static byte[] getByteCode(Class<?> c) 
			throws IOException {
		byte[] bytecode = null;
		if (c != null) {
			String normalizedClassName = c.getName().replaceAll("/", ".");
			ClassWriter writer = new ClassWriter(0);
			ClassReader reader = new ClassReader(normalizedClassName);
			//ClassReader reader = new ClassReader(c.getName());
			//return reader.b;
			reader.accept(writer, 0);
			bytecode = writer.toByteArray();
		}
		return bytecode;
	}*/

	/*
	 * Get byte array from class file
	 */
	public static byte[] getByteArray(Class<?> c) {

		if (c != null) {
			try {

				InputStream in = c.getClass().getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class");
				byte[] bytes = IOUtils.toByteArray(in);
				in.close();
				System.out.printf(" >>>>>>> %-55s\n", c.getName());
				return bytes;
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

		}
		return null;
	}

	public static void logMethodInvocation() {
		System.out.println("Logging invocation...");
		//Thread.dumpStack();
		System.out.println("logging complete!");
	}

	/*
	 * Get class from classname
	 */
	public static Class<?> getClass(String className) {
		Class<?> result = null;
		for (@SuppressWarnings("rawtypes") Class c : INSTRUMENTATION.getAllLoadedClasses()) {
			//System.out.println("Trying to find class: " + className + " and this is: " + c.getName());
			if (canInstrument(c) && className.replace("/", ".").equalsIgnoreCase(c.getName())) {
				System.out.println("Trying to find class: " + className.replace("/", ".") + " and this is: " + c.getName());
				result = c;
				break;
			}
		}
		return result;
	}

	public static void countVisit(String methodName, String desc, String methodOwner) {
		long currentTime = System.nanoTime();
		System.out.println(" Method: " + methodOwner + "." + methodName + "() | Time: " + currentTime);

		HitCounter.countVisit(methodName, desc, methodOwner, currentTime);
		//AgentData.hit(methodName, desc, methodOwner, currentTime);
	}



	/*
	 * Get methods details for each class
	 */
	public static Map<String, ArrayList<Map<String, Object>>> getMethods(Class<?> c) {
		//class.getDeclaredMethods().length to get number of methods in class
		final Map<String, ArrayList<Map<String, Object>>> results = new HashMap<String, ArrayList<Map<String, Object>>>(c.getDeclaredMethods().length);
		final String normalizedClassName = c.getName().replaceAll("/", ".");
		try {
			ClassReader reader = new ClassReader(normalizedClassName);
			ClassWriter writer = new ClassWriter(reader, 0);
			ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
				@Override
				public MethodVisitor visitMethod(int access, final String name, String desc, String signature, String[] exceptions) {
					MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

					System.out.println("Method: " + name + " Access: " + access + " Class: " + normalizedClassName + " Desc: " + desc);
					if (results.containsKey(name)) {
						Map<String, Object> mDetails = new HashMap<String, Object>();
						mDetails.put("access", access);
						mDetails.put("desc", desc);
						mDetails.put("signature", signature);
						mDetails.put("exceptions", exceptions);
						results.get(name).add(mDetails);	
					} else {
						ArrayList<Map<String, Object>> details  = new ArrayList<Map<String, Object>>();
						Map<String, Object> mDetails = new HashMap<String, Object>();

						mDetails.put("access", access);
						mDetails.put("desc", desc);
						mDetails.put("signature", signature);
						mDetails.put("exceptions", exceptions);
						details.add(mDetails);
						results.put("name", new ArrayList<Map<String, Object>>(details));
					}

					return mv;
				}
			};

			reader.accept(visitor, 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return results;
	}

}