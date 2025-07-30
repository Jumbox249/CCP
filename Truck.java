import java.util.*;

/**
 * Truck class for transporting containers to delivery hubs
 * Each truck can hold up to 18 containers as per requirements
 * Thread-safe implementation with synchronized methods
 */
public class Truck {
    private final int id;
    private final List<Container> containers = new ArrayList<>();
    private final long creationTime;
    private volatile long firstContainerLoadTime = 0; // Track when loading actually starts
    private volatile String destination;
    private volatile String status;
    
    // Truck parameters
    private static final int MAX_CONTAINERS = 18; // Maximum containers per truck
    private static final String DEFAULT_DESTINATION = "Distribution Centre";
    
    /**
     * Constructor
     * @param id Unique truck identifier
     */
    public Truck(int id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.destination = DEFAULT_DESTINATION;
        this.status = "LOADING";
    }
    
    /**
     * Load a container onto the truck
     * Thread-safe operation
     * @param container Container to load
     * @return true if container was loaded, false if truck is full
     */
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
            
            // Update status if truck becomes full
            if (containers.size() >= MAX_CONTAINERS) {
                status = "FULL";
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Check if truck is full
     * @return true if truck has reached maximum capacity
     */
    public synchronized boolean isFull() {
        return containers.size() >= MAX_CONTAINERS;
    }
    
    /**
     * Get truck ID
     * @return Unique truck identifier
     */
    public int getId() { 
        return id; 
    }
    
    /**
     * Get number of containers loaded
     * @return Current container count
     */
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
    
    /**
     * Set truck destination
     * @param destination New destination name
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    /**
     * Get current truck status
     * @return Status string (LOADING, FULL, DEPARTED, etc.)
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Set truck status
     * @param status New status
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Get defensive copy of containers list
     * @return Copy of containers in truck
     */
    public synchronized List<Container> getContainers() {
        return new ArrayList<>(containers);
    }
    
    /**
     * Get maximum truck capacity
     * @return Maximum number of containers
     */
    public int getMaxCapacity() {
        return MAX_CONTAINERS;
    }
    
    /**
     * Get truck utilization percentage
     * @return Percentage of truck filled (0-100)
     */
    public synchronized double getUtilization() {
        return (containers.size() * 100.0) / MAX_CONTAINERS;
    }
    
    /**
     * Get available space in truck
     * @return Number of additional containers that can be loaded
     */
    public synchronized int getAvailableSpace() {
        return MAX_CONTAINERS - containers.size();
    }
    
    /**
     * Check if truck is empty
     * @return true if no containers loaded
     */
    public synchronized boolean isEmpty() {
        return containers.isEmpty();
    }
    
    /**
     * Get total boxes in all containers
     * @return Total number of boxes being transported
     */
    public synchronized int getTotalBoxes() {
        int totalBoxes = 0;
        for (Container container : containers) {
            totalBoxes += container.getBoxCount();
        }
        return totalBoxes;
    }
    
    /**
     * Get estimated total weight
     * @return Estimated weight of all containers
     */
    public synchronized double getEstimatedWeight() {
        double totalWeight = 0.0;
        for (Container container : containers) {
            totalWeight += container.getEstimatedWeight();
        }
        return totalWeight;
    }
    
    /**
     * Get list of all tracking numbers in truck
     * @return List of tracking numbers from all containers
     */
    public synchronized List<String> getAllTrackingNumbers() {
        List<String> allTrackingNumbers = new ArrayList<>();
        for (Container container : containers) {
            allTrackingNumbers.addAll(container.getTrackingNumbers());
        }
        return allTrackingNumbers;
    }
    
    /**
     * Simulate truck departure
     * @return Departure summary string
     */
    public synchronized String depart() {
        status = "DEPARTED";
        long departureTime = System.currentTimeMillis();
        long loadingDuration = departureTime - creationTime;
        
        return String.format("Truck-%d departed to %s with %d containers (%d boxes) after %.2f seconds", 
            id, destination, containers.size(), getTotalBoxes(), loadingDuration / 1000.0);
    }
    
    /**
     * Validate all containers in truck
     * @return true if all containers are properly loaded and validated
     */
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