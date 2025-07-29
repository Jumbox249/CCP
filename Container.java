import java.util.*;

/**
 * Container class for holding multiple boxes (packed orders)
 * Thread-safe implementation with synchronized methods
 * Maximum capacity of 30 boxes per container (as per requirements)
 */
public class Container {
    private final int id;
    private final List<Order> boxes; // These are packed orders (boxes)
    private final int capacity = 30; // 30 boxes per container
    
    public Container(int id) {
        this.id = id;
        this.boxes = new ArrayList<>();
    }
    
    public synchronized boolean addBox(Order box) {
        if (boxes.size() < capacity) {
            boxes.add(box);
            return true;
        }
        return false;
    }
    
    public synchronized boolean isFull() {
        return boxes.size() >= capacity;
    }
    
    public int getId() { return id; }
    public List<Order> getBoxes() { return new ArrayList<>(boxes); }
    public int getBoxCount() { return boxes.size(); }
    
    // Legacy methods for compatibility
    @Deprecated
    public List<Order> getOrders() { return getBoxes(); }
    @Deprecated
    public int getSize() { return getBoxCount(); }
    @Deprecated
    public boolean addOrder(Order order) { return addBox(order); }
}