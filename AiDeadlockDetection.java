import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
class DeadlockDetector {
    private final Map<Thread, List<Thread>> waitForGraph = new HashMap<>();
    private final Map<Thread, Integer> deadlockRisk = new HashMap<>();
    private final Timer monitoringTimer = new Timer();

    public DeadlockDetector() {
        monitoringTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                monitorThreads();
            }
        }, 0, 2000);
    }
    public synchronized void requestResource(Thread requester, Thread holder) {
        waitForGraph.computeIfAbsent(requester, k -> new ArrayList<>()).add(holder);
        updateDeadlockRisk(requester);
        if (detectDeadlock()) {
            System.out.println("[AI ALERT] Potential deadlock detected! Resolving...");
            resolveDeadlock();
        }
    }

    private void updateDeadlockRisk(Thread thread) {
        int riskScore = waitForGraph.getOrDefault(thread, new ArrayList<>()).size();
        deadlockRisk.put(thread, riskScore);
    }
    private boolean detectDeadlock() {
        Set<Thread> visited = new HashSet<>();
        Set<Thread> recStack = new HashSet<>();
        for (Thread node : waitForGraph.keySet()) {
            if (dfs(node, visited, recStack)) {
                return true;
            }
        }
        return false;
    }
    private boolean dfs(Thread node, Set<Thread> visited, Set<Thread> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;
       visited.add(node);
        recStack.add(node);
        for (Thread neighbor : waitForGraph.getOrDefault(node, new ArrayList<>())) {
            if (dfs(neighbor, visited, recStack)) return true;
        }
        recStack.remove(node);
        return false;
    }
    private void resolveDeadlock() {
        Thread highestRiskThread = Collections.max(deadlockRisk.entrySet(), Map.Entry.comparingByValue()).getKey();
        System.out.println("[AI] Terminating " + highestRiskThread.getName() + " to resolve deadlock.");
        highestRiskThread.interrupt();
        waitForGraph.remove(highestRiskThread);
        deadlockRisk.remove(highestRiskThread);
    }
    private void monitorThreads() {
        System.out.println("[Monitor] Active Threads: " + Thread.activeCount());
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            System.out.println("Thread: " + thread.getName() + " - State: " + thread.getState());
        }
    }
}
class Resource {
    private final ReentrantLock lock = new ReentrantLock();

    public boolean acquire(Thread requester, DeadlockDetector detector, Thread holder) {
        detector.requestResource(requester, holder);
        return lock.tryLock();
    }
    public void release() {
        lock.unlock();
    }
}
public class AiDeadlockDetection {
    public static void main(String[] args) {
        DeadlockDetector detector = new DeadlockDetector();
        Resource resourceA = new Resource();
        Resource resourceB = new Resource();
        Thread thread1 = new Thread(() -> {
            if (resourceA.acquire(Thread.currentThread(), detector, Thread.currentThread())) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                if (resourceB.acquire(Thread.currentThread(), detector, Thread.currentThread())) {
                    resourceB.release();
                }
                resourceA.release();
            }
        }, "Thread-1");
        Thread thread2 = new Thread(() -> {
            if (resourceB.acquire(Thread.currentThread(), detector, Thread.currentThread())) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                if (resourceA.acquire(Thread.currentThread(), detector, Thread.currentThread())) {
                    resourceA.release();
                }
                resourceB.release();
            }
        }, "Thread-2");
        thread1.start();
        thread2.start();
    }
}
