import java.util.*;

public class Truck {
    private final int id;
    private final List<Container> containers = new ArrayList<>();
    private final long creationTime;
    private volatile long firstContainerLoadTime = 0; // Track when loading actually starts
    private volatile String destination;
    private volatile String status;
    
    private static final int MAX_CONTAINERS = 18;
    private static final String DEFAULT_DESTINATION = "Distribution Centre";
    
    public Truck(int id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.destination = DEFAULT_DESTINATION;
        this.status = "LOADING";
    }
    
    public synchronized boolean loadContainer(Container container) {
        if (containers.size() < MAX_CONTAINERS) {
            // Track when loading actually starts (first container)
            if (containers.isEmpty()) {
                firstContainerLoadTime = System.currentTimeMillis();
            }
            
            containers.add(container);
            
            System.out.printf("Truck-%d: Loaded Container #%d (%d/%d containers) (Thread: %s)%n",
                id, container.getId(), containers.size(), MAX_CONTAINERS,
                Thread.currentThread().getName());
            
            if (containers.size() >= MAX_CONTAINERS) {
                status = "FULL";
            }
            
            return true;
        }
        return false;
    }
    
    public synchronized boolean isFull() {
        return containers.size() >= MAX_CONTAINERS;
    }
    
    public int getId() {
        return id;
    }
    
    public synchronized int getContainerCount() {
        return containers.size();
    }
    
    /**
     * Get truck creation time
     * @return Timestamp when truck was created
     */
    public long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Get time when first container was loaded (loading started)
     * @return Timestamp when loading began, or 0 if no containers loaded
     */
    public long getFirstContainerLoadTime() {
        return firstContainerLoadTime;
    }
    
    /**
     * Get wait time (time from creation to first container load)
     * @return Wait time in milliseconds
     */
    public long getWaitTime() {
        if (firstContainerLoadTime == 0) {
            return System.currentTimeMillis() - creationTime; // Still waiting
        }
        return firstContainerLoadTime - creationTime;
    }
    
    /**
     * Get loading time (time from first container to departure)
     * @return Loading time in milliseconds, or 0 if not departed
     */
    public long getLoadingTime() {
        if (firstContainerLoadTime == 0) {
            return 0; // No loading started yet
        }
        return System.currentTimeMillis() - firstContainerLoadTime;
    }
    
    /**
     * Get truck destination
     * @return Destination name
     */
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public synchronized List<Container> getContainers() {
        return new ArrayList<>(containers);
    }
    
    public int getMaxCapacity() {
        return MAX_CONTAINERS;
    }
    
    public synchronized double getUtilization() {
        return (containers.size() * 100.0) / MAX_CONTAINERS;
    }
    
    public synchronized int getAvailableSpace() {
        return MAX_CONTAINERS - containers.size();
    }
    
    public synchronized boolean isEmpty() {
        return containers.isEmpty();
    }
    
    public synchronized int getTotalBoxes() {
        int totalBoxes = 0;
        for (Container container : containers) {
            totalBoxes += container.getBoxCount();
        }
        return totalBoxes;
    }
    
    public synchronized double getEstimatedWeight() {
        double totalWeight = 0.0;
        for (Container container : containers) {
            totalWeight += container.getEstimatedWeight();
        }
        return totalWeight;
    }
    
    public synchronized List<String> getAllTrackingNumbers() {
        List<String> allTrackingNumbers = new ArrayList<>();
        for (Container container : containers) {
            allTrackingNumbers.addAll(container.getTrackingNumbers());
        }
        return allTrackingNumbers;
    }
    
    public synchronized String depart() {
        status = "DEPARTED";
        long departureTime = System.currentTimeMillis();
        long loadingDuration = departureTime - creationTime;
        
        return String.format("Truck-%d departed to %s with %d containers (%d boxes) after %.2f seconds",
            id, destination, containers.size(), getTotalBoxes(), loadingDuration / 1000.0);
    }
    
    public synchronized boolean validateLoad() {
        for (Container container : containers) {
            if (!container.validateContents()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("Truck{id=%d, containers=%d/%d, status='%s', destination='%s'}", 
            id, getContainerCount(), MAX_CONTAINERS, status, destination);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Truck truck = (Truck) obj;
        return id == truck.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}