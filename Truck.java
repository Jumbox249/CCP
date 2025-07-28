class Truck {
    private final int id;
    private final List<Container> containers = new ArrayList<>();
    private static final int MAX_CONTAINERS = 18;
    
    public Truck(int id) {
        this.id = id;
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
}