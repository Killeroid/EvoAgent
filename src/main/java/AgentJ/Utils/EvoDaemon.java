package AgentJ.Utils;

import java.lang.instrument.UnmodifiableClassException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import AgentJ.Agent;


/*
 * Daemon that runs every x minutes and transforms code
 */
public class EvoDaemon implements Runnable {

	private boolean Debug;
	private static ConcurrentLinkedQueue<Class<Runnable>> queue;
	private static ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<?> runningTask;

	
	public void run() {
		
		
		try {
			// Will keep running and executing task one after the other until error occurs or we kill it 
			for (;;) {
				if (!queue.isEmpty() && (runningTask.isDone() || runningTask.isCancelled() || runningTask == null)) {
					runningTask = executor.submit(queue.poll().newInstance());
				}
				
				// Do some background task that will generate things to do
			}
			
		} catch (InstantiationException e) {
			//executor.shutdown();
			e.printStackTrace();
			shutdownAndAwaitTermination(executor);	
		} catch (IllegalAccessException e) {
			//executor.shutdown();
			e.printStackTrace();
			shutdownAndAwaitTermination(executor);	
		}
	}
	
	/*
	 * The following method shuts down an ExecutorService in two phases, 
	 * first by calling shutdown to reject incoming tasks, and then calling shutdownNow, 
	 * if necessary, to cancel any lingering tasks
	 * 
	 * Sourced from: http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ExecutorService.html
	 */
	// 
	public void shutdownAndAwaitTermination(ExecutorService pool) {
		   pool.shutdown(); // Disable new tasks from being submitted
		   try {
		     // Wait a while for existing tasks to terminate
		     if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
		       pool.shutdownNow(); // Cancel currently executing tasks
		       // Wait a while for tasks to respond to being cancelled
		       if (!pool.awaitTermination(60, TimeUnit.SECONDS))
		           System.err.println("Pool did not terminate");
		     }
		   } catch (InterruptedException ie) {
		     // (Re-)Cancel if current thread also interrupted
		     pool.shutdownNow();
		     // Preserve interrupt status
		     Thread.currentThread().interrupt();
		   }
		 }

	public void printVisitCounts() {
		for (Entry<String, AtomicInteger> entry : VisitCounter.getVisits().entrySet()) {
			String methodFullNameDesc = entry.getKey();
			String methodName = methodFullNameDesc.substring(0, methodFullNameDesc.lastIndexOf(" >> ")) ;

			System.out.printf("%-70s %-10d\n", "NAME", "VISITS");
			//normalizedOwner + "." + methodName + " >> " + normalizedDesc;
			System.out.printf("%-70s %-10d\n", methodName, entry.getValue().intValue());
		}

	}
	
	public static void hit(String methodFullName) {
		//queue.add(methodFullName);
		//exec.submit(task);
	}

	class Task implements Runnable {
		private String className;
		private String methodName;
		private String methodDesc;
		
		Task(String className, String methodName, String methodDesc) {
			this.className = className;
			this.methodName = methodName;
			this.methodDesc = methodDesc;
		}
		public void run() {
			String normalizedOwner = this.className.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (this.methodDesc == null) ? "" : this.methodDesc;
			String methodFullName = normalizedOwner + "." + methodName + " >> " + normalizedDesc;
			AgentData.visitCounts.putIfAbsent(methodFullName, new AtomicLong(0));
			AgentData.visitCounts.get(methodFullName).incrementAndGet();
		}
	}
	
	class Remover implements Runnable {
		private String className;
		private String methodName;
		private String methodDesc;
		
		Remover(String className, String methodName, String methodDesc) {
			this.className = className;
			this.methodName = methodName;
			this.methodDesc = methodDesc;
		}
		public void run() {
			String normalizedOwner = this.className.replaceAll(Pattern.quote("."), "/");
			String normalizedDesc = (this.methodDesc == null) ? "" : this.methodDesc;
			String methodFullName = normalizedOwner + "." + methodName + " >> " + normalizedDesc;
			AgentData.visitCounts.putIfAbsent(methodFullName, new AtomicLong(0));
			AgentData.visitCounts.get(methodFullName).incrementAndGet();
		}
	}
	
	class CounterInserter implements Runnable {

		public void run() {
			for (Class<?> c : Agent.INSTRUMENTATION.getAllLoadedClasses()) {
				if (Agent.canInstrument(c)) {
					try {
						System.out.printf("%-55s %-55s %-45s\n", c.getName(), c.getCanonicalName(), c.getClassLoader());
						Agent.INSTRUMENTATION.retransformClasses(new Class<?>[] { c });
					} catch (Exception e) {
						e.printStackTrace();
						//System.exit(1);
					}
				} 
			}
			try {
				//redefs.redefineClasses(Agent.INSTRUMENTATION);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}	
	}
	
	class HitFrequencyCounter implements Runnable {
		private int period;
		
		public HitFrequencyCounter(int periodInSecs) {
			this.period = periodInSecs;
		}
		
		public HitFrequencyCounter() {
			this.period = 60;
		}
		
		public void run() {
			long startTime = System.currentTimeMillis();
			
		}
		
	}

	


}