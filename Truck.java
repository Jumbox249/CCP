import java.util.*;

/**
 * Truck class for transporting containers
 * Each truck can hold up to 18 containers
 */
public class Truck {
    private final int id;
    private final List<Container> containers = new ArrayList<>();
    private static final int MAX_CONTAINERS = 18;
    private final long creationTime;
    
    public Truck(int id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
    }
    
    public synchronized boolean loadContainer(Container container) {
        if (containers.size() < MAX_CONTAINERS) {
            containers.add(container);
            return true;
        }
        return false;
    }
    
    public synchronized boolean isFull() {
        return containers.size() >= MAX_CONTAINERS;
    }
    
    public int getId() { return id; }
    public int getContainerCount() { return containers.size(); }
    public long getCreationTime() { return creationTime; }
}