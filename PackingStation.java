import java.util.Random;
import java.util.concurrent.*;

public class PackingStation {
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    // Optimized packing parameters for faster processing
    private static final double PACKING_SUCCESS_RATE = 0.98; // 98% success (2% packing errors)
    private static final int BASE_PACKING_TIME = 200; // Aggressively reduced to 200ms
    private static final int VARIABLE_PACKING_TIME = 200; // Aggressively reduced to 200ms (200-400ms total)
    private static final int SCANNER_VERIFICATION_TIME = 75; // Aggressively reduced to 75ms
    private static final int CONTENT_VERIFICATION_TIME_PER_ITEM = 15; // Aggressively reduced to 15ms per item
    
    /**
     * Constructor
     * @param packingLock Synchronization lock for single-order processing
     */
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    /**
     * Pack order into shipping box with optimized parallel verification
     * Only one order can be packed at a time (synchronized)
     * @param order Order to be packed
     * @return Packed order or null if rejected
     * @throws InterruptedException if thread is interrupted
     */
    public Order packOrder(Order order) throws InterruptedException {
        synchronized (packingLock) {
            System.out.printf("PackingStation: Starting to pack Order #%d (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            
            // Optimized packing process with parallel verification
            long packingStartTime = System.currentTimeMillis();
            
            // Start content verification in parallel with packing (using CompletableFuture)
            CompletableFuture<Boolean> contentVerificationFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return verifyContentsOptimized(order);
                } catch (Exception e) {
                    return false;
                }
            });
            
            // Simulate optimized packing process (0.8-1.5 seconds)
            Thread.sleep(BASE_PACKING_TIME + random.nextInt(VARIABLE_PACKING_TIME));
            
            // Optimized scanner verification (parallel with final packing steps)
            System.out.printf("PackingStation: Scanning packed Order #%d for verification (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            Thread.sleep(SCANNER_VERIFICATION_TIME);
            
            if (random.nextDouble() >= PACKING_SUCCESS_RATE) {
                contentVerificationFuture.cancel(true); // Cancel parallel verification
                order.setStatus("REJECTED_PACKING_ERROR");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Scanner detected packing error");
                System.out.printf("PackingStation: Order #%d REJECTED - packing error detected (Thread: %s)%n",
                    order.getId(), Thread.currentThread().getName());
                return null;
            }
            
            // Wait for parallel content verification to complete
            try {
                boolean contentValid = contentVerificationFuture.get(1000, TimeUnit.MILLISECONDS);
                if (!contentValid) {
                    order.setStatus("REJECTED_CONTENT_MISMATCH");
                    SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Contents do not match order");
                    System.out.printf("PackingStation: Order #%d REJECTED - content mismatch (Thread: %s)%n",
                        order.getId(), Thread.currentThread().getName());
                    return null;
                }
            } catch (ExecutionException | TimeoutException e) {
                // If verification fails or times out, reject the order
                order.setStatus("REJECTED_VERIFICATION_TIMEOUT");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Content verification timeout");
                System.out.printf("PackingStation: Order #%d REJECTED - verification timeout (Thread: %s)%n",
                    order.getId(), Thread.currentThread().getName());
                return null;
            }
            
            order.setPacked(true);
            order.setStatus("PACKED");
            
            long totalTime = System.currentTimeMillis() - packingStartTime;
            System.out.printf("PackingStation: Order #%d packed successfully in %dms (Thread: %s)%n",
                order.getId(), totalTime, Thread.currentThread().getName());
            
            return order;
        }
    }
    
    /**
     * Verify that packed contents match the original order (legacy method)
     * @param order Order to verify
     * @return true if contents match
     */
    private boolean verifyContents(Order order) {
        try {
            Thread.sleep(CONTENT_VERIFICATION_TIME_PER_ITEM * order.getItems().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        return random.nextDouble() < 0.995;
    }
    
    private boolean verifyContentsOptimized(Order order) {
        try {
            Thread.sleep(CONTENT_VERIFICATION_TIME_PER_ITEM * order.getItems().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        // 99.5% success rate for content verification
        return random.nextDouble() < 0.995;
    }
    
    /**
     * Optimized content verification that can run in parallel
     * @param order Order to verify
     * @return true if contents match
     */
    private boolean verifyContentsOptimized(Order order) {
        // Optimized verification process - faster per-item checking
        try {
            Thread.sleep(CONTENT_VERIFICATION_TIME_PER_ITEM * order.getItems().size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        return random.nextDouble() < 0.995;
    }
    
    public boolean isBusy() {
        synchronized (packingLock) {
            return false;
        }
    }
    
    /**
     * Get estimated processing time for current queue (optimized)
     * @param queueSize Number of orders waiting
     * @return Estimated time in milliseconds
     */
    public long getEstimatedProcessingTime(int queueSize) {
        // Optimized average processing time: base + half variable + scanner time
        // Content verification now runs in parallel, so it doesn't add to total time
        long avgProcessingTime = BASE_PACKING_TIME + (VARIABLE_PACKING_TIME / 2) + SCANNER_VERIFICATION_TIME;
        return avgProcessingTime * queueSize;
    }
    
    /**
     * Get optimized processing statistics
     * @return String with current optimization details
     */
    public String getOptimizationStats() {
        long avgTime = BASE_PACKING_TIME + (VARIABLE_PACKING_TIME / 2) + SCANNER_VERIFICATION_TIME;
        return String.format("Optimized Packing: %d-%dms base + %dms scanner (content verification parallel)",
            BASE_PACKING_TIME, BASE_PACKING_TIME + VARIABLE_PACKING_TIME, SCANNER_VERIFICATION_TIME);
    }
}