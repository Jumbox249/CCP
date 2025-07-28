import java.util.Random;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Packing Station - Processes one order at a time with scanner verification
 * Uses synchronized block to ensure single-order processing
 */
public class PackingStation {
    private static final Logger logger = Logger.getLogger(PackingStation.class.getName());
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    public Order packOrder(Order order) throws InterruptedException {
        synchronized (packingLock) {
            logger.info(String.format("Thread [%s]: Starting to pack order #%d",
                Thread.currentThread().getName(), order.getId()));
            
            // Simulate packing process (2-4 seconds)
            Thread.sleep(2000 + random.nextInt(2000));
            
            // Scanner verification (2% chance of incorrect packing)
            if (random.nextDouble() < 0.02) {
                logger.warning(String.format("Thread [%s]: Order #%d failed scanner verification - incorrect packing",
                    Thread.currentThread().getName(), order.getId()));
                order.setStatus("REJECTED_PACKING_ERROR");
                return null;
            }
            
            order.setPacked(true);
            order.setStatus("PACKED");
            logger.info(String.format("Thread [%s]: Order #%d packed successfully",
                Thread.currentThread().getName(), order.getId()));
            return order;
        }
    }
}