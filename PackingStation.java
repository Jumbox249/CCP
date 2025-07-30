import java.util.Random;
import java.util.concurrent.*;

/**
 * Packing Station - Processes one order at a time with scanner verification
 * Uses synchronized block to ensure single-order processing as per requirements
 * Includes quality control scanner with error detection
 */
public class PackingStation {
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    // Packing parameters
    private static final double PACKING_SUCCESS_RATE = 0.98; // 98% success (2% packing errors)
    private static final int BASE_PACKING_TIME = 2000; // 2 seconds base time
    private static final int VARIABLE_PACKING_TIME = 2000; // +0-2 seconds variable
    private static final int SCANNER_VERIFICATION_TIME = 500; // 500ms scanner check
    
    /**
     * Constructor
     * @param packingLock Synchronization lock for single-order processing
     */
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    /**
     * Pack order into shipping box with scanner verification
     * Only one order can be packed at a time (synchronized)
     * @param order Order to be packed
     * @return Packed order or null if rejected
     * @throws InterruptedException if thread is interrupted
     */
    public Order packOrder(Order order) throws InterruptedException {
        // Synchronized block ensures only one order packed at a time
        synchronized (packingLock) {
            System.out.printf("PackingStation: Starting to pack Order #%d (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            
            // Simulate packing process (2-4 seconds)
            Thread.sleep(BASE_PACKING_TIME + random.nextInt(VARIABLE_PACKING_TIME));
            
            // Simulate scanner verification process
            System.out.printf("PackingStation: Scanning packed Order #%d for verification (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            Thread.sleep(SCANNER_VERIFICATION_TIME);
            
            // Scanner verification - check for packing errors (2% failure rate)
            if (random.nextDouble() >= PACKING_SUCCESS_RATE) {
                order.setStatus("REJECTED_PACKING_ERROR");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Scanner detected packing error");
                System.out.printf("PackingStation: Order #%d REJECTED - packing error detected (Thread: %s)%n", 
                    order.getId(), Thread.currentThread().getName());
                return null;
            }
            
            // Additional check - ensure contents match order
            if (!verifyContents(order)) {
                order.setStatus("REJECTED_CONTENT_MISMATCH");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Contents do not match order");
                System.out.printf("PackingStation: Order #%d REJECTED - content mismatch (Thread: %s)%n", 
                    order.getId(), Thread.currentThread().getName());
                return null;
            }
            
            // Successfully packed
            order.setPacked(true);
            order.setStatus("PACKED");
            
            System.out.printf("PackingStation: Order #%d packed successfully (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            
            return order;
        }
    }
    
    /**
     * Verify that packed contents match the original order
     * @param order Order to verify
     * @return true if contents match
     */
    private boolean verifyContents(Order order) {
        // Simulate content verification process
        try {
            Thread.sleep(100 * order.getItems().size()); // 100ms per item
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        // 99.5% success rate for content verification
        return random.nextDouble() < 0.995;
    }
    
    /**
     * Check if packing station is currently busy
     * @return true if station is processing an order
     */
    public boolean isBusy() {
        // Try to acquire lock without blocking
        synchronized (packingLock) {
            return false; // If we got here, it's not busy
        }
    }
    
    /**
     * Get estimated processing time for current queue
     * @param queueSize Number of orders waiting
     * @return Estimated time in milliseconds
     */
    public long getEstimatedProcessingTime(int queueSize) {
        long avgProcessingTime = BASE_PACKING_TIME + (VARIABLE_PACKING_TIME / 2) + SCANNER_VERIFICATION_TIME;
        return avgProcessingTime * queueSize;
    }
}