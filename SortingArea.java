import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Sorting Area - Groups boxes into batches and creates containers
 * Batches of 6 boxes sorted by regional zones
 * Containers hold maximum 30 boxes as per requirements
 * Thread-safe implementation with synchronized operations
 */
public class SortingArea {
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>(); // Current batch of boxes
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final AtomicInteger batchCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    private final AtomicInteger containersCreated;
    
    // Sorting parameters
    private static final int BATCH_SIZE = 6; // 6 boxes per batch as per requirements
    private static final int CONTAINER_CAPACITY = 30; // 30 boxes per container
    private static final int SORTING_TIME_PER_BOX = 150; // 150ms per box (reduced for better flow)
    private static final String[] REGIONAL_ZONES = {
        "North", "South", "East", "West", "Central"
    };
    
    /**
     * Constructor
     * @param loadingQueue Queue for sending completed containers
     * @param containersCreated Counter for tracking created containers
     */
    public SortingArea(BlockingQueue<Container> loadingQueue, AtomicInteger containersCreated) {
        this.loadingQueue = loadingQueue;
        this.containersCreated = containersCreated;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
        containersCreated.incrementAndGet();
    }
    
    /**
     * Sort box into batches and load into containers
     * @param box Packed order (box) to sort
     * @throws InterruptedException if thread is interrupted
     */
    public void sortBox(Order box) throws InterruptedException {
        synchronized (sortingLock) {
            int currentBatchNumber = batchCounter.get();
            
            // Simulate sorting time per box
            Thread.sleep(SORTING_TIME_PER_BOX);
            
            // Assign regional zone for sorting simulation
            String zone = assignRegionalZone(box);
            System.out.printf("Sorter: Added Order #%d to Batch #%d (Zone: %s) (Thread: %s)%n", 
                box.getId(), currentBatchNumber, zone, Thread.currentThread().getName());
            
            currentBatch.add(box);
            
            // Process batch when it reaches 6 boxes (as per requirements)
            if (currentBatch.size() >= BATCH_SIZE) {
                processBatch();
                batchCounter.incrementAndGet();
            }
        }
    }
    
    /**
     * Process completed batch and load into containers
     * @throws InterruptedException if thread is interrupted
     */
    private void processBatch() throws InterruptedException {
        System.out.printf("Sorter: Processing Batch #%d with %d boxes (Thread: %s)%n", 
            batchCounter.get(), currentBatch.size(), Thread.currentThread().getName());
        
        for (Order box : currentBatch) {
            // Check if current container can fit this box
            if (!currentContainer.addBox(box)) {
                // Container is full (30 boxes), send to loading and create new one
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.put(currentContainer);
                    System.out.printf("Sorter: Container #%d completed with %d boxes, sent to loading bay (Thread: %s)%n", 
                        currentContainer.getId(), currentContainer.getBoxCount(), Thread.currentThread().getName());
                }
                
                // Create new container
                currentContainer = new Container(containerCounter.getAndIncrement());
                containersCreated.incrementAndGet();
                System.out.printf("Sorter: Created new Container #%d (Thread: %s)%n", 
                    currentContainer.getId(), Thread.currentThread().getName());
                
                // Add the box to the new container
                if (!currentContainer.addBox(box)) {
                    System.err.printf("ERROR: Box %d couldn't be added to new container! (Thread: %s)%n", 
                        box.getId(), Thread.currentThread().getName());
                    continue;
                }
            }
            
            box.setStatus("SORTED");
            System.out.printf("Sorter: Box #%d added to Container #%d (%d/%d boxes) (Thread: %s)%n", 
                box.getId(), currentContainer.getId(), currentContainer.getBoxCount(), 
                CONTAINER_CAPACITY, Thread.currentThread().getName());
        }
        
        currentBatch.clear();
        System.out.printf("Sorter: Batch #%d processed. Current container #%d has %d boxes (Thread: %s)%n", 
            batchCounter.get(), currentContainer.getId(), currentContainer.getBoxCount(), 
            Thread.currentThread().getName());
    }
    
    /**
     * Assign regional zone for sorting (simulated)
     * @param box Box to assign zone to
     * @return Regional zone name
     */
    private String assignRegionalZone(Order box) {
        // Simple hash-based zone assignment for simulation
        int zoneIndex = Math.abs(box.getId() % REGIONAL_ZONES.length);
        return REGIONAL_ZONES[zoneIndex];
    }
    
    /**
     * Flush remaining boxes at simulation end
     */
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                // Process any remaining boxes in current batch
                if (!currentBatch.isEmpty()) {
                    System.out.printf("Sorter: Processing final partial batch with %d boxes (Thread: %s)%n", 
                        currentBatch.size(), Thread.currentThread().getName());
                    processBatch();
                }
                
                // Send current container if it has any boxes
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.offer(currentContainer);
                    System.out.printf("Sorter: Final container #%d sent with %d boxes (Thread: %s)%n", 
                        currentContainer.getId(), currentContainer.getBoxCount(), Thread.currentThread().getName());
                } else {
                    System.out.printf("Sorter: Final container #%d was empty, not sent (Thread: %s)%n", 
                        currentContainer.getId(), Thread.currentThread().getName());
                    // Decrement counter since we created but didn't use this container
                    containersCreated.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Sorter: Interrupted while flushing remaining boxes");
            }
        }
    }
    
    /**
     * Get current batch size
     * @return Number of boxes in current batch
     */
    public int getCurrentBatchSize() {
        synchronized (sortingLock) {
            return currentBatch.size();
        }
    }
    
    /**
     * Get current container utilization
     * @return Number of boxes in current container
     */
    public int getCurrentContainerSize() {
        synchronized (sortingLock) {
            return currentContainer.getBoxCount();
        }
    }
    
    /**
     * Get current container ID
     * @return Current container identifier
     */
    public int getCurrentContainerId() {
        synchronized (sortingLock) {
            return currentContainer.getId();
        }
    }
    
    /**
     * Get total batches processed
     * @return Number of batches completed
     */
    public int getBatchesProcessed() {
        return batchCounter.get() - 1; // Subtract 1 since we start at 1
    }
    
    /**
     * Get container utilization percentage
     * @return Percentage of current container filled
     */
    public double getContainerUtilization() {
        synchronized (sortingLock) {
            return (currentContainer.getBoxCount() * 100.0) / CONTAINER_CAPACITY;
        }
    }
}