import java.util.Random;
import java.util.concurrent.*;
import java.util.logging.*;


public class PickingStation {
    private static final Logger logger = Logger.getLogger(PickingStation.class.getName());
    private final int stationId;
    private final Semaphore capacity;
    private final Random random = ThreadLocalRandom.current();
    
    public PickingStation(int stationId, Semaphore capacity) {
        this.stationId = stationId;
        this.capacity = capacity;
    }
    
    public Order pickOrder(Order order) throws InterruptedException {
        capacity.acquire(); // Acquire permit for concurrent processing
        
        try {
            logger.info(String.format("Thread [%s]: Starting to pick order #%d at station %d",
                Thread.currentThread().getName(), order.getId(), stationId));
            
            // Simulate robotic picking time (1-3 seconds)
            Thread.sleep(1000 + random.nextInt(2000));
            
            // Check for missing items (5% chance)
            if (random.nextDouble() < 0.05) {
                logger.warning(String.format("Thread [%s]: Order #%d has missing items - rejected",
                    Thread.currentThread().getName(), order.getId()));
                order.setStatus("REJECTED_MISSING_ITEMS");
                return null;
            }
            
            // Simulate item verification
            for (String item : order.getItems()) {
                logger.info(String.format("Thread [%s]: Picked item '%s' for order #%d",
                    Thread.currentThread().getName(), item, order.getId()));
                Thread.sleep(200); // 200ms per item
            }
            
            order.setStatus("PICKED");
            logger.info(String.format("Thread [%s]: Order #%d picking completed at station %d",
                Thread.currentThread().getName(), order.getId(), stationId));
            return order;
            
        } finally {
            capacity.release(); // Always release permit
        }
    }
}