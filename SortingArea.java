import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sorting Area - Groups orders into batches and creates containers
 * Batches of 6 orders, containers hold 30 orders max
 * FIXED: Better handling of partial batches and container edge cases
 */
public class SortingArea {
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>();
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final AtomicInteger batchCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    private final AtomicInteger containersCreated;
    
    public SortingArea(BlockingQueue<Container> loadingQueue, AtomicInteger containersCreated) {
        this.loadingQueue = loadingQueue;
        this.containersCreated = containersCreated;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
        containersCreated.incrementAndGet();
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
            // Check if current container can fit this order
            if (!currentContainer.addOrder(order)) {
                // Container is full, send to loading and create new one
                if (currentContainer.getSize() > 0) { // Only send non-empty containers
                    loadingQueue.put(currentContainer);
                    System.out.printf("Sorter: Container #%d completed with %d orders, sent to loading bay%n", 
                        currentContainer.getId(), currentContainer.getSize());
                }
                
                // Create new container
                currentContainer = new Container(containerCounter.getAndIncrement());
                containersCreated.incrementAndGet();
                System.out.printf("Sorter: Created new Container #%d%n", currentContainer.getId());
                
                // Add the order to the new container
                if (!currentContainer.addOrder(order)) {
                    System.err.printf("ERROR: Order %d couldn't be added to new container!%n", order.getId());
                    continue;
                }
            }
            order.setStatus("SORTED");
        }
        
        currentBatch.clear();
        System.out.printf("Sorter: Batch #%d processed. Current container #%d has %d orders%n", 
            batchCounter.get(), currentContainer.getId(), currentContainer.getSize());
    }
    
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                // Process any remaining orders in current batch
                if (!currentBatch.isEmpty()) {
                    System.out.printf("Sorter: Processing final partial batch with %d orders%n", currentBatch.size());
                    processBatch();
                }
                
                // Send current container if it has any orders
                if (currentContainer.getSize() > 0) {
                    loadingQueue.offer(currentContainer);
                    System.out.printf("Sorter: Final container #%d sent with %d orders%n", 
                        currentContainer.getId(), currentContainer.getSize());
                } else {
                    System.out.printf("Sorter: Final container #%d was empty, not sent%n", currentContainer.getId());
                    // Decrement counter since we created but didn't use this container
                    containersCreated.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Sorter: Interrupted while flushing remaining orders");
            }
        }
    }
    
    public int getCurrentBatchSize() {
        synchronized (sortingLock) {
            return currentBatch.size();
        }
    }
    
    public int getCurrentContainerSize() {
        synchronized (sortingLock) {
            return currentContainer.getSize();
        }
    }
}