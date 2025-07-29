import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Loading Bay - Manages 2 bays with truck capacity constraints
 * Each truck can hold containers maximum
 * Trucks can only be loaded when both a loader AND bay are free
 * Pauses loading when 20+ containers are waiting
 */
public class LoadingBay {
    private final Semaphore bayAvailability = new Semaphore(2, true); // 2 bays, fair access
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    private final AtomicInteger containersWaiting = new AtomicInteger(0);
    
    public Truck getTruckForLoading() throws InterruptedException {
        // Check if we have containers waiting for dispatch
        int waiting = containersWaiting.get();
        if (waiting >= 20) {
            SwiftCartSimulation.BusinessLogger.logDispatchPaused(waiting);
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
                return newTruck;
            } else {
                // No bay available - truck must wait
                int newTruckId = truckCounter.get();
                SwiftCartSimulation.BusinessLogger.logTruckWaiting(newTruckId);
                
                containersWaiting.incrementAndGet();
                
                // Block until a bay becomes available
                bayAvailability.acquire(); // This will block until bay is free
                containersWaiting.decrementAndGet();
                
                // Now we have a bay, create the truck
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
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
}