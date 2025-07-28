// OrderIntakeSystem.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Order Intake System - Receives and verifies orders
 * Processes one order every 500ms with payment, inventory, and address verification
 */
class OrderIntakeSystem {
    private static final Logger logger = Logger.getLogger(OrderIntakeSystem.class.getName());
    private final BlockingQueue<Order> orderQueue;
    private final AtomicInteger rejectedOrders;
    private final Random random = ThreadLocalRandom.current();
    
    public OrderIntakeSystem(BlockingQueue<Order> orderQueue, AtomicInteger rejectedOrders) {
        this.orderQueue = orderQueue;
        this.rejectedOrders = rejectedOrders;
    }
    
    public Order receiveOrder(int orderId) {
        Order order = new Order(orderId);
        logger.info(String.format("Thread [%s]: Receiving order #%d", 
            Thread.currentThread().getName(), orderId));
        
        // Verify payment (95% success rate)
        boolean paymentVerified = random.nextDouble() < 0.95;
        order.setPaymentVerified(paymentVerified);
        
        if (!paymentVerified) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Payment verification failed",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_PAYMENT");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify inventory (90% success rate)
        boolean inventoryAvailable = random.nextDouble() < 0.90;
        order.setInventoryAvailable(inventoryAvailable);
        
        if (!inventoryAvailable) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Inventory unavailable",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_INVENTORY");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify address (98% success rate)
        boolean addressValid = random.nextDouble() < 0.98;
        order.setAddressValid(addressValid);
        
        if (!addressValid) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Invalid address",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_ADDRESS");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        order.setStatus("VERIFIED");
        logger.info(String.format("Thread [%s]: Order #%d verified successfully",
            Thread.currentThread().getName(), orderId));
        return order;
    }
}