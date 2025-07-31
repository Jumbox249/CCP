import java.util.Random;
import java.util.concurrent.*;

public class PackingStation {
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    private static final double PACKING_SUCCESS_RATE = 0.98;
    private static final int BASE_PACKING_TIME = 200;
    private static final int VARIABLE_PACKING_TIME = 200;
    private static final int SCANNER_VERIFICATION_TIME = 75;
    private static final int CONTENT_VERIFICATION_TIME_PER_ITEM = 15;
    
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    public Order packOrder(Order order) throws InterruptedException {
        synchronized (packingLock) {
            System.out.printf("PackingStation: Starting to pack Order #%d (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            
            long packingStartTime = System.currentTimeMillis();
            
            CompletableFuture<Boolean> contentVerificationFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return verifyContentsOptimized(order);
                } catch (Exception e) {
                    return false;
                }
            });
            
            Thread.sleep(BASE_PACKING_TIME + random.nextInt(VARIABLE_PACKING_TIME));
            
            System.out.printf("PackingStation: Scanning packed Order #%d for verification (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            Thread.sleep(SCANNER_VERIFICATION_TIME);
            
            if (random.nextDouble() >= PACKING_SUCCESS_RATE) {
                contentVerificationFuture.cancel(true);
                order.setStatus("REJECTED_PACKING_ERROR");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Scanner detected packing error");
                System.out.printf("PackingStation: Order #%d REJECTED - packing error detected (Thread: %s)%n",
                    order.getId(), Thread.currentThread().getName());
                return null;
            }
            
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
        
        return random.nextDouble() < 0.995;
    }
    
    public boolean isBusy() {
        synchronized (packingLock) {
            return false;
        }
    }
    
    public long getEstimatedProcessingTime(int queueSize) {
        long avgProcessingTime = BASE_PACKING_TIME + (VARIABLE_PACKING_TIME / 2) + SCANNER_VERIFICATION_TIME;
        return avgProcessingTime * queueSize;
    }
    
    public String getOptimizationStats() {
        long avgTime = BASE_PACKING_TIME + (VARIABLE_PACKING_TIME / 2) + SCANNER_VERIFICATION_TIME;
        return String.format("Optimized Packing: %d-%dms base + %dms scanner (content verification parallel)",
            BASE_PACKING_TIME, BASE_PACKING_TIME + VARIABLE_PACKING_TIME, SCANNER_VERIFICATION_TIME);
    }
}