import java.util.Random;
import java.util.concurrent.*;

/**
 * Picking Station - Robotic arms pick orders into bins
 * 4 concurrent threads with missing item verification
 */
public class PickingStation {
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
            // Simulate robotic picking time (1-3 seconds)
            TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(2000));
            
            // Check for missing items (5% chance)
            if (random.nextDouble() < 0.05) {
                order.setStatus("REJECTED_MISSING_ITEMS");
                return null;
            }
            
            // Simulate item verification
            for (String item : order.getItems()) {
                TimeUnit.MILLISECONDS.sleep(200); // 200ms per item
            }
            
            order.setStatus("PICKED");
            return order;
            
        } finally {
            capacity.release(); // Always release permit
        }
    }
}