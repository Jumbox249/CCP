import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Loading Bay - Manages 2 loading bays with truck capacity constraints
 * Each truck can hold 18 containers maximum
 * Trucks can only be loaded when both a loader AND bay are free
 * Implements congestion handling and wait time tracking
 */
public class LoadingBay {
    private final Semaphore bayAvailability = new Semaphore(2, true); // 2 bays, fair access
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    private final AtomicInteger containersWaiting = new AtomicInteger(0);
    private final AtomicInteger totalTrucksCreated = new AtomicInteger(0);
    
    // Loading bay parameters
    private static final int MAX_BAYS = 2;
    private static final int CONTAINERS_PER_TRUCK = 18;
    private static final int LOADING_TIME_PER_CONTAINER = 2000; // 2 seconds per container
    
    /**
     * Get truck for loading containers
     * Handles truck assignment, bay allocation, and congestion
     * @param containerQueueSize Current size of container queue
     * @return Truck ready for loading
     * @throws InterruptedException if thread is interrupted
     */
    public Truck getTruckForLoading(int containerQueueSize) throws InterruptedException {
        bayLock.lock();
        try {
            // First, try to find an existing truck with space
            for (Truck truck : currentTrucks.values()) {
                if (!truck.isFull()) {
                    return truck;
                }
            }
            
            // No existing truck has space, need a new truck
            // Check if we can get a bay immediately
            if (bayAvailability.tryAcquire()) {
                // Got a bay, create new truck
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                totalTrucksCreated.incrementAndGet();
                
                System.out.printf("LoadingBay: Truck-%d assigned to bay (Thread: %s)%n", 
                    newTruck.getId(), Thread.currentThread().getName());
                
                return newTruck;
            } else {
                // No bay available - truck must wait
                int futureTruckId = truckCounter.get();
                System.out.printf("Truck-%d: Waiting for loading bay to be free.%n", futureTruckId);
                
                containersWaiting.incrementAndGet();
                
                // Block until a bay becomes available
                bayAvailability.acquire();
                containersWaiting.decrementAndGet();
                
                // Now we have a bay, create the truck
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                totalTrucksCreated.incrementAndGet();
                
                System.out.printf("LoadingBay: Truck-%d assigned to bay after waiting (Thread: %s)%n", 
                    newTruck.getId(), Thread.currentThread().getName());
                
                return newTruck;
            }
        } finally {
            bayLock.unlock();
        }
    }
    
    /**
     * Process truck departure and update statistics
     * @param truck Departing truck
     * @param waitTimes List to record wait times
     * @param loadTimes List to record load times
     */
    public void truckDeparted(Truck truck, List<Long> waitTimes, List<Long> loadTimes) {
        bayLock.lock();
        try {
            currentTrucks.remove(truck.getId());
            bayAvailability.release(); // Free up the bay for next truck
            
            // Calculate truck timing statistics
            long currentTime = System.currentTimeMillis();
            long totalTime = currentTime - truck.getCreationTime();
            
            // Record timing statistics
            if (waitTimes != null) {
                waitTimes.add(totalTime);
            }
            if (loadTimes != null) {
                loadTimes.add(totalTime); // Simplified - total time as load time
            }
            
            System.out.printf("LoadingBay: Truck-%d departed, bay freed (Thread: %s)%n", 
                truck.getId(), Thread.currentThread().getName());
            
        } finally {
            bayLock.unlock();
        }
    }
    
    /**
     * Get number of available loading bays
     * @return Number of free bays
     */
    public int getAvailableBays() {
        return bayAvailability.availablePermits();
    }
    
    /**
     * Get number of containers waiting for trucks
     * @return Waiting container count
     */
    public int getWaitingContainers() {
        return containersWaiting.get();
    }
    
    /**
     * Get total number of trucks created
     * @return Total trucks created since simulation start
     */
    public int getTotalTrucksCreated() {
        return totalTrucksCreated.get();
    }
    
    /**
     * Get number of currently active trucks
     * @return Number of trucks currently being loaded
     */
    public int getActiveTrucks() {
        return currentTrucks.size();
    }
    
    /**
     * Get current truck details
     * @return Map of active trucks with their details
     */
    public Map<Integer, String> getActiveTruckDetails() {
        Map<Integer, String> details = new HashMap<>();
        for (Map.Entry<Integer, Truck> entry : currentTrucks.entrySet()) {
            Truck truck = entry.getValue();
            details.put(entry.getKey(), 
                String.format("Truck-%d: %d/%d containers", 
                    truck.getId(), truck.getContainerCount(), CONTAINERS_PER_TRUCK));
        }
        return details;
    }
    
    /**
     * Check if loading bay is at capacity
     * @return true if all bays are occupied
     */
    public boolean isAtCapacity() {
        return bayAvailability.availablePermits() == 0;
    }
    
    /**
     * Get bay utilization percentage
     * @return Percentage of bays currently in use
     */
    public double getBayUtilization() {
        int occupiedBays = MAX_BAYS - bayAvailability.availablePermits();
        return (occupiedBays * 100.0) / MAX_BAYS;
    }
    
    /**
     * Get estimated loading time for a truck
     * @param containerCount Number of containers to load
     * @return Estimated time in milliseconds
     */
    public long getEstimatedLoadingTime(int containerCount) {
        return containerCount * LOADING_TIME_PER_CONTAINER;
    }
    
    /**
     * Force dispatch trucks that have been waiting too long
     * @param maxWaitTimeMs Maximum wait time in milliseconds
     * @return Number of trucks that were force dispatched
     */
    public int forceDispatchOldTrucks(long maxWaitTimeMs) {
        bayLock.lock();
        try {
            int dispatchedCount = 0;
            long currentTime = System.currentTimeMillis();
            
            for (Truck truck : new ArrayList<>(currentTrucks.values())) {
                long truckAge = currentTime - truck.getCreationTime();
                if (truckAge > maxWaitTimeMs && truck.getContainerCount() > 0) {
                    System.out.printf("LoadingBay: Force dispatching Truck-%d (waited %.1f seconds with %d containers)%n", 
                        truck.getId(), truckAge / 1000.0, truck.getContainerCount());
                    
                    truck.setStatus("FORCE_DEPARTED");
                    currentTrucks.remove(truck.getId());
                    bayAvailability.release();
                    dispatchedCount++;
                }
            }
            return dispatchedCount;
        } finally {
            bayLock.unlock();
        }
    }
    
    /**
     * Force departure of all current trucks (for simulation end)
     * @return Number of trucks that were forced to depart
     */
    public int forceAllTrucksDeparture() {
        bayLock.lock();
        try {
            int departedCount = 0;
            for (Truck truck : new ArrayList<>(currentTrucks.values())) {
                if (truck.getContainerCount() > 0) {
                    System.out.printf("LoadingBay: Forcing departure of Truck-%d with %d containers%n", 
                        truck.getId(), truck.getContainerCount());
                    currentTrucks.remove(truck.getId());
                    bayAvailability.release();
                    departedCount++;
                }
            }
            return departedCount;
        } finally {
            bayLock.unlock();
        }
    }
}