import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Order class representing an e-commerce order
 * Contains order details, status tracking, and verification flags
 * Thread-safe implementation with immutable ID and creation time
 */
public class Order {
    private final int id;
    private final long creationTime;
    private volatile String status;
    private volatile boolean paymentVerified;
    private volatile boolean inventoryAvailable;
    private volatile boolean addressValid;
    private final List<String> items;
    private volatile String trackingNumber;
    private volatile boolean packed;
    private volatile boolean labelled;
    
    /**
     * Constructor creates new order with random items
     * @param id Unique order identifier
     */
    public Order(int id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.status = "NEW";
        this.items = generateRandomItems();
        this.paymentVerified = false;
        this.inventoryAvailable = false;
        this.addressValid = false;
        this.packed = false;
        this.labelled = false;
    }
    
    /**
     * Generate random items for the order (1-4 items)
     * @return List of item names
     */
    private List<String> generateRandomItems() {
        List<String> allItems = Arrays.asList(
            "Laptop", "Phone", "Tablet", "Headphones", 
            "Keyboard", "Mouse", "Monitor", "Cable", 
            "Charger", "Case", "Speaker", "Camera"
        );
        
        int itemCount = ThreadLocalRandom.current().nextInt(1, 5);
        List<String> orderItems = new ArrayList<>();
        
        for (int i = 0; i < itemCount; i++) {
            String item = allItems.get(ThreadLocalRandom.current().nextInt(allItems.size()));
            orderItems.add(item);
        }
        return orderItems;
    }
    
    // Getters - Thread-safe for immutable fields
    public int getId() { 
        return id; 
    }
    
    public long getCreationTime() { 
        return creationTime; 
    }
    
    public String getStatus() { 
        return status; 
    }
    
    public boolean isPaymentVerified() { 
        return paymentVerified; 
    }
    
    public boolean isInventoryAvailable() { 
        return inventoryAvailable; 
    }
    
    public boolean isAddressValid() { 
        return addressValid; 
    }
    
    public List<String> getItems() { 
        return new ArrayList<>(items); // Return defensive copy
    }
    
    public String getTrackingNumber() { 
        return trackingNumber; 
    }
    
    public boolean isPacked() { 
        return packed; 
    }
    
    public boolean isLabelled() { 
        return labelled; 
    }
    
    // Setters - Thread-safe with volatile fields
    public void setStatus(String status) { 
        this.status = status; 
    }
    
    public void setPaymentVerified(boolean verified) { 
        this.paymentVerified = verified; 
    }
    
    public void setInventoryAvailable(boolean available) { 
        this.inventoryAvailable = available; 
    }
    
    public void setAddressValid(boolean valid) { 
        this.addressValid = valid; 
    }
    
    public void setItems(List<String> items) { 
        this.items.clear();
        this.items.addAll(items);
    }
    
    public void setTrackingNumber(String trackingNumber) { 
        this.trackingNumber = trackingNumber; 
    }
    
    public void setPacked(boolean packed) { 
        this.packed = packed; 
    }
    
    public void setLabelled(boolean labelled) { 
        this.labelled = labelled; 
    }
    
    /**
     * Check if order is ready for next processing stage
     * @return true if all verifications passed
     */
    public boolean isVerified() {
        return paymentVerified && inventoryAvailable && addressValid;
    }
    
    /**
     * Check if order is completely processed
     * @return true if packed and labelled
     */
    public boolean isCompleted() {
        return packed && labelled && trackingNumber != null;
    }
    
    @Override
    public String toString() {
        return String.format("Order{id=%d, status='%s', items=%d, tracking='%s'}", 
            id, status, items.size(), trackingNumber);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Order order = (Order) obj;
        return id == order.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}