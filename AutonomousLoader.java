import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Autonomous Loader - Loads containers onto trucks
 * Simulates random breakdowns and repairs
 * 3 loaders work concurrently across 2 loading bays
 * FIXED: Handles dispatch pause properly and accurate logging
 */
public class AutonomousLoader {
    private final int loaderId;
    private final LoadingBay loadingBay;
    private final AtomicInteger trucksDispatched;
    private final List<Long> truckWaitTimes;
    private final List<Long> truckLoadTimes;
    private final Random random = ThreadLocalRandom.current();
    private volatile boolean operational = true;
    
    public AutonomousLoader(int loaderId, LoadingBay loadingBay, AtomicInteger trucksDispatched,
                           List<Long> truckWaitTimes, List<Long> truckLoadTimes) {
        this.loaderId = loaderId;
        this.loadingBay = loadingBay;
        this.trucksDispatched = trucksDispatched;
        this.truckWaitTimes = truckWaitTimes;
        this.truckLoadTimes = truckLoadTimes;
    }
    
    public void loadContainer(Container container, int containerQueueSize) throws InterruptedException {
        // Check for random breakdown (5% chance)
        if (operational && random.nextDouble() < 0.05) {
            simulateBreakdown();
        }
        
        if (!operational) {
            TimeUnit.MILLISECONDS.sleep(5000); // Wait for repair (5 seconds)
            operational = true;
            SwiftCartSimulation.BusinessLogger.logLoaderRepaired(loaderId);
        }
        
        Truck truck = loadingBay.getTruckForLoading(containerQueueSize);
        
        // If truck is null, dispatch is paused - skip this cycle
        if (truck == null) {
            return;
        }
        
        // Log container movement to bay
        int bayId = (truck.getId() % 2) + 1; // Simple bay assignment
        SwiftCartSimulation.BusinessLogger.logContainerLoading(loaderId, container.getId(), bayId);
        
        // Simulate loading time (3-5 seconds)
        TimeUnit.MILLISECONDS.sleep(3000 + random.nextInt(2000));
        
        if (truck.loadContainer(container)) {            
            if (truck.isFull()) {
                SwiftCartSimulation.BusinessLogger.logTruckDeparture(truck.getId(), truck.getContainerCount());
                loadingBay.truckDeparted(truck, truckWaitTimes, truckLoadTimes);
                trucksDispatched.incrementAndGet();
            }
        }
    }
    
    private void simulateBreakdown() {
        operational = false;
        SwiftCartSimulation.BusinessLogger.logLoaderBreakdown(loaderId);
    }
}