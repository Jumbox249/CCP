import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Labelling Station - Assigns tracking numbers and labels to packages
 * Includes quality scanner verification for label accuracy
 * Processes one box at a time through quality scanner as per requirements
 */
public class LabellingStation {
    private final AtomicInteger trackingCounter = new AtomicInteger(100000);
    private final Random random = ThreadLocalRandom.current();
    
    // Labelling parameters
    private static final double LABELLING_SUCCESS_RATE = 0.99; // 99% success (1% label errors)
    private static final int BASE_LABELLING_TIME = 1000; // 1 second base time
    private static final int VARIABLE_LABELLING_TIME = 1000; // +0-1 second variable
    private static final int QUALITY_SCANNER_TIME = 800; // 800ms quality scan
    private static final String TRACKING_PREFIX = "A"; // Tracking number prefix
    
    /**
     * Label order with tracking number and verify through quality scanner
     * @param order Order to be labelled
     * @return Labelled order or null if rejected
     * @throws InterruptedException if thread is interrupted
     */
    public Order labelOrder(Order order) throws InterruptedException {
        System.out.printf("LabellingStation: Starting to label Order #%d (Thread: %s)%n", 
            order.getId(), Thread.currentThread().getName());
        
        // Generate unique tracking number
        String trackingNumber = generateTrackingNumber();
        order.setTrackingNumber(trackingNumber);
        
        // Simulate labelling process (1-2 seconds)
        Thread.sleep(BASE_LABELLING_TIME + random.nextInt(VARIABLE_LABELLING_TIME));
        
        // Quality scanner verification - one box at a time as per requirements
        System.out.printf("LabellingStation: Quality scanning Order #%d (Thread: %s)%n", 
            order.getId(), Thread.currentThread().getName());
        Thread.sleep(QUALITY_SCANNER_TIME);
        
        // Scanner check for label errors (1% failure rate)
        if (random.nextDouble() >= LABELLING_SUCCESS_RATE) {
            order.setStatus("REJECTED_LABEL_ERROR");
            SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Label error detected by quality scanner");
            System.out.printf("LabellingStation: Order #%d REJECTED - label error (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            return null;
        }
        
        // Additional verification checks
        if (!verifyLabel(order)) {
            order.setStatus("REJECTED_LABEL_VERIFICATION");
            SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Label verification failed");
            System.out.printf("LabellingStation: Order #%d REJECTED - label verification failed (Thread: %s)%n", 
                order.getId(), Thread.currentThread().getName());
            return null;
        }
        
        // Successfully labelled
        order.setLabelled(true);
        order.setStatus("LABELLED");
        
        System.out.printf("LabellingStation: Order #%d labelled successfully with tracking %s (Thread: %s)%n", 
            order.getId(), trackingNumber, Thread.currentThread().getName());
        
        return order;
    }
    
    /**
     * Generate unique tracking number
     * @return Formatted tracking number (e.g., A400001)
     */
    private String generateTrackingNumber() {
        int trackingNum = 400 + trackingCounter.getAndIncrement() - 100000;
        return TRACKING_PREFIX + String.format("%06d", trackingNum);
    }
    
    /**
     * Verify label accuracy and readability
     * @param order Order to verify
     * @return true if label is valid
     */
    private boolean verifyLabel(Order order) {
        try {
            // Simulate label verification process
            Thread.sleep(200); // 200ms verification time
            
            // Check if tracking number is properly formatted
            if (order.getTrackingNumber() == null || 
                !order.getTrackingNumber().startsWith(TRACKING_PREFIX)) {
                return false;
            }
            
            // Barcode readability check (99.8% success rate)
            if (random.nextDouble() >= 0.998) {
                System.out.printf("LabellingStation: Barcode unreadable for Order #%d%n", order.getId());
                return false;
            }
            
            // Address label check
            if (!order.isAddressValid()) {
                System.out.printf("LabellingStation: Invalid address label for Order #%d%n", order.getId());
                return false;
            }
            
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Get next tracking number (for preview purposes)
     * @return Next tracking number that would be assigned
     */
    public String getNextTrackingNumber() {
        int nextNum = 400 + trackingCounter.get() - 100000;
        return TRACKING_PREFIX + String.format("%06d", nextNum);
    }
    
    /**
     * Get total labels processed
     * @return Number of tracking numbers assigned
     */
    public int getLabelsProcessed() {
        return trackingCounter.get() - 100000;
    }
    
    /**
     * Reset tracking counter (for testing purposes)
     */
    public void resetTrackingCounter() {
        trackingCounter.set(100000);
    }
}