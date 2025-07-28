import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Sorting Area - Groups orders into batches and creates containers
 * Batches of 6 orders, containers hold 30 boxes max
 */
public class SortingArea {
    private static final Logger logger = Logger.getLogger(SortingArea.class.getName());
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>();
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    
    public SortingArea(BlockingQueue<Container> loadingQueue) {
        this.loadingQueue = loadingQueue;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
    }
    
    public void sortOrder(Order order) throws InterruptedException {
        synchronized (sortingLock) {
            logger.info(String.format("Thread [%s]: Sorting order #%d",
                Thread.currentThread().getName(), order.getId()));
            
            currentBatch.add(order);
            
            // Process batch when it reaches 6 orders
            if (currentBatch.size() >= 6) {
                processBatch();
            }
        }
    }
    
    private void processBatch() throws InterruptedException {
        logger.info(String.format("Thread [%s]: Processing batch of %d orders",
            Thread.currentThread().getName(), currentBatch.size()));
        
        for (Order order : currentBatch) {
            if (!currentContainer.addOrder(order)) {
                // Container is full, send to loading and create new one
                logger.info(String.format("Thread [%s]: Container #%d full with %d boxes, sending to loading",
                    Thread.currentThread().getName(), currentContainer.getId(), currentContainer.getSize()));
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
                    logger.info(String.format("Thread [%s]: Flushing final container #%d with %d boxes",
                        Thread.currentThread().getName(), currentContainer.getId(), currentContainer.getSize()));
                    loadingQueue.offer(currentContainer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}