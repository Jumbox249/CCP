import java.util.Random;
import java.util.concurrent.*;

/**
 * Packing Station - Processes one order at a time with scanner verification
 * Uses synchronized block to ensure single-order processing
 */
public class PackingStation {
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    public Order packOrder(Order order) throws InterruptedException {
        synchronized (packingLock) {
            // Simulate packing process (2-4 seconds)
            TimeUnit.MILLISECONDS.sleep(2000 + random.nextInt(2000));
            
            // Scanner verification (2% chance of incorrect packing)
            if (random.nextDouble() < 0.02) {
                order.setStatus("REJECTED_PACKING_ERROR");
                return null;
            }
            
            order.setPacked(true);
            order.setStatus("PACKED");
            return order;
        }
    }
}