import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Autonomous Loader - Loads containers onto trucks
 * Simulates random breakdowns and repairs
 */
public class AutonomousLoader {
    private static final Logger logger = Logger.getLogger(AutonomousLoader.class.getName());
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
            logger.warning(String.format("Thread [%s]: Loader %d is broken down, waiting for repair",
                Thread.currentThread().getName(), loaderId));
            Thread.sleep(5000); // Wait for repair
            operational = true;
            logger.info(String.format("Thread [%s]: Loader %d repaired and operational",
                Thread.currentThread().getName(), loaderId));
        }
        
        Truck truck = loadingBay.getTruckForLoading();
        
        logger.info(String.format("Thread [%s]: Loader %d loading container #%d onto truck #%d",
            Thread.currentThread().getName(), loaderId, container.getId(), truck.getId()));
        
        // Simulate loading time (3-5 seconds)
        Thread.sleep(3000 + random.nextInt(2000));
        
        if (truck.loadContainer(container)) {
            logger.info(String.format("Thread [%s]: Container #%d loaded successfully onto truck #%d",
                Thread.currentThread().getName(), container.getId(), truck.getId()));
            
            if (truck.isFull()) {
                logger.info(String.format("Thread [%s]: Truck #%d is full, dispatching",
                    Thread.currentThread().getName(), truck.getId()));
                loadingBay.truckDeparted(truck);
                trucksLoaded.incrementAndGet();
            }
        } else {
            logger.warning(String.format("Thread [%s]: Failed to load container #%d - truck full",
                Thread.currentThread().getName(), container.getId()));
        }
    }
    
    private void simulateBreakdown() {
        operational = false;
        logger.warning(String.format("Thread [%s]: BREAKDOWN - Loader %d has malfunctioned!",
            Thread.currentThread().getName(), loaderId));
    }
}