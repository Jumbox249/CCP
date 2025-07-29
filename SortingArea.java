import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sorting Area - Groups boxes into batches and creates containers
 * CORRECTED: Batches of 6 boxes, containers hold 30 boxes max (as per requirements)
 */
public class SortingArea {
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>(); // Batch of boxes (packed orders)
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
    
    public void sortBox(Order box) throws InterruptedException {
        synchronized (sortingLock) {
            int currentBatchNumber = batchCounter.get();
            System.out.printf("Sorter: Added Order #%d to Batch #%d (Thread: %s)%n", 
                box.getId(), currentBatchNumber, Thread.currentThread().getName());
            
            currentBatch.add(box);
            
            // Process batch when it reaches 6 boxes (as per requirements)
            if (currentBatch.size() >= 6) {
                processBatch();
                batchCounter.incrementAndGet();
            }
        }
    }
    
    private void processBatch() throws InterruptedException {
        for (Order box : currentBatch) {
            // Check if current container can fit this box
            if (!currentContainer.addBox(box)) {
                // Container is full, send to loading and create new one
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.put(currentContainer);
                    System.out.printf("Sorter: Container #%d completed with %d boxes, sent to loading bay%n", 
                        currentContainer.getId(), currentContainer.getBoxCount());
                }
                
                // Create new container
                currentContainer = new Container(containerCounter.getAndIncrement());
                containersCreated.incrementAndGet();
                System.out.printf("Sorter: Created new Container #%d%n", currentContainer.getId());
                
                // Add the box to the new container
                if (!currentContainer.addBox(box)) {
                    System.err.printf("ERROR: Box %d couldn't be added to new container!%n", box.getId());
                    continue;
                }
            }
            box.setStatus("SORTED");
        }
        
        currentBatch.clear();
        System.out.printf("Sorter: Batch #%d processed. Current container #%d has %d boxes%n", 
            batchCounter.get(), currentContainer.getId(), currentContainer.getBoxCount());
    }
    
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                // Process any remaining boxes in current batch
                if (!currentBatch.isEmpty()) {
                    System.out.printf("Sorter: Processing final partial batch with %d boxes%n", currentBatch.size());
                    processBatch();
                }
                
                // Send current container if it has any boxes
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.offer(currentContainer);
                    System.out.printf("Sorter: Final container #%d sent with %d boxes%n", 
                        currentContainer.getId(), currentContainer.getBoxCount());
                } else {
                    System.out.printf("Sorter: Final container #%d was empty, not sent%n", currentContainer.getId());
                    // Decrement counter since we created but didn't use this container
                    containersCreated.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Sorter: Interrupted while flushing remaining boxes");
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
            return currentContainer.getBoxCount();
        }
    }
}