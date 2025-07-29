import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Labelling Station - Assigns tracking numbers and labels to packages
 * Includes scanner verification for label accuracy
 */
public class LabellingStation {
    private final AtomicInteger trackingCounter = new AtomicInteger(100000);
    private final Random random = ThreadLocalRandom.current();
    
    public Order labelOrder(Order order) throws InterruptedException {
        // Generate tracking number
        String trackingNumber = "A" + (400 + trackingCounter.getAndIncrement() - 100000);
        order.setTrackingNumber(trackingNumber);
        
        // Simulate labelling process (1-2 seconds)
        TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(1000));
        
        // Scanner verification (1% chance of label error)
        if (random.nextDouble() < 0.01) {
            order.setStatus("REJECTED_LABEL_ERROR");
            return null;
        }
        
        order.setLabelled(true);
        order.setStatus("LABELLED");
        return order;
    }
}