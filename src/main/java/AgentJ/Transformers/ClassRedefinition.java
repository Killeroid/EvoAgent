package AgentJ.Transformers;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import AgentJ.Agent;


public class ClassRedefinition implements ClassFileTransformer {

	private static ArrayList<ClassDefinition> defs = new ArrayList<ClassDefinition>();
	private ClassFileTransformer transformer;
	private Set<String> redefined;
	private Set<String> transformed;
	private String uuid;
	private boolean canRedefined;

	public ClassRedefinition(ClassFileTransformer trans) {
		this.transformer = trans;
		this.uuid = UUID.randomUUID().toString();
		this.transformed = new HashSet<String>();
		this.redefined = new HashSet<String>();
		this.canRedefined = false;
	}
	
	public ClassRedefinition(ClassFileTransformer trans, boolean redef) {
		this.transformer = trans;
		this.uuid = UUID.randomUUID().toString();
		this.transformed = new HashSet<String>();
		this.redefined = new HashSet<String>();
		this.canRedefined = redef;
	}
	
	public String getUUID() {
		return this.uuid;
	}

	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {

		if (Agent.canInstrument(className) && canBeRedefined(className)) {
			Class<?> clazz = null;
			byte[] newClassBuffer = this.transformer.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
			//System.out.println("Storing def for file : " + className);
			try {
				clazz = Class.forName(className.replaceAll("/", "."), false, loader);
				defs.add(new ClassDefinition(clazz, newClassBuffer));
				transformed.add(className.replaceAll("/", "."));
				return newClassBuffer;
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new IllegalClassFormatException(className + "was not found");
			} 
			
		} else {
			return classfileBuffer;
		}

		
	}

	
	//Call to redefine classes
	public synchronized void redefineClasses(Instrumentation instr) throws ClassNotFoundException, UnmodifiableClassException {
		//System.out.println("Hows many defs do we have?: " + defs.size());
		if (defs.size() > 0) {
			ClassDefinition[] redefs = new ClassDefinition[defs.size()];
			defs.toArray(redefs);
			instr.redefineClasses(redefs);
			this.redefined.addAll(transformed);
			this.defs = new ArrayList<ClassDefinition>();
			redefs = null;
			this.transformed = new HashSet<String>();
		}
	}

	//Aid in garbage collection
	public void cleanup() {
		defs = new ArrayList<ClassDefinition>();
		this.transformer = null;
		this.uuid = null;
		this.transformed = null;
		this.redefined = null;
	}

	public boolean equals(Object obj) {
		
		if (obj instanceof ClassRedefinition) {
			if (this.getUUID() == ((ClassRedefinition) obj).getUUID()) {
				return true;
			} else {
				return false;
			}		
		} else {
			return false;
		}
	}
	
	public boolean canBeRedefined(String className) {
		if (!this.canRedefined && 
				( this.transformed.contains(className.replaceAll("/", ".")) 
						|| this.redefined.contains(className.replaceAll("/", "."))) ) {
			System.out.println("[WARNING]: " + className.replaceAll("/", ".") + " already redefined. Skipping...");
			return false;
		} else {
			return true;
		}
	}




}