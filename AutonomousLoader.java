import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Autonomous Loader - Loads containers onto trucks
 * Simulates random breakdowns and repairs
 */
public class AutonomousLoader {
    private final int loaderId;
    private final LoadingBay loadingBay;
    private final AtomicInteger trucksLoaded;
    private final Random random = ThreadLocalRandom.current();
    private volatile boolean operational = true;
    
    public AutonomousLoader(int loaderId, LoadingBay loadingBay, AtomicInteger trucksLoaded) {
        this.loaderId = loaderId;
        this.loadingBay = loadingBay;
        this.trucksLoaded = trucksLoaded;
    }
    
    public void loadContainer(Container container) throws InterruptedException {
        // Check for random breakdown (5% chance)
        if (operational && random.nextDouble() < 0.05) {
            simulateBreakdown();
        }
        
        if (!operational) {
            TimeUnit.MILLISECONDS.sleep(5000); // Wait for repair
            operational = true;
            SwiftCartSimulation.BusinessLogger.logLoaderRepaired(loaderId);
        }
        
        Truck truck = loadingBay.getTruckForLoading();
        
        // Log container movement to bay
        int bayId = (truck.getId() % 2) + 1; // Simple bay assignment
        SwiftCartSimulation.BusinessLogger.logContainerLoading(loaderId, container.getId(), bayId);
        
        // Simulate loading time (3-5 seconds)
        TimeUnit.MILLISECONDS.sleep(3000 + random.nextInt(2000));
        
        if (truck.loadContainer(container)) {            
            if (truck.isFull()) {
                SwiftCartSimulation.BusinessLogger.logTruckDeparture(truck.getId());
                loadingBay.truckDeparted(truck);
                trucksLoaded.incrementAndGet();
            }
        }
    }
    
    private void simulateBreakdown() {
        operational = false;
        SwiftCartSimulation.BusinessLogger.logLoaderBreakdown(loaderId);
    }
}