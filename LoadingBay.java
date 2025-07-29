import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Loading Bay - Manages 2 bays with truck capacity constraints
 * Each truck can hold 18 containers maximum
 */
public class LoadingBay {
    private final Semaphore bayAvailability = new Semaphore(2); // 2 bays
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    private final AtomicInteger containersWaiting = new AtomicInteger(0);
    
    public Truck getTruckForLoading() throws InterruptedException {
        bayLock.lock();
        try {
            // Find truck with space
            for (Truck truck : currentTrucks.values()) {
                if (!truck.isFull()) {
                    return truck;
                }
            }
            
            // No truck with space, try to get a bay for new truck
            if (bayAvailability.tryAcquire()) {
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                return newTruck;
            }
            
            // No bay available - log truck waiting and container buildup
            int newTruckId = truckCounter.get();
            SwiftCartSimulation.BusinessLogger.logTruckWaiting(newTruckId);
            
            int waitingContainers = containersWaiting.incrementAndGet();
            if (waitingContainers >= 20) {
                SwiftCartSimulation.BusinessLogger.logDispatchPaused(waitingContainers);
            }
            
            // Wait for bay to become available
            bayAvailability.acquire();
            containersWaiting.decrementAndGet();
            
            Truck newTruck = new Truck(truckCounter.getAndIncrement());
            currentTrucks.put(newTruck.getId(), newTruck);
            return newTruck;
            
        } finally {
            bayLock.unlock();
        }
    }
    
    public void truckDeparted(Truck truck) {
        bayLock.lock();
        try {
            currentTrucks.remove(truck.getId());
            bayAvailability.release();
        } finally {
            bayLock.unlock();
        }
    }
}