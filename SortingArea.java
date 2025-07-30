import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SortingArea {
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>();
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final AtomicInteger batchCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    private final AtomicInteger containersCreated;
    
    // Sorting parameters
    private static final int BATCH_SIZE = 6; // 6 boxes per batch as per requirements
    private static final int CONTAINER_CAPACITY = 30; // 30 boxes per container
    private static final int SORTING_TIME_PER_BOX = 15; // 15ms per box (aggressive optimization for 5-minute completion)
    private static final String[] REGIONAL_ZONES = {
        "North", "South", "East", "West", "Central"
    };
    
    public SortingArea(BlockingQueue<Container> loadingQueue, AtomicInteger containersCreated) {
        this.loadingQueue = loadingQueue;
        this.containersCreated = containersCreated;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
        containersCreated.incrementAndGet();
    }
    
    public void sortBox(Order box) throws InterruptedException {
        synchronized (sortingLock) {
            int currentBatchNumber = batchCounter.get();
            
            Thread.sleep(SORTING_TIME_PER_BOX);
            
            String zone = assignRegionalZone(box);
            System.out.printf("Sorter: Added Order #%d to Batch #%d (Zone: %s) (Thread: %s)%n",
                box.getId(), currentBatchNumber, zone, Thread.currentThread().getName());
            
            currentBatch.add(box);
            
            if (currentBatch.size() >= BATCH_SIZE) {
                processBatch();
                batchCounter.incrementAndGet();
            }
        }
    }
    
    private void processBatch() throws InterruptedException {
        System.out.printf("Sorter: Processing Batch #%d with %d boxes (Thread: %s)%n",
            batchCounter.get(), currentBatch.size(), Thread.currentThread().getName());
        
        for (Order box : currentBatch) {
            if (!currentContainer.addBox(box)) {
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.put(currentContainer);
                    System.out.printf("Sorter: Container #%d completed with %d boxes, sent to loading bay (Thread: %s)%n",
                        currentContainer.getId(), currentContainer.getBoxCount(), Thread.currentThread().getName());
                }
                
                currentContainer = new Container(containerCounter.getAndIncrement());
                containersCreated.incrementAndGet();
                System.out.printf("Sorter: Created new Container #%d (Thread: %s)%n",
                    currentContainer.getId(), Thread.currentThread().getName());
                
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
    
    private String assignRegionalZone(Order box) {
        int zoneIndex = Math.abs(box.getId() % REGIONAL_ZONES.length);
        return REGIONAL_ZONES[zoneIndex];
    }
    
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                if (!currentBatch.isEmpty()) {
                    System.out.printf("Sorter: Processing final partial batch with %d boxes (Thread: %s)%n",
                        currentBatch.size(), Thread.currentThread().getName());
                    processBatch();
                }
                
                if (currentContainer.getBoxCount() > 0) {
                    loadingQueue.offer(currentContainer);
                    System.out.printf("Sorter: Final container #%d sent with %d boxes (Thread: %s)%n",
                        currentContainer.getId(), currentContainer.getBoxCount(), Thread.currentThread().getName());
                } else {
                    System.out.printf("Sorter: Final container #%d was empty, not sent (Thread: %s)%n",
                        currentContainer.getId(), Thread.currentThread().getName());
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
    
    public int getCurrentContainerId() {
        synchronized (sortingLock) {
            return currentContainer.getId();
        }
    }
    
    public int getBatchesProcessed() {
        return batchCounter.get() - 1;
    }
    
    public double getContainerUtilization() {
        synchronized (sortingLock) {
            return (currentContainer.getBoxCount() * 100.0) / CONTAINER_CAPACITY;
        }
    }
}