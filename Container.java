import java.util.*;

/**
 * Container class for holding multiple boxes (packed orders)
 * Thread-safe implementation with synchronized methods
 * Maximum capacity of 30 boxes per container as per requirements
 */
public class Container {
    private final int id;
    private final List<Order> boxes; // These are packed orders (boxes)
    private final long creationTime;
    private static final int CAPACITY = 30; // 30 boxes per container as per requirements
    
    /**
     * Constructor
     * @param id Unique container identifier
     */
    public Container(int id) {
        this.id = id;
        this.boxes = new ArrayList<>();
        this.creationTime = System.currentTimeMillis();
    }
    
    /**
     * Add a box (packed order) to the container
     * Thread-safe operation
     * @param box Packed order to add
     * @return true if box was added, false if container is full
     */
    public synchronized boolean addBox(Order box) {
        if (boxes.size() < CAPACITY) {
            boxes.add(box);
            return true;
        }
        return false;
    }
    
    /**
     * Check if container is full
     * @return true if container has reached maximum capacity
     */
    public synchronized boolean isFull() {
        return boxes.size() >= CAPACITY;
    }
    
    /**
     * Get container ID
     * @return Unique container identifier
     */
    public int getId() { 
        return id; 
    }
    
    /**
     * Get defensive copy of boxes list
     * @return Copy of boxes in container
     */
    public synchronized List<Order> getBoxes() { 
        return new ArrayList<>(boxes); 
    }
    
    /**
     * Get number of boxes in container
     * @return Current box count
     */
    public synchronized int getBoxCount() { 
        return boxes.size(); 
    }
    
    /**
     * Get container creation time
     * @return Timestamp when container was created
     */
    public long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Get container capacity
     * @return Maximum number of boxes this container can hold
     */
    public int getCapacity() {
        return CAPACITY;
    }
    
    /**
     * Get container utilization percentage
     * @return Percentage of container filled (0-100)
     */
    public synchronized double getUtilization() {
        return (boxes.size() * 100.0) / CAPACITY;
    }
    
    /**
     * Check if container is empty
     * @return true if no boxes in container
     */
    public synchronized boolean isEmpty() {
        return boxes.isEmpty();
    }
    
    /**
     * Get available space in container
     * @return Number of additional boxes that can be added
     */
    public synchronized int getAvailableSpace() {
        return CAPACITY - boxes.size();
    }
    
    /**
     * Get total weight simulation (for transport planning)
     * @return Simulated weight based on number of boxes
     */
    public synchronized double getEstimatedWeight() {
        // Simulate weight: assume average 2.5kg per box
        return boxes.size() * 2.5;
    }
    
    /**
     * Get list of tracking numbers in this container
     * @return List of tracking numbers for all boxes
     */
    public synchronized List<String> getTrackingNumbers() {
        List<String> trackingNumbers = new ArrayList<>();
        for (Order box : boxes) {
            if (box.getTrackingNumber() != null) {
                trackingNumbers.add(box.getTrackingNumber());
            }
        }
        return trackingNumbers;
    }
    
    /**
     * Validate all boxes in container have required information
     * @return true if all boxes are properly processed
     */
    public synchronized boolean validateContents() {
        for (Order box : boxes) {
            if (!box.isPacked() || !box.isLabelled() || box.getTrackingNumber() == null) {
                return false;
            }
        }
        return true;
    }
    
    // Legacy methods for compatibility with existing code
    @Deprecated
    public synchronized List<Order> getOrders() { 
        return getBoxes(); 
    }
    
    @Deprecated
    public synchronized int getSize() { 
        return getBoxCount(); 
    }
    
    @Deprecated
    public synchronized boolean addOrder(Order order) { 
        return addBox(order); 
    }
    
    @Override
    public String toString() {
        return String.format("Container{id=%d, boxes=%d/%d, utilization=%.1f%%}", 
            id, getBoxCount(), CAPACITY, getUtilization());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Container container = (Container) obj;
        return id == container.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}