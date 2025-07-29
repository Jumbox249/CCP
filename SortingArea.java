import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sorting Area - Groups orders into batches and creates containers
 * Batches of 6 orders, containers hold 30 boxes max
 */
public class SortingArea {
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>();
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final AtomicInteger batchCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    
    public SortingArea(BlockingQueue<Container> loadingQueue) {
        this.loadingQueue = loadingQueue;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
    }
    
    public void sortOrder(Order order) throws InterruptedException {
        synchronized (sortingLock) {
            int currentBatchNumber = batchCounter.get();
            SwiftCartSimulation.BusinessLogger.logOrderSorted(order, currentBatchNumber);
            
            currentBatch.add(order);
            
            // Process batch when it reaches 6 orders
            if (currentBatch.size() >= 6) {
                processBatch();
                batchCounter.incrementAndGet();
            }
        }
    }
    
    private void processBatch() throws InterruptedException {
        for (Order order : currentBatch) {
            if (!currentContainer.addOrder(order)) {
                // Container is full, send to loading and create new one
                loadingQueue.put(currentContainer);
                currentContainer = new Container(containerCounter.getAndIncrement());
                currentContainer.addOrder(order);
            }
            order.setStatus("SORTED");
        }
        
        currentBatch.clear();
    }
    
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                if (!currentBatch.isEmpty()) {
                    processBatch();
                }
                if (currentContainer.getSize() > 0) {
                    loadingQueue.offer(currentContainer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}