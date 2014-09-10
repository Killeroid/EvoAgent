package AgentJ.Transformers;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import AgentJ.Agent;
import AgentJ.Utils.AgentData;




public class HitCounter implements ClassFileTransformer{
	private static Set<String> instrumentedClasses = new HashSet<String>();
	
	
	private ArrayList<String> noCounter = new ArrayList<String>(); //List of methods missing the counter
	public static MethodInsnNode visitCounterInsn = new MethodInsnNode(Opcodes.INVOKESTATIC, "AgentJ/Agent", "countVisit", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);

	String currentClassName;

	public byte[] transform(ClassLoader    loader,
            String              className,
            Class<?>            classBeingRedefined,
            ProtectionDomain    protectionDomain,
            byte[]              classfileBuffer)
			throws IllegalClassFormatException {
		
		final String normalizedClassName = className.replaceAll("/", ".");
		currentClassName = className.replaceAll(Pattern.quote("."), "/");
		

		if (!Agent.canInstrument(className) && instrumentedClasses.contains(normalizedClassName)) {
			return classfileBuffer;
			} 

		
		ClassReader reader = null;
		try {
			reader = new ClassReader(classfileBuffer);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, ClassReader.EXPAND_FRAMES);
		

		for (final Object obj : classNode.methods) {
			MethodNode mNode = (MethodNode)obj;
			String normalizedOwner = className.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (mNode.desc == null) ? "" : mNode.desc;
			String methodFullName = normalizedOwner + "." + mNode.name + " >> " + normalizedDesc;
			AgentData.visitCounts.putIfAbsent(methodFullName, new AtomicLong(0));
			//Note methods without the counter
			if (!mNode.instructions.contains(visitCounterInsn)) {
				noCounter.add(methodFullName);
			}
		}
		
		reader = new ClassReader(classfileBuffer);
		
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		
		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
		    @Override
		    public MethodVisitor visitMethod(int access, final String name, String desc, String signature, String[] exceptions) {
		        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		        
				String normalizedDesc = (desc == null) ? "" : desc;
				String methodFullName = currentClassName + "." + name + " >> " + normalizedDesc;
				
				if (noCounter.contains(methodFullName)) {
					//System.out.print(methodFullName + " --------- \n");
					noCounter.remove(methodFullName); //Help garbage collection
					EnteringAdapter mv_insert = new EnteringAdapter(mv, access, name, desc, currentClassName); 
		        	return mv_insert;
				} else {
					//System.out.print(methodFullName + " ********** \n");
					return mv;
				}
		    }
		};
		
		reader.accept(visitor, ClassReader.EXPAND_FRAMES);
				
		instrumentedClasses.add(normalizedClassName); //Make sure not to instrument this class again
		return writer.toByteArray();    
	}	
	
	// Called by instrumented methods anytime they're accessed
		public static void countVisit(String methodName, String desc, String methodOwner) {
			String normalizedOwner = methodOwner.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (desc == null) ? "" : desc;
			String methodFullName = normalizedOwner + "." + methodName + " >> " + normalizedDesc;
			String methodCaller = getMethodCaller();
			
			//Count visits
			AgentData.visitCounts.putIfAbsent(methodFullName, new AtomicLong(0));
			AgentData.visitCounts.get(methodFullName).incrementAndGet();
			
			//Count number of times invokee
			AgentData.methodInvokeeCount.putIfAbsent(methodCaller, new AtomicLong(0));
			AgentData.methodInvokeeCount.get(methodCaller).incrementAndGet();
			
			
			System.out.print(" Method: " + normalizedOwner + "." + methodName + "() | Visit: " + AgentData.visitCounts.get(methodFullName).get());
			System.out.print(" | ");
			System.out.print("Invoked by " + methodCaller + "\n");
			
			
		}
		
		// Update call counter when method is removed
		public static void resetVisits(String methodName, String desc, String methodOwner) {
			String normalizedOwner = methodOwner.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (desc == null) ? "" : desc;
			String methodFullName = normalizedOwner + "." + methodName + " >> " + normalizedDesc;
			AgentData.visitCounts.remove(methodFullName);
		}
		
		// Get the caller of the counter method
		public static String getMethodCaller() {
			final StackTraceElement ste = Thread.currentThread().getStackTrace()[3];
			String result = ste.getClassName() + "." + ste.getMethodName() + "()";
			
			return result;
		}
		
		// Insert counter caller into method that is being instrumented
		// Checks and makes sure caller hasnt already been inserted
		public MethodVisitor insertCounter(MethodVisitor mv, int access, final String name, String owner, String desc, String signature, String[] exceptions) {
			String normalizedOwner = owner.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (desc == null) ? "" : desc;
			String methodFullName = normalizedOwner + "." + name + " >> " + normalizedDesc;
			
			if (noCounter.contains(methodFullName)) {
				//System.out.print(" --------- ");
				noCounter.remove(methodFullName); //Help garbage collection
				EnteringAdapter mv_insert = new EnteringAdapter(mv, access, name, desc, owner); 
				//ExitAdapter mv_insert = new ExitAdapter(mv, access, name, desc, owner); 
	        	return mv_insert;
			} else {
				//System.out.print(" ********** ");
				return mv;
			}
		}
		
		public static ConcurrentHashMap<String, AtomicLong> getVisits() {
			return AgentData.visitCounts;
		}
	
	static class EnteringAdapter extends AdviceAdapter {
		private String methodName;
		private String methodOwner;
		private String methodDesc;
		
		public EnteringAdapter(MethodVisitor mv,int access, String name, String desc, String className) {
		    super(Opcodes.ASM5, mv, access, name, desc);
		    this.methodName = name;//normalizedClassName;
		    this.methodOwner = className.replaceAll(Pattern.quote("."), "/");
		    this.methodDesc = (desc == null) ? "" : desc;
		}
		
		//Put args on stack and supply them to countVisits functions
		protected void onMethodEnter() {
			super.visitLdcInsn(methodName);
			super.visitLdcInsn(methodDesc);
		    super.visitLdcInsn(methodOwner);
		    super.visitMethodInsn(Opcodes.INVOKESTATIC, "AgentJ/Agent", "countVisit", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
		}
		
		public void visitMaxs(int stack, int locals) {
			super.visitMaxs(stack, locals);
		}
	}
	
	
	/*
	 * Insert countvisitor at the end of each method
	 * Takes into consideration if method returns error or not
	 */
	static class ExitAdapter extends AdviceAdapter {
		private String methodName;
		private String methodOwner;
		private String methodDesc;
		private Label startFinally = new Label();
		
		public ExitAdapter(MethodVisitor mv,int access, String name, String desc, String className) {
		    super(Opcodes.ASM5, mv, access, name, desc);
		    this.methodName = name;//normalizedClassName;
		    this.methodOwner = className.replaceAll(Pattern.quote("."), "/");
		    this.methodDesc = (desc == null) ? "" : desc;
		}
		
		public void visitCode() {
		    super.visitCode();
		    super.visitLabel(startFinally);
		  }
		
		public void visitMaxs(int stack, int locals) {
			Label endFinally = new Label();
			super.visitTryCatchBlock(startFinally,
		        endFinally, endFinally, null);
			super.visitLabel(endFinally);
		    onFinally(Opcodes.ATHROW);
		    super.visitInsn(Opcodes.ATHROW);
		    super.visitMaxs(stack, locals);
		}
		
		protected void onMethodExit(int opcode) {
		    if(opcode!=Opcodes.ATHROW) {
		      onFinally(opcode);
		    }
		}
		
		//Put args on stack and supply them to countVisits functions
		private void onFinally(int opcode) {
			super.visitLdcInsn(methodName);
			super.visitLdcInsn(methodDesc);
			super.visitLdcInsn(methodOwner);
			super.visitMethodInsn(Opcodes.INVOKESTATIC, "AgentJ/Agent", "countVisit", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
			//deBug(opcode);
		  }
		
		private void deBug(int opcode) {
			super.visitFieldInsn(Opcodes.GETSTATIC,
		        "java/lang/System", "err",
		        "Ljava/io/PrintStream;");
			super.visitLdcInsn("Exiting " + this.methodName);
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
		        "java/io/PrintStream", "println",
		        "(Ljava/lang/String;)V", false);
		  }
		
	}
}
