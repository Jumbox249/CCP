import java.util.Random;
import java.util.concurrent.*;

public class PickingStation {
    private final int stationId;
    private final Semaphore capacity;
    private final Random random = ThreadLocalRandom.current();
    
    private static final double PICKING_SUCCESS_RATE = 0.95;
    private static final int BASE_PICKING_TIME = 150; // 0.15 second base time (aggressive optimization)
    private static final int VARIABLE_PICKING_TIME = 150; // +0-0.15 seconds variable (aggressive optimization)
    private static final int ITEM_VERIFICATION_TIME = 15; // 15ms per item (aggressive optimization)
    
    public PickingStation(int stationId, Semaphore capacity) {
        this.stationId = stationId;
        this.capacity = capacity;
    }
    
    public Order pickOrder(Order order) throws InterruptedException {
        capacity.acquire();
        
        try {
            System.out.printf("PickingStation: Picking Order #%d (Thread: %s)%n",
                order.getId(), Thread.currentThread().getName());
            
            Thread.sleep(BASE_PICKING_TIME + random.nextInt(VARIABLE_PICKING_TIME));
            
            if (random.nextDouble() >= PICKING_SUCCESS_RATE) {
                order.setStatus("REJECTED_MISSING_ITEMS");
                SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(), "Missing items during picking");
                return null;
            }
            
            for (String item : order.getItems()) {
                Thread.sleep(ITEM_VERIFICATION_TIME);
                
                if (random.nextDouble() < 0.01) {
                    order.setStatus("REJECTED_ITEM_VERIFICATION");
                    SwiftCartSimulation.BusinessLogger.logOrderRejected(order.getId(),
                        "Item verification failed: " + item);
                    return null;
                }
            }
            
            order.setStatus("PICKED");
            System.out.printf("PickingStation: Order #%d picked successfully with %d items (Thread: %s)%n",
                order.getId(), order.getItems().size(), Thread.currentThread().getName());
            
            return order;
            
        } finally {
            capacity.release();
        }
    }
    
    public int getStationId() {
        return stationId;
    }
    
    public boolean hasCapacity() {
        return capacity.availablePermits() > 0;
    }
    
    public int getAvailableCapacity() {
        return capacity.availablePermits();
    }
}