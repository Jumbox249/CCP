import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Order Intake System - Receives and validates incoming orders
 * Verifies payment, inventory availability, and shipping address
 * Implements rejection logic with configurable failure rates
 */
public class OrderIntakeSystem {
    private final AtomicInteger rejectedOrders;
    private final Random random = ThreadLocalRandom.current();
    
    // Verification success rates (as per assignment requirements)
    private static final double PAYMENT_SUCCESS_RATE = 0.95; // 95% success
    private static final double INVENTORY_SUCCESS_RATE = 0.90; // 90% success  
    private static final double ADDRESS_SUCCESS_RATE = 0.98; // 98% success
    
    /**
     * Constructor
     * @param rejectedOrders Atomic counter for tracking rejected orders
     */
    public OrderIntakeSystem(AtomicInteger rejectedOrders) {
        this.rejectedOrders = rejectedOrders;
    }
    
    /**
     * Receive and verify an incoming order
     * @param orderId Unique order identifier
     * @return Verified order or null if rejected
     */
    public Order receiveOrder(int orderId) {
        Order order = new Order(orderId);
        
        // Step 1: Verify payment (95% success rate)
        boolean paymentVerified = random.nextDouble() < PAYMENT_SUCCESS_RATE;
        order.setPaymentVerified(paymentVerified);
        
        if (!paymentVerified) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Payment verification failed");
            order.setStatus("REJECTED_PAYMENT");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Step 2: Verify inventory availability (90% success rate)
        boolean inventoryAvailable = random.nextDouble() < INVENTORY_SUCCESS_RATE;
        order.setInventoryAvailable(inventoryAvailable);
        
        if (!inventoryAvailable) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Inventory unavailable");
            order.setStatus("REJECTED_INVENTORY");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Step 3: Verify shipping address (98% success rate)
        boolean addressValid = random.nextDouble() < ADDRESS_SUCCESS_RATE;
        order.setAddressValid(addressValid);
        
        if (!addressValid) {
            SwiftCartSimulation.BusinessLogger.logOrderRejected(orderId, "Invalid shipping address");
            order.setStatus("REJECTED_ADDRESS");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // All verifications passed
        order.setStatus("VERIFIED");
        return order;
    }
    
    /**
     * Get the current rejection rate statistics
     * @param totalOrders Total orders processed
     * @return Rejection rate as percentage
     */
    public double getRejectionRate(int totalOrders) {
        if (totalOrders == 0) return 0.0;
        return (rejectedOrders.get() * 100.0) / totalOrders;
    }
    
    /**
     * Get total rejected orders count
     * @return Number of rejected orders
     */
    public int getRejectedCount() {
        return rejectedOrders.get();
    }
}