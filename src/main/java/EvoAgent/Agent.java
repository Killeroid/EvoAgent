package EvoAgent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.List;
import java.util.jar.JarFile;

import com.sun.tools.attach.*;
import com.sun.tools.attach.spi.*;

import sun.tools.attach.*;


/**
 * Simple agent to get access to the Instrumentation API.
 */
public final class Agent {

	private static String jarFilePath;
	public static volatile Instrumentation INSTRUMENTATION;
	private final String JVM_ID;

	private static boolean Debug;


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


	public static void setDebug(boolean debug) {
		Debug = debug;
	}

	private static void onAgentStartup(boolean vmStartup) {
		//Start agent
		String jarpath = getDaemonPath();
		try {
			//Load jar files in bootclassloader so that it is available to the agent and to the system
			INSTRUMENTATION.appendToBootstrapClassLoaderSearch(new JarFile(jarpath));
		} catch (NullPointerException e) {
			System.out.println("[ERROR] Could not insert EvoDaemon classes into JVM");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("[ERROR] Could not find EvoDaemon temp jar");
			e.printStackTrace();
		}
		
		try {
			Class<?> daemonClass = Thread.currentThread().getContextClassLoader().loadClass("EvoDaemon.EvoDaemon");
			Thread daemon = new Thread((Runnable) daemonClass.getDeclaredConstructor(Instrumentation.class, String.class).newInstance(Agent.INSTRUMENTATION, jarpath));
			daemon.setName("EvoDaemon");
			//daemon.setUncaughtExceptionHandler(new DefaultExceptionHandler());
			daemon.start();

		} catch (ClassNotFoundException e1) {
			System.out.println("[ERROR] Starting daemon thread: " + Thread.currentThread().getContextClassLoader());
			e1.printStackTrace();
		} catch (IllegalAccessException e) {
			System.out.println("[ERROR] Accessing to daemon class : " + Thread.currentThread().getContextClassLoader());
			e.printStackTrace();
		} catch (InstantiationException e) {
			System.out.println("[ERROR] Instantiating daemon thread: " + Thread.currentThread().getContextClassLoader());
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	public static void agentmain(String args, Instrumentation instr) throws IOException {
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
			System.err.println("[ERROR] Could not determine location of this agent");
			throw new RuntimeException(e);
		}
		return jarPath;
	}
	
	private JarFile getDaemon() {
		try {
			//String path = this.getClass().getResource("/EvoDaemon.jar").getFile();
			File tempjar = File.createTempFile("EvoDaemon", ".jar");
			//tempjar.deleteOnExit();
			InputStream is = this.getClass().getResourceAsStream("/EvoDaemon.jar");
			OutputStream os = new FileOutputStream(tempjar.getPath());
			byte[] buffer = new byte[1024];
            int bytesRead;
            //read from is to buffer
            while((bytesRead = is.read(buffer)) !=-1){
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            //flush OutputStream to write any buffered data to file
            os.flush();
            os.close();
			return new JarFile(tempjar.getPath());
		} catch (Exception e) {
			System.err.println("[ERROR] Could not determine location of the daemon");
			throw new RuntimeException(e);
		}
	}
	
	private static String getDaemonPath() {
		try {
			//String path = this.getClass().getResource("/EvoDaemon.jar").getFile();
			File tempjar = File.createTempFile("EvoDaemon", ".jar");
			String path = tempjar.getCanonicalPath();
			//tempjar.deleteOnExit();
			InputStream is = Agent.class.getResourceAsStream("/EvoDaemon.jar");
			OutputStream os = new FileOutputStream(tempjar.getPath());
			byte[] buffer = new byte[1024];
            int bytesRead;
            //read from is to buffer
            while((bytesRead = is.read(buffer)) !=-1){
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            //flush OutputStream to write any buffered data to file
            os.flush();
            os.close();
			return path;
		} catch (Exception e) {
			System.err.println("[ERROR] Could not determine location of the daemon");
			throw new RuntimeException(e);
		}
	}



	static String getPid() {
		String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
		int p = nameOfRunningVM.indexOf('@');
		return nameOfRunningVM.substring(0, p);
	}


	public boolean loadAgent() {
		System.out.println("Inserting into Process " + JVM_ID);
		try {
			initAgentInProcess();
			return true;
		} catch (Exception e) {
		
			for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
				System.out.println(ste);
			}

			System.err.println("[ERROR] Agent was not able to be inserted into Process: " + JVM_ID);
			return false;
		}

	}



	/**
	 * Programmatic hook to dynamically load javaagent at runtime.
	 */
	public void initAtRuntime() throws Exception {
		if (INSTRUMENTATION == null) {
			try {
				initAgentInProcess();

			} catch (Exception e) {
				System.err.println("Could not dynamically load Java Agent");
				throw e;
				
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
	
	static class DefaultExceptionHandler implements Thread.UncaughtExceptionHandler {

		public void uncaughtException(Thread t, Throwable e) {
			System.out.println(t + " throws an uncaught exception: " + e);
			for (StackTraceElement ste : t.getStackTrace()) {
				System.out.println(ste);
			}
			
		}
		
	}

}