import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Labelling Station - Assigns tracking numbers and labels to packages
 * Includes scanner verification for label accuracy
 */
public class LabellingStation {
    private static final Logger logger = Logger.getLogger(LabellingStation.class.getName());
    private final AtomicInteger trackingCounter = new AtomicInteger(100000);
    private final Random random = ThreadLocalRandom.current();
    
    public Order labelOrder(Order order) throws InterruptedException {
        logger.info(String.format("Thread [%s]: Starting to label order #%d",
            Thread.currentThread().getName(), order.getId()));
        
        // Generate tracking number
        String trackingNumber = "SC" + trackingCounter.getAndIncrement();
        order.setTrackingNumber(trackingNumber);
        
        // Simulate labelling process (1-2 seconds)
        TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(1000));
        
        // Scanner verification (1% chance of label error)
        if (random.nextDouble() < 0.01) {
            logger.warning(String.format("Thread [%s]: Order #%d failed label scanner - incorrect label",
                Thread.currentThread().getName(), order.getId()));
            order.setStatus("REJECTED_LABEL_ERROR");
            return null;
        }
        
        order.setLabelled(true);
        order.setStatus("LABELLED");
        logger.info(String.format("Thread [%s]: Order #%d labelled with tracking #%s",
            Thread.currentThread().getName(), order.getId(), trackingNumber));
        return order;
    }
}