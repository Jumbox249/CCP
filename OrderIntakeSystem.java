import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class OrderIntakeSystem {
    private final AtomicInteger rejectedOrders;
    private final Random random = ThreadLocalRandom.current();
    
    private static final double PAYMENT_SUCCESS_RATE = 0.95;
    private static final double INVENTORY_SUCCESS_RATE = 0.90;
    private static final double ADDRESS_SUCCESS_RATE = 0.98;
    
    public OrderIntakeSystem(AtomicInteger rejectedOrders) {
        this.rejectedOrders = rejectedOrders;
    }
    
    public Order receiveOrder(int orderId) {
        Order order = new Order(orderId);
        
        boolean paymentVerified = random.nextDouble() < PAYMENT_SUCCESS_RATE;
        order.setPaymentVerified(paymentVerified);
        
        if (!paymentVerified) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Payment verification failed");
            order.setStatus("REJECTED_PAYMENT");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        boolean inventoryAvailable = random.nextDouble() < INVENTORY_SUCCESS_RATE;
        order.setInventoryAvailable(inventoryAvailable);
        
        if (!inventoryAvailable) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Inventory unavailable");
            order.setStatus("REJECTED_INVENTORY");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        boolean addressValid = random.nextDouble() < ADDRESS_SUCCESS_RATE;
        order.setAddressValid(addressValid);
        
        if (!addressValid) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Invalid shipping address");
            order.setStatus("REJECTED_ADDRESS");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        order.setStatus("VERIFIED");
        return order;
    }
    
    public double getRejectionRate(int totalOrders) {
        if (totalOrders == 0) return 0.0;
        return (rejectedOrders.get() * 100.0) / totalOrders;
    }
    
    public int getRejectedCount() {
        return rejectedOrders.get();
    }
}