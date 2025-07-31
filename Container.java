import java.util.*;

public class Container {
    private final int id;
    private final List<Order> boxes;
    private final long creationTime;
    private static final int CAPACITY = 30;
    
    public Container(int id) {
        this.id = id;
        this.boxes = new ArrayList<>();
        this.creationTime = System.currentTimeMillis();
    }
    
    public synchronized boolean addBox(Order box) {
        if (boxes.size() < CAPACITY) {
            boxes.add(box);
            return true;
        }
        return false;
    }
    
    public synchronized boolean isFull() {
        return boxes.size() >= CAPACITY;
    }
    
    public int getId() {
        return id;
    }
    
    public synchronized List<Order> getBoxes() {
        return new ArrayList<>(boxes);
    }
    
    public synchronized int getBoxCount() {
        return boxes.size();
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public int getCapacity() {
        return CAPACITY;
    }
    
    public synchronized double getUtilization() {
        return (boxes.size() * 100.0) / CAPACITY;
    }
    
    public synchronized boolean isEmpty() {
        return boxes.isEmpty();
    }
    
    public synchronized int getAvailableSpace() {
        return CAPACITY - boxes.size();
    }
    
    public synchronized double getEstimatedWeight() {
        return boxes.size() * 2.5;
    }
    
    public synchronized List<String> getTrackingNumbers() {
        List<String> trackingNumbers = new ArrayList<>();
        for (Order box : boxes) {
            if (box.getTrackingNumber() != null) {
                trackingNumbers.add(box.getTrackingNumber());
            }
        }
        return trackingNumbers;
    }
    
    public synchronized boolean validateContents() {
        for (Order box : boxes) {
            if (!box.isPacked() || !box.isLabelled() || box.getTrackingNumber() == null) {
                return false;
            }
        }
        return true;
    }
    
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