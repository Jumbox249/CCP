import java.util.Random;
import java.util.concurrent.*;

/**
 * Picking Station - Robotic arms pick orders from shelves into bins
 * Up to 4 orders can be picked concurrently using semaphore control
 * Includes missing item verification and item-by-item processing
 */
public class PickingStation {
    private final int stationId;
    private final Semaphore capacity;
    private final Random random = ThreadLocalRandom.current();
    
    // Picking success rate (95% - 5% chance of missing items)
    private static final double PICKING_SUCCESS_RATE = 0.95;
    private static final int BASE_PICKING_TIME = 1000; // 1 second base time
    private static final int VARIABLE_PICKING_TIME = 2000; // +0-2 seconds variable
    private static final int ITEM_VERIFICATION_TIME = 200; // 200ms per item
    
    /**
     * Constructor
     * @param stationId Station identifier (1-4)
     * @param capacity Semaphore controlling concurrent picking (max 4)
     */
    public PickingStation(int stationId, Semaphore capacity) {
        this.stationId = stationId;
        this.capacity = capacity;
    }
    
    /**
     * Pick order items from shelves into bins
     * @param order Order to be picked
     * @return Processed order or null if rejected
     * @throws InterruptedException if thread is interrupted
     */
    public Order pickOrder(Order order) throws InterruptedException {
        // Acquire semaphore permit for concurrent processing (max 4)
        capacity.acquire();
        
        try {
            System.out.printf("PickingStation: Picking Order #%d (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            
            // Simulate robotic picking time (1-3 seconds)
            Thread.sleep(BASE_PICKING_TIME + random.nextInt(VARIABLE_PICKING_TIME));
            
            // Check for missing items during picking (5% failure rate)
            if (random.nextDouble() >= PICKING_SUCCESS_RATE) {
                order.setStatus("REJECTED_MISSING_ITEMS");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Missing items during picking");
                return null;
            }
            
            // Simulate item-by-item verification process
            for (String item : order.getItems()) {
                Thread.sleep(ITEM_VERIFICATION_TIME); // 200ms per item verification
                
                // Small chance of item verification failure
                if (random.nextDouble() < 0.01) { // 1% chance per item
                    order.setStatus("REJECTED_ITEM_VERIFICATION");
                    SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), 
                        "Item verification failed: " + item);
                    return null;
                }
            }
            
            // Successfully picked
            order.setStatus("PICKED");
            System.out.printf("PickingStation: Order #%d picked successfully with %d items (Thread: %s)%n", 
                order.getId(), order.getItems().size(), Thread.currentThread().getName());
            
            return order;
            
        } finally {
            // Always release semaphore permit
            capacity.release();
        }
    }
    
    /**
     * Get station ID
     * @return Station identifier
     */
    public int getStationId() {
        return stationId;
    }
    
    /**
     * Check if station has available capacity
     * @return true if capacity is available
     */
    public boolean hasCapacity() {
        return capacity.availablePermits() > 0;
    }
    
    /**
     * Get available picking capacity
     * @return Number of available permits
     */
    public int getAvailableCapacity() {
        return capacity.availablePermits();
    }
}