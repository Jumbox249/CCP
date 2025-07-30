import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Autonomous Loader - Loads containers onto trucks at loading bays
 * Simulates random breakdowns and repairs as per requirements
 * 3 loaders work concurrently across 2 loading bays
 * Includes breakdown simulation and recovery mechanisms
 */
public class AutonomousLoader {
    private final int loaderId;
    private final LoadingBay loadingBay;
    private final AtomicInteger trucksDispatched;
    private final List<Long> truckWaitTimes;
    private final List<Long> truckLoadTimes;
    private final Random random = ThreadLocalRandom.current();
    private volatile boolean operational = true;
    
    // Loader parameters
    private static final double BREAKDOWN_PROBABILITY = 0.05; // 5% chance of breakdown
    private static final int REPAIR_TIME_MS = 5000; // 5 seconds repair time
    private static final int BASE_LOADING_TIME = 3000; // 3 seconds base loading time
    private static final int VARIABLE_LOADING_TIME = 2000; // +0-2 seconds variable
    
    /**
     * Constructor
     * @param loaderId Unique loader identifier (1-3)
     * @param loadingBay Loading bay management system
     * @param trucksDispatched Counter for dispatched trucks
     * @param truckWaitTimes List for recording truck wait times
     * @param truckLoadTimes List for recording truck load times
     */
    public AutonomousLoader(int loaderId, LoadingBay loadingBay, AtomicInteger trucksDispatched,
                           List<Long> truckWaitTimes, List<Long> truckLoadTimes) {
        this.loaderId = loaderId;
        this.loadingBay = loadingBay;
        this.trucksDispatched = trucksDispatched;
        this.truckWaitTimes = truckWaitTimes;
        this.truckLoadTimes = truckLoadTimes;
    }
    
    /**
     * Load container onto truck with breakdown simulation
     * @param container Container to load
     * @param containerQueueSize Current size of container queue
     * @throws InterruptedException if thread is interrupted
     */
    public void loadContainer(Container container, int containerQueueSize) throws InterruptedException {
        // Check for random breakdown before loading (5% chance)
        if (operational && random.nextDouble() < BREAKDOWN_PROBABILITY) {
            simulateBreakdown();
        }
        
        // If loader is broken down, simulate repair process
        if (!operational) {
            simulateRepair();
        }
        
        // Get truck for loading (may involve waiting for available bay)
        Truck truck = loadingBay.getTruckForLoading(containerQueueSize);
        
        // Log container movement to bay
        int bayId = determineBayId(truck);
        System.out.printf("Loader-%d: Moving Container #%d to Loading Bay-%d (Thread: %s)%n", 
            loaderId, container.getId(), bayId, Thread.currentThread().getName());
        
        // Simulate loading time (3-5 seconds)
        int loadingTime = BASE_LOADING_TIME + random.nextInt(VARIABLE_LOADING_TIME);
        Thread.sleep(loadingTime);
        
        // Load container onto truck
        boolean loaded = truck.loadContainer(container);
        if (!loaded) {
            System.err.printf("ERROR: Loader-%d failed to load Container #%d onto Truck-%d%n", 
                loaderId, container.getId(), truck.getId());
            return;
        }
        
        System.out.printf("Loader-%d: Successfully loaded Container #%d onto Truck-%d (Thread: %s)%n", 
            loaderId, container.getId(), truck.getId(), Thread.currentThread().getName());
        
        // Check if truck is now full and ready to dispatch
        if (truck.isFull()) {
            dispatchTruck(truck);
        }
    }
    
    /**
     * Simulate loader breakdown
     */
    private void simulateBreakdown() {
        operational = false;
        SwiftCartSimulation.BusinessLogger.logLoaderBreakdown(loaderId);
        System.out.printf("Loader-%d: BREAKDOWN detected - stopping operations (Thread: %s)%n", 
            loaderId, Thread.currentThread().getName());
    }
    
    /**
     * Simulate loader repair process
     * @throws InterruptedException if thread is interrupted during repair
     */
    private void simulateRepair() throws InterruptedException {
        System.out.printf("Loader-%d: Starting repair process... (Thread: %s)%n", 
            loaderId, Thread.currentThread().getName());
        
        // Repair takes 5 seconds
        Thread.sleep(REPAIR_TIME_MS);
        
        operational = true;
        SwiftCartSimulation.BusinessLogger.logLoaderRepaired(loaderId);
        System.out.printf("Loader-%d: Repair completed - resuming operations (Thread: %s)%n", 
            loaderId, Thread.currentThread().getName());
    }
    
    /**
     * Dispatch full truck and update statistics
     * @param truck Truck to dispatch
     */
    private void dispatchTruck(Truck truck) {
        System.out.printf("Truck-%d: Fully loaded with %d containers. Departing to Distribution Centre. (Thread: %s)%n", 
            truck.getId(), truck.getContainerCount(), Thread.currentThread().getName());
        
        // Update truck status and dispatch
        truck.setStatus("DEPARTED");
        String departureInfo = truck.depart();
        System.out.printf("Loader-%d: %s (Thread: %s)%n", loaderId, departureInfo, Thread.currentThread().getName());
        
        // Record truck departure in loading bay
        loadingBay.truckDeparted(truck, truckWaitTimes, truckLoadTimes);
        trucksDispatched.incrementAndGet();
        
        // Log departure for business tracking
        SwiftCartSimulation.BusinessLogger.logTruckDeparture(truck.getId(), truck.getContainerCount());
    }
    
    /**
     * Determine which bay the truck is using (simplified assignment)
     * @param truck Truck to determine bay for
     * @return Bay ID (1 or 2)
     */
    private int determineBayId(Truck truck) {
        // Simple assignment based on truck ID
        return (truck.getId() % 2) + 1;
    }
    
    /**
     * Check if loader is currently operational
     * @return true if loader is working, false if broken down
     */
    public boolean isOperational() {
        return operational;
    }
    
    /**
     * Get loader ID
     * @return Loader identifier
     */
    public int getLoaderId() {
        return loaderId;
    }
    
    /**
     * Force loader back to operational state (for testing/emergency)
     */
    public void forceRepair() {
        if (!operational) {
            operational = true;
            System.out.printf("Loader-%d: Emergency repair completed%n", loaderId);
        }
    }
    
    /**
     * Get loader status information
     * @return Status string describing current loader state
     */
    public String getStatus() {
        return operational ? "OPERATIONAL" : "BROKEN_DOWN";
    }
    
    /**
     * Simulate loader performing maintenance check
     * @return true if maintenance check passed
     */
    public boolean performMaintenanceCheck() {
        try {
            System.out.printf("Loader-%d: Performing maintenance check... (Thread: %s)%n", 
                loaderId, Thread.currentThread().getName());
            Thread.sleep(1000); // 1 second maintenance check
            
            // 95% chance maintenance check passes
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