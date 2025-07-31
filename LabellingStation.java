import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class LabellingStation {
    private final AtomicInteger trackingCounter = new AtomicInteger(100000);
    private final Random random = ThreadLocalRandom.current();
    
    private static final double LABELLING_SUCCESS_RATE = 0.99;
    private static final int BASE_LABELLING_TIME = 100;
    private static final int VARIABLE_LABELLING_TIME = 100;
    private static final int QUALITY_SCANNER_TIME = 100;
    private static final String TRACKING_PREFIX = "A";
    
    public Order labelOrder(Order order) throws InterruptedException {
        System.out.printf("LabellingStation: Starting to label Order #%d (Thread: %s)%n",
            order.getId(), Thread.currentThread().getName());
        
        String trackingNumber = generateTrackingNumber();
        order.setTrackingNumber(trackingNumber);
        
        Thread.sleep(BASE_LABELLING_TIME + random.nextInt(VARIABLE_LABELLING_TIME));
        
        System.out.printf("LabellingStation: Quality scanning Order #%d (Thread: %s)%n",
            order.getId(), Thread.currentThread().getName());
        Thread.sleep(QUALITY_SCANNER_TIME);
        
        if (random.nextDouble() >= LABELLING_SUCCESS_RATE) {
            order.setStatus("REJECTED_LABEL_ERROR");
            SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Label error detected by quality scanner");
            System.out.printf("LabellingStation: Order #%d REJECTED - label error (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            return null;
        }
        
        if (!verifyLabel(order)) {
            order.setStatus("REJECTED_LABEL_VERIFICATION");
            SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Label verification failed");
            System.out.printf("LabellingStation: Order #%d REJECTED - label verification failed (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            return null;
        }
        
        order.setLabelled(true);
        order.setStatus("LABELLED");
        
        System.out.printf("LabellingStation: Order #%d labelled successfully with tracking %s (Thread: %s)%n",
            order.getId(), trackingNumber, Thread.currentThread().getName());
        
        return order;
    }
    
    private String generateTrackingNumber() {
        int trackingNum = 400 + trackingCounter.getAndIncrement() - 100000;
        return TRACKING_PREFIX + String.format("%06d", trackingNum);
    }
    
    private boolean verifyLabel(Order order) {
        try {
            Thread.sleep(25);
            
            if (order.getTrackingNumber() == null ||
                !order.getTrackingNumber().startsWith(TRACKING_PREFIX)) {
                return false;
            }
            
            if (random.nextDouble() >= 0.998) {
                System.out.printf("LabellingStation: Barcode unreadable for Order #%d%n", order.getId());
                return false;
            }
            
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
    
    public String getNextTrackingNumber() {
        int nextNum = 400 + trackingCounter.get() - 100000;
        return TRACKING_PREFIX + String.format("%06d", nextNum);
    }
    
    public int getLabelsProcessed() {
        return trackingCounter.get() - 100000;
    }
    
    public void resetTrackingCounter() {
        trackingCounter.set(100000);
    }
}