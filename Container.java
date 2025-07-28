import java.util.*;

/**
 * Container class for holding multiple orders
 * Thread-safe implementation with synchronized methods
 */
public class Container {
    private final int id;
    private final List<Order> orders;
    private final int capacity = 30;
    
    public Container(int id) {
        this.id = id;
        this.orders = new ArrayList<>();
    }
    
    public synchronized boolean addOrder(Order order) {
        if (orders.size() < capacity) {
            orders.add(order);
            return true;
        }
        return false;
    }
    
    public synchronized boolean isFull() {
        return orders.size() >= capacity;
    }
    
    public int getId() { return id; }
    public List<Order> getOrders() { return new ArrayList<>(orders); }
    public int getSize() { return orders.size(); }
}