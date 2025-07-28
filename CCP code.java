// OrderIntakeSystem.java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * Order Intake System - Receives and verifies orders
 * Processes one order every 500ms with payment, inventory, and address verification
 */
class OrderIntakeSystem {
    private static final Logger logger = Logger.getLogger(OrderIntakeSystem.class.getName());
    private final BlockingQueue<Order> orderQueue;
    private final AtomicInteger rejectedOrders;
    private final Random random = ThreadLocalRandom.current();
    
    public OrderIntakeSystem(BlockingQueue<Order> orderQueue, AtomicInteger rejectedOrders) {
        this.orderQueue = orderQueue;
        this.rejectedOrders = rejectedOrders;
    }
    
    public Order receiveOrder(int orderId) {
        Order order = new Order(orderId);
        logger.info(String.format("Thread [%s]: Receiving order #%d", 
            Thread.currentThread().getName(), orderId));
        
        // Verify payment (95% success rate)
        boolean paymentVerified = random.nextDouble() < 0.95;
        order.setPaymentVerified(paymentVerified);
        
        if (!paymentVerified) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Payment verification failed",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_PAYMENT");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify inventory (90% success rate)
        boolean inventoryAvailable = random.nextDouble() < 0.90;
        order.setInventoryAvailable(inventoryAvailable);
        
        if (!inventoryAvailable) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Inventory unavailable",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_INVENTORY");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        // Verify address (98% success rate)
        boolean addressValid = random.nextDouble() < 0.98;
        order.setAddressValid(addressValid);
        
        if (!addressValid) {
            logger.warning(String.format("Thread [%s]: Order #%d rejected - Invalid address",
                Thread.currentThread().getName(), orderId));
            order.setStatus("REJECTED_ADDRESS");
            rejectedOrders.incrementAndGet();
            return null;
        }
        
        order.setStatus("VERIFIED");
        logger.info(String.format("Thread [%s]: Order #%d verified successfully",
            Thread.currentThread().getName(), orderId));
        return order;
    }
}

// PickingStation.java
/**
 * Picking Station - Handles concurrent order picking with robotic systems
 * Can process up to 4 orders concurrently
 */
class PickingStation {
    private static final Logger logger = Logger.getLogger(PickingStation.class.getName());
    private final int stationId;
    private final Semaphore capacity;
    private final Random random = ThreadLocalRandom.current();
    
    public PickingStation(int stationId, Semaphore capacity) {
        this.stationId = stationId;
        this.capacity = capacity;
    }
    
    public Order pickOrder(Order order) throws InterruptedException {
        capacity.acquire(); // Acquire permit for concurrent processing
        
        try {
            logger.info(String.format("Thread [%s]: Starting to pick order #%d at station %d",
                Thread.currentThread().getName(), order.getId(), stationId));
            
            // Simulate robotic picking time (1-3 seconds)
            Thread.sleep(1000 + random.nextInt(2000));
            
            // Check for missing items (5% chance)
            if (random.nextDouble() < 0.05) {
                logger.warning(String.format("Thread [%s]: Order #%d has missing items - rejected",
                    Thread.currentThread().getName(), order.getId()));
                order.setStatus("REJECTED_MISSING_ITEMS");
                return null;
            }
            
            // Simulate item verification
            for (String item : order.getItems()) {
                logger.info(String.format("Thread [%s]: Picked item '%s' for order #%d",
                    Thread.currentThread().getName(), item, order.getId()));
                Thread.sleep(200); // 200ms per item
            }
            
            order.setStatus("PICKED");
            logger.info(String.format("Thread [%s]: Order #%d picking completed at station %d",
                Thread.currentThread().getName(), order.getId(), stationId));
            return order;
            
        } finally {
            capacity.release(); // Always release permit
        }
    }
}

// PackingStation.java
/**
 * Packing Station - Processes one order at a time with scanner verification
 * Uses synchronized block to ensure single-order processing
 */
class PackingStation {
    private static final Logger logger = Logger.getLogger(PackingStation.class.getName());
    private final Object packingLock;
    private final Random random = ThreadLocalRandom.current();
    
    public PackingStation(Object packingLock) {
        this.packingLock = packingLock;
    }
    
    public Order packOrder(Order order) throws InterruptedException {
        synchronized (packingLock) {
            logger.info(String.format("Thread [%s]: Starting to pack order #%d",
                Thread.currentThread().getName(), order.getId()));
            
            // Simulate packing process (2-4 seconds)
            Thread.sleep(2000 + random.nextInt(2000));
            
            // Scanner verification (2% chance of incorrect packing)
            if (random.nextDouble() < 0.02) {
                logger.warning(String.format("Thread [%s]: Order #%d failed scanner verification - incorrect packing",
                    Thread.currentThread().getName(), order.getId()));
                order.setStatus("REJECTED_PACKING_ERROR");
                return null;
            }
            
            order.setPacked(true);
            order.setStatus("PACKED");
            logger.info(String.format("Thread [%s]: Order #%d packed successfully",
                Thread.currentThread().getName(), order.getId()));
            return order;
        }
    }
}

// LabellingStation.java
/**
 * Labelling Station - Assigns tracking numbers and labels to packages
 * Includes scanner verification for label accuracy
 */
class LabellingStation {
    private static final Logger logger = Logger.getLogger(LabellingStation.class.getName());
    private final AtomicInteger trackingCounter = new AtomicInteger(100000);
    private final Random random = ThreadLocalRandom.current();
    
    public Order labelOrder(Order order) throws InterruptedException {
        logger.info(String.format("Thread [%s]: Starting to label order #%d",
            Thread.currentThread().getName(), order.getId()));
        
        // Generate tracking number
        String trackingNumber = "SC" + trackingCounter.getAndIncrement();
        order.setTrackingNumber(trackingNumber);
        
        // Simulate labelling process (1-2 seconds)
        Thread.sleep(1000 + random.nextInt(1000));
        
        // Scanner verification (1% chance of label error)
        if (random.nextDouble() < 0.01) {
            logger.warning(String.format("Thread [%s]: Order #%d failed label scanner - incorrect label",
                Thread.currentThread().getName(), order.getId()));
            order.setStatus("REJECTED_LABEL_ERROR");
            return null;
        }
        
        order.setLabelled(true);
        order.setStatus("LABELLED");
        logger.info(String.format("Thread [%s]: Order #%d labelled with tracking #%s",
            Thread.currentThread().getName(), order.getId(), trackingNumber));
        return order;
    }
}

// SortingArea.java
/**
 * Sorting Area - Groups orders into batches and creates containers
 * Batches of 6 orders, containers hold 30 boxes max
 */
class SortingArea {
    private static final Logger logger = Logger.getLogger(SortingArea.class.getName());
    private final BlockingQueue<Container> loadingQueue;
    private final List<Order> currentBatch = new ArrayList<>();
    private Container currentContainer;
    private final AtomicInteger containerCounter = new AtomicInteger(1);
    private final Object sortingLock = new Object();
    
    public SortingArea(BlockingQueue<Container> loadingQueue) {
        this.loadingQueue = loadingQueue;
        this.currentContainer = new Container(containerCounter.getAndIncrement());
    }
    
    public void sortOrder(Order order) throws InterruptedException {
        synchronized (sortingLock) {
            logger.info(String.format("Thread [%s]: Sorting order #%d",
                Thread.currentThread().getName(), order.getId()));
            
            currentBatch.add(order);
            
            // Process batch when it reaches 6 orders
            if (currentBatch.size() >= 6) {
                processBatch();
            }
        }
    }
    
    private void processBatch() throws InterruptedException {
        logger.info(String.format("Thread [%s]: Processing batch of %d orders",
            Thread.currentThread().getName(), currentBatch.size()));
        
        for (Order order : currentBatch) {
            if (!currentContainer.addOrder(order)) {
                // Container is full, send to loading and create new one
                logger.info(String.format("Thread [%s]: Container #%d full with %d boxes, sending to loading",
                    Thread.currentThread().getName(), currentContainer.getId(), currentContainer.getSize()));
                loadingQueue.put(currentContainer);
                currentContainer = new Container(containerCounter.getAndIncrement());
                currentContainer.addOrder(order);
            }
            order.setStatus("SORTED");
        }
        
        currentBatch.clear();
    }
    
    public void flushRemaining() {
        synchronized (sortingLock) {
            try {
                if (!currentBatch.isEmpty()) {
                    processBatch();
                }
                if (currentContainer.getSize() > 0) {
                    logger.info(String.format("Thread [%s]: Flushing final container #%d with %d boxes",
                        Thread.currentThread().getName(), currentContainer.getId(), currentContainer.getSize()));
                    loadingQueue.offer(currentContainer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

// LoadingBay.java
/**
 * Loading Bay - Manages 2 bays with truck capacity constraints
 * Each truck can hold 18 containers maximum
 */
class LoadingBay {
    private static final Logger logger = Logger.getLogger(LoadingBay.class.getName());
    private final Semaphore bayAvailability = new Semaphore(2); // 2 bays
    private final AtomicInteger truckCounter = new AtomicInteger(1);
    private final Map<Integer, Truck> currentTrucks = new ConcurrentHashMap<>();
    private final ReentrantLock bayLock = new ReentrantLock();
    
    public Truck getTruckForLoading() throws InterruptedException {
        bayLock.lock();
        try {
            // Find truck with space
            for (Truck truck : currentTrucks.values()) {
                if (!truck.isFull()) {
                    return truck;
                }
            }
            
            // No truck with space, try to get a bay for new truck
            if (bayAvailability.tryAcquire()) {
                Truck newTruck = new Truck(truckCounter.getAndIncrement());
                currentTrucks.put(newTruck.getId(), newTruck);
                logger.info(String.format("Thread [%s]: New truck #%d arrived at bay",
                    Thread.currentThread().getName(), newTruck.getId()));
                return newTruck;
            }
            
            // Wait for bay to become available
            logger.info(String.format("Thread [%s]: Waiting for available bay",
                Thread.currentThread().getName()));
            bayAvailability.acquire();
            Truck newTruck = new Truck(truckCounter.getAndIncrement());
            currentTrucks.put(newTruck.getId(), newTruck);
            return newTruck;
            
        } finally {
            bayLock.unlock();
        }
    }
    
    public void truckDeparted(Truck truck) {
        bayLock.lock();
        try {
            currentTrucks.remove(truck.getId());
            bayAvailability.release();
            logger.info(String.format("Thread [%s]: Truck #%d departed with %d containers",
                Thread.currentThread().getName(), truck.getId(), truck.getContainerCount()));
        } finally {
            bayLock.unlock();
        }
    }
}

// Truck.java
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

// AutonomousLoader.java
/**
 * Autonomous Loader - Loads containers onto trucks
 * Simulates random breakdowns and repairs
 */
class AutonomousLoader {
    private static final Logger logger = Logger.getLogger(AutonomousLoader.class.getName());
    private final int loaderId;
    private final LoadingBay loadingBay;
    private final AtomicInteger trucksLoaded;
    private final Random random = ThreadLocalRandom.current();
    private volatile boolean operational = true;
    
    public AutonomousLoader(int loaderId, LoadingBay loadingBay, AtomicInteger trucksLoaded) {
        this.loaderId = loaderId;
        this.loadingBay = loadingBay;
        this.trucksLoaded = trucksLoaded;
    }
    
    public void loadContainer(Container container) throws InterruptedException {
        // Check for random breakdown (5% chance)
        if (operational && random.nextDouble() < 0.05) {
            simulateBreakdown();
        }
        
        if (!operational) {
            logger.warning(String.format("Thread [%s]: Loader %d is broken down, waiting for repair",
                Thread.currentThread().getName(), loaderId));
            Thread.sleep(5000); // Wait for repair
            operational = true;
            logger.info(String.format("Thread [%s]: Loader %d repaired and operational",
                Thread.currentThread().getName(), loaderId));
        }
        
        Truck truck = loadingBay.getTruckForLoading();
        
        logger.info(String.format("Thread [%s]: Loader %d loading container #%d onto truck #%d",
            Thread.currentThread().getName(), loaderId, container.getId(), truck.getId()));
        
        // Simulate loading time (3-5 seconds)
        Thread.sleep(3000 + random.nextInt(2000));
        
        if (truck.loadContainer(container)) {
            logger.info(String.format("Thread [%s]: Container #%d loaded successfully onto truck #%d",
                Thread.currentThread().getName(), container.getId(), truck.getId()));
            
            if (truck.isFull()) {
                logger.info(String.format("Thread [%s]: Truck #%d is full, dispatching",
                    Thread.currentThread().getName(), truck.getId()));
                loadingBay.truckDeparted(truck);
                trucksLoaded.incrementAndGet();
            }
        } else {
            logger.warning(String.format("Thread [%s]: Failed to load container #%d - truck full",
                Thread.currentThread().getName(), container.getId()));
        }
    }
    
    private void simulateBreakdown() {
        operational = false;
        logger.warning(String.format("Thread [%s]: BREAKDOWN - Loader %d has malfunctioned!",
            Thread.currentThread().getName(), loaderId));
    }
}