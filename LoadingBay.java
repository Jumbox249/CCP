import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class LoadingBay {
    private final Semaphore bayAvailability = new Semaphore(2, true);
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    private final AtomicInteger containersWaiting = new AtomicInteger(0);
    private final AtomicInteger totalTrucksCreated = new AtomicInteger(0);
    
    private static final int MAX_BAYS = 2;
    private static final int CONTAINERS_PER_TRUCK = 18;
    private static final int LOADING_TIME_PER_CONTAINER = 2000;
    
    public Truck getTruckForLoading(int containerQueueSize) throws InterruptedException {
        bayLock.lock();
        try {
            for (Truck truck : currentTrucks.values()) {
                if (!truck.isFull()) {
                    return truck;
                }
            }
            
            if (bayAvailability.tryAcquire()) {
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                totalTrucksCreated.incrementAndGet();
                
                System.out.printf("LoadingBay: Truck-%d assigned to bay (Thread: %s)%n",
                    newTruck.getId(), Thread.currentThread().getName());
                
                return newTruck;
            } else {
                int futureTruckId = truckCounter.get();
                System.out.printf("Truck-%d: Waiting for loading bay to be free.%n", futureTruckId);
                
                containersWaiting.incrementAndGet();
                
                bayAvailability.acquire();
                containersWaiting.decrementAndGet();
                
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
    
    public void truckDeparted(Truck truck, List<Long> waitTimes, List<Long> loadTimes) {
        bayLock.lock();
        try {
            currentTrucks.remove(truck.getId());
            bayAvailability.release();
            
            long waitTime = truck.getWaitTime();
            long loadTime = truck.getLoadingTime();
            
            if (waitTimes != null && waitTime > 0) {
                waitTimes.add(waitTime);
            }
            if (loadTimes != null && loadTime > 0) {
                loadTimes.add(loadTime);
            }
            
            System.out.printf("LoadingBay: Truck-%d departed (Wait: %.2fs, Load: %.2fs), bay freed (Thread: %s)%n",
                truck.getId(), waitTime / 1000.0, loadTime / 1000.0, Thread.currentThread().getName());
            
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
    
    public int getTotalTrucksCreated() {
        return totalTrucksCreated.get();
    }
    
    public int getActiveTrucks() {
        return currentTrucks.size();
    }
    
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
    
    public boolean isAtCapacity() {
        return bayAvailability.availablePermits() == 0;
    }
    
    public double getBayUtilization() {
        int occupiedBays = MAX_BAYS - bayAvailability.availablePermits();
        return (occupiedBays * 100.0) / MAX_BAYS;
    }
    
    public long getEstimatedLoadingTime(int containerCount) {
        return containerCount * LOADING_TIME_PER_CONTAINER;
    }
    
    public int forceDispatchOldTrucks(long maxWaitTimeMs) {
        bayLock.lock();
        try {
            int dispatchedCount = 0;
            long currentTime = System.currentTimeMillis();
            
            for (Truck truck : new ArrayList<>(currentTrucks.values())) {
                long truckAge = currentTime - truck.getCreationTime();
                if (truckAge > 15000 && truck.getContainerCount() > 0) {
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