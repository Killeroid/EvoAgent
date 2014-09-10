package AgentJ.Utils;

import java.lang.instrument.ClassDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class AgentData {
	

	/*
	 * Stores list of classes that should not be instrumented
	 * https://community.oracle.com/thread/1524222?start=0&tstart=0
	 * 
	 */
	@SuppressWarnings("serial")
	private static ConcurrentHashMap<String, Integer> doNotInstrument = new ConcurrentHashMap<String, Integer>() {{
			put("com.sun.tools.attach", 0);
			put("sun.tools.attach", 0);
			put("org.objectweb.asm", 0);
			put("java.lang.String", 0);
			put("java.lang.Class", 0);
			put("java.lang.Throwable", 0);
			put("java.lang.ref.Reference", 0);
			put("java.lang.ref.SoftReference", 0);
			put("java.lang.ClassLoader", 0);
			put("java.lang.System", 0);
			put("java.lang.StackTraceElement", 0);
			put("java.lang.Object", 0);
			put("AgentJ", 0);
			put("Test.", 0);
			//put("java.", 0);
			put("sun.instrument", 0);
			put("java.lang.instrument", 0);
			put("sun.reflect", 0);
			
	}};
	
	public static ConcurrentHashMap<String, AtomicLong> visitCounts = new ConcurrentHashMap<String, AtomicLong>(); //store number of visits
	
	public static ConcurrentHashMap<String, AtomicLong> methodInvokeeCount = new ConcurrentHashMap<String, AtomicLong>(); //store number of times a method invokes something
	
	private static ConcurrentHashMap<String, HashSet<ClassDefinition>> toBeRedefined = new ConcurrentHashMap<String, HashSet<ClassDefinition>>();
	
	private static ConcurrentHashMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<String, Class<?>>();  //All loaded classes
	
	
	public static Set<String> getDoNotInstrument() {
		return doNotInstrument.keySet();
	}
	
	public static void addNoInstrumentClass(String name) {
		doNotInstrument.putIfAbsent(name, 0);
	}
	
	public static boolean canInstrument(String className) {
		boolean result = true;
		String normalizedClassName = className.replaceAll("/", ".");
		for (String prefix: doNotInstrument.keySet()) {
			if (normalizedClassName.startsWith(prefix)) {
				result = false;
				break;
			}
		}
		return result;
	}
	
	public static void addNewRedefinition(String name, ClassDefinition redef) {
		if (toBeRedefined.containsKey(name)) {
			toBeRedefined.get(name).add(redef);
		} else {
			HashSet<ClassDefinition> defs = new HashSet<ClassDefinition>();
			defs.add(redef);
			toBeRedefined.putIfAbsent(name, defs);
		}

	}
	
	public static void doneRedefining() {
		
	}
	
	
	public static void addClass(Class<?> clazz) {
		loadedClasses.putIfAbsent(clazz.getName(), clazz);
	}
	
	public static Class<?> classLoaded(String name) {
		return loadedClasses.get(name);
	}
	
	
	
	
	
}