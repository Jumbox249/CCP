import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Loading Bay - Manages 2 bays with truck capacity constraints
 * Each truck can hold containers maximum
 * Trucks can only be loaded when both a loader AND bay are free
 * Pauses loading when 20+ containers are waiting
 * FIXED: Proper truck counting and thread-safe dispatch pause
 */
public class LoadingBay {
    private final Semaphore bayAvailability = new Semaphore(2, true); // 2 bays, fair access
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    private final AtomicInteger containersWaiting = new AtomicInteger(0);
    private final AtomicInteger totalTrucksCreated = new AtomicInteger(0); // NEW: Track total trucks created
    private final AtomicBoolean dispatchPaused = new AtomicBoolean(false);
    
    public Truck getTruckForLoading(int containerQueueSize) throws InterruptedException {
        // Check if dispatch should be paused due to container backlog
        if (containerQueueSize >= 20) {
            if (dispatchPaused.compareAndSet(false, true)) {
                SwiftCartSimulation.BusinessLogger.logDispatchPaused(containerQueueSize);
            }
            // Actually pause loading operations
            Thread.sleep(2000); // 2 second pause
            return null; // Signal to skip this loading cycle
        } else if (dispatchPaused.get() && containerQueueSize < 20) {
            if (dispatchPaused.compareAndSet(true, false)) {
                System.out.printf("Supervisor: Dispatch resumed. %d containers remaining in queue%n", containerQueueSize);
            }
        }
        
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
                totalTrucksCreated.incrementAndGet(); // Track creation
                return newTruck;
            } else {
                // No bay available - truck must wait
                // FIXED: Pre-allocate the truck ID that will be used
                int futureTruckId = truckCounter.get(); // This will be the next truck's ID
                SwiftCartSimulation.BusinessLogger.logTruckWaiting(futureTruckId);
                
                containersWaiting.incrementAndGet();
                
                // Block until a bay becomes available
                bayAvailability.acquire(); // This will block until bay is free
                containersWaiting.decrementAndGet();
                
                // Now we have a bay, create the truck with the ID we logged
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                totalTrucksCreated.incrementAndGet(); // Track creation
                return newTruck;
            }
        } finally {
            bayLock.unlock();
        }
    }
    
    public void truckDeparted(Truck truck, List<Long> waitTimes, List<Long> loadTimes) {
        bayLock.lock();
        try {
            currentTrucks.remove(truck.getId());
            bayAvailability.release(); // Free up the bay for next truck
            
            // Calculate truck timing statistics
            long currentTime = System.currentTimeMillis();
            long waitTime = currentTime - truck.getCreationTime();
            waitTimes.add(waitTime);
            loadTimes.add(waitTime); // Simplified for this simulation
            
        } finally {
            bayLock.unlock();
        }
    }
    
    public int getAvailableBays() {
        return bayAvailability.availablePermits();
    }
    
    public int getWaitingContainers() {
        return containersWaiting.get();
    }
    
    // NEW: Method to check if dispatch is currently paused
    public boolean isDispatchPaused() {
        return dispatchPaused.get();
    }
    
    // NEW: Method to get total trucks created for statistics
    public int getTotalTrucksCreated() {
        return totalTrucksCreated.get();
    }
    
    // NEW: Method to get currently active trucks
    public int getActiveTrucks() {
        return currentTrucks.size();
    }
}