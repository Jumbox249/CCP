import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class AutonomousLoader {
    private final int loaderId;
    private final LoadingBay loadingBay;
    private final AtomicInteger trucksDispatched;
    private final List<Long> truckWaitTimes;
    private final List<Long> truckLoadTimes;
    private final Random random = ThreadLocalRandom.current();
    private volatile boolean operational = true;
    
    private static final double BREAKDOWN_PROBABILITY = 0.05;
    private static final int REPAIR_TIME_MS = 5000;
    private static final int BASE_LOADING_TIME = 300;
    private static final int VARIABLE_LOADING_TIME = 200;
    
    public AutonomousLoader(int loaderId, LoadingBay loadingBay, AtomicInteger trucksDispatched,
                           List<Long> truckWaitTimes, List<Long> truckLoadTimes) {
        this.loaderId = loaderId;
        this.loadingBay = loadingBay;
        this.trucksDispatched = trucksDispatched;
        this.truckWaitTimes = truckWaitTimes;
        this.truckLoadTimes = truckLoadTimes;
    }
    
    public void loadContainer(Container container, int containerQueueSize) throws InterruptedException {
        if (operational && random.nextDouble() < BREAKDOWN_PROBABILITY) {
            simulateBreakdown();
        }
        
        if (!operational) {
            simulateRepair();
        }
        
        Truck truck = loadingBay.getTruckForLoading(containerQueueSize);
        
        int bayId = determineBayId(truck);
        System.out.printf("Loader-%d: Moving Container #%d to Loading Bay-%d (Thread: %s)%n",
            loaderId, container.getId(), bayId, Thread.currentThread().getName());
        
        int loadingTime = BASE_LOADING_TIME + random.nextInt(VARIABLE_LOADING_TIME);
        Thread.sleep(loadingTime);
        
        boolean loaded = truck.loadContainer(container);
        if (!loaded) {
            System.err.printf("ERROR: Loader-%d failed to load Container #%d onto Truck-%d%n",
                loaderId, container.getId(), truck.getId());
            return;
        }
        
        System.out.printf("Loader-%d: Successfully loaded Container #%d onto Truck-%d (Thread: %s)%n",
            loaderId, container.getId(), truck.getId(), Thread.currentThread().getName());
        
        if (truck.isFull()) {
            dispatchTruck(truck);
        } else {
            long truckAge = System.currentTimeMillis() - truck.getCreationTime();
            if (truckAge > 5000 && truck.getContainerCount() > 0) {
                System.out.printf("Loader-%d: Dispatching Truck-%d due to timeout (%d containers, %.1f seconds old) (Thread: %s)%n",
                    loaderId, truck.getId(), truck.getContainerCount(), truckAge / 1000.0, Thread.currentThread().getName());
                dispatchTruck(truck);
            }
        }
    }
    
    private void simulateBreakdown() {
        operational = false;
        SwiftCartSimulation.BusinessLogger.logLoaderBreakdown(loaderId);
        System.out.printf("Loader-%d: BREAKDOWN detected - stopping operations (Thread: %s)%n",
            loaderId, Thread.currentThread().getName());
    }
    
    private void simulateRepair() throws InterruptedException {
        System.out.printf("Loader-%d: Starting repair process... (Thread: %s)%n",
            loaderId, Thread.currentThread().getName());
        
        Thread.sleep(REPAIR_TIME_MS);
        
        operational = true;
        SwiftCartSimulation.BusinessLogger.logLoaderRepaired(loaderId);
        System.out.printf("Loader-%d: Repair completed - resuming operations (Thread: %s)%n",
            loaderId, Thread.currentThread().getName());
    }
    
    private void dispatchTruck(Truck truck) {
        System.out.printf("Truck-%d: Fully loaded with %d containers. Departing to Distribution Centre. (Thread: %s)%n",
            truck.getId(), truck.getContainerCount(), Thread.currentThread().getName());
        
        truck.setStatus("DEPARTED");
        String departureInfo = truck.depart();
        System.out.printf("Loader-%d: %s (Thread: %s)%n", loaderId, departureInfo, Thread.currentThread().getName());
        
        loadingBay.truckDeparted(truck, truckWaitTimes, truckLoadTimes);
        trucksDispatched.incrementAndGet();
        
        SwiftCartSimulation.BusinessLogger.logTruckDeparture(truck.getId(), truck.getContainerCount());
    }
    
    private int determineBayId(Truck truck) {
        return (truck.getId() % 2) + 1;
    }
    
    public boolean isOperational() {
        return operational;
    }
    
    public int getLoaderId() {
        return loaderId;
    }
    
    public void forceRepair() {
        if (!operational) {
            operational = true;
            System.out.printf("Loader-%d: Emergency repair completed%n", loaderId);
        }
    }
    
    public String getStatus() {
        return operational ? "OPERATIONAL" : "BROKEN_DOWN";
    }
    
    public boolean performMaintenanceCheck() {
        try {
            System.out.printf("Loader-%d: Performing maintenance check... (Thread: %s)%n",
                loaderId, Thread.currentThread().getName());
            Thread.sleep(1000);
            
            boolean checkPassed = random.nextDouble() < 0.95;
            
            if (!checkPassed) {
                System.out.printf("Loader-%d: Maintenance check failed - needs repair (Thread: %s)%n",
                    loaderId, Thread.currentThread().getName());
                operational = false;
            }
            
            return checkPassed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}