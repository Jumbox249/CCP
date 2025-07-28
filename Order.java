import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Order class representing an e-commerce order
 * Contains order details, status, and verification flags
 */
public class Order {
    private final int id;
    private final long creationTime;
    private String status;
    private boolean paymentVerified;
    private boolean inventoryAvailable;
    private boolean addressValid;
    private List<String> items;
    private String trackingNumber;
    private boolean packed;
    private boolean labelled;
    
    public Order(int id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.status = "NEW";
        this.items = generateRandomItems();
    }
    
    private List<String> generateRandomItems() {
        List<String> allItems = Arrays.asList("Laptop", "Phone", "Tablet", "Headphones", 
            "Keyboard", "Mouse", "Monitor", "Cable", "Charger", "Case");
        int itemCount = ThreadLocalRandom.current().nextInt(1, 5);
        List<String> orderItems = new ArrayList<>();
        
        for (int i = 0; i < itemCount; i++) {
            orderItems.add(allItems.get(ThreadLocalRandom.current().nextInt(allItems.size())));
        }
        return orderItems;
    }
    
    // Getters and setters
    public int getId() { return id; }
    public long getCreationTime() { return creationTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPaymentVerified() { return paymentVerified; }
    public void setPaymentVerified(boolean verified) { this.paymentVerified = verified; }
    public boolean isInventoryAvailable() { return inventoryAvailable; }
    public void setInventoryAvailable(boolean available) { this.inventoryAvailable = available; }
    public boolean isAddressValid() { return addressValid; }
    public void setAddressValid(boolean valid) { this.addressValid = valid; }
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public boolean isPacked() { return packed; }
    public void setPacked(boolean packed) { this.packed = packed; }
    public boolean isLabelled() { return labelled; }
    public void setLabelled(boolean labelled) { this.labelled = labelled; }
}