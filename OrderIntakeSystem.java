import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Order Intake System - Receives and validates orders
 * Verifies payment, inventory, and shipping address
 */
public class OrderIntakeSystem {
    private final AtomicInteger rejectedOrders;
    private final Random random = ThreadLocalRandom.current();
    
    public OrderIntakeSystem(AtomicInteger rejectedOrders) {
        this.rejectedOrders = rejectedOrders;
    }
    
    public Order receiveOrder(int orderId) {
        Order order = new Order(orderId);
        
        // Verify payment (95% success rate)
        boolean paymentVerified = random.nextDouble() < 0.95;
        order.setPaymentVerified(paymentVerified);
        
        if (!paymentVerified) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Payment verification failed");
            order.setStatus("REJECTED_PAYMENT");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify inventory (90% success rate)
        boolean inventoryAvailable = random.nextDouble() < 0.90;
        order.setInventoryAvailable(inventoryAvailable);
        
        if (!inventoryAvailable) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Inventory unavailable");
            order.setStatus("REJECTED_INVENTORY");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify address (98% success rate)
        boolean addressValid = random.nextDouble() < 0.98;
        order.setAddressValid(addressValid);
        
        if (!addressValid) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Invalid address");
            order.setStatus("REJECTED_ADDRESS");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        order.setStatus("VERIFIED");
        return order;
    }
}