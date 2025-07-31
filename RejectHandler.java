import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RejectHandler {
    private final BlockingQueue<Order> rejectedOrders;
    private final AtomicInteger totalRejectedCount;
    private volatile boolean running = true;
    
    public RejectHandler(AtomicInteger totalRejectedCount) {
        this.rejectedOrders = new LinkedBlockingQueue<>();
        this.totalRejectedCount = totalRejectedCount;
    }
    
    public void addRejectedOrder(Order order, String reason) {
        try {
            order.setStatus("REJECTED_" + reason.toUpperCase().replace(" ", "_"));
            rejectedOrders.put(order);
            System.out.printf("RejectHandler: Order #%d rejected - %s (Thread: %s)%n",
                order.getId(), reason, Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void startHandler() {
        Thread handlerThread = new Thread(() -> {
            Thread.currentThread().setName("RejectHandler-1");
            while (running) {
                try {
                    Order rejectedOrder = rejectedOrders.poll(1, TimeUnit.SECONDS);
                    if (rejectedOrder != null) {
                        processRejectedOrder(rejectedOrder);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        handlerThread.start();
    }
    
    private void processRejectedOrder(Order order) {
        System.out.printf("RejectHandler: Processing rejected Order #%d - Status: %s (Thread: %s)%n",
            order.getId(), order.getStatus(), Thread.currentThread().getName());
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        totalRejectedCount.incrementAndGet();
    }
    
    public void stop() {
        running = false;
    }
    
    public int getPendingRejects() {
        return rejectedOrders.size();
    }
    
    public void logRejectionAudit(Order order, double processingTimeSeconds) {
        System.out.printf("RejectHandler: AUDIT LOG - Order #%d rejected after %.2f seconds - Status: %s (Thread: %s)%n",
            order.getId(), processingTimeSeconds, order.getStatus(), Thread.currentThread().getName());
    }
    
    public void processRejectedOrderWithAudit(Order order, double processingTimeSeconds) {
        logRejectionAudit(order, processingTimeSeconds);
        
        processRejectedOrder(order);
    }
}