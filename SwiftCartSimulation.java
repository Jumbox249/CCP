import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class SwiftCartSimulation {
    private static final int TOTAL_ORDERS = 600;
    private static final int SIMULATION_DURATION_MS = 300000; // 5 minutes
    
    // Thread pools for different stations
    private final ExecutorService orderIntakeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pickingExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService packingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService labellingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sortingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService loadingExecutor = Executors.newFixedThreadPool(3);
    
    // Queues for inter-station communication
    private final BlockingQueue<Order> pickingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> packingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> labellingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> sortingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Container> loadingQueue = new LinkedBlockingQueue<>();
    
    // Enhanced Statistics tracking
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);
    private final AtomicInteger boxesPacked = new AtomicInteger(0);
    private final AtomicInteger containersCreated = new AtomicInteger(0);
    private final AtomicInteger trucksDispatched = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    // Truck timing statistics
    private final List<Long> truckWaitTimes = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> truckLoadTimes = Collections.synchronizedList(new ArrayList<>());
    
    // Synchronization objects
    private final Semaphore pickingStationCapacity = new Semaphore(4);
    private final Object packingLock = new Object();
    private final LoadingBay loadingBay = new LoadingBay();
    
    private volatile boolean simulationRunning = true;
    private final long startTime = System.currentTimeMillis();
    
    public static void main(String[] args) {
        configureLogging();
        SwiftCartSimulation simulation = new SwiftCartSimulation();
        simulation.start();
    }
    
    private static void configureLogging() {
        BusinessLogger.configureSimpleLogging();
    }
    
    public void start() {
        System.out.println("=== SwiftCart Simulation Starting ===");
        System.out.println("Processing " + TOTAL_ORDERS + " orders through automated e-commerce center");
        
        // Start all stations
        startOrderIntake();
        startPickingStation();
        startPackingStation();
        startLabellingStation();
        startSortingArea();
        startLoadingBay();
        
        // Schedule simulation end
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(this::stopSimulation, SIMULATION_DURATION_MS, TimeUnit.MILLISECONDS);
        
        // Monitor progress
        monitorProgress();
    }
    
    private void startOrderIntake() {
        orderIntakeExecutor.submit(() -> {
            Thread.currentThread().setName("OrderThread-1");
            OrderIntakeSystem intakeSystem = new OrderIntakeSystem(totalRejected);
            
            for (int i = 1; i <= TOTAL_ORDERS && simulationRunning; i++) {
                try {
                    Order order = intakeSystem.receiveOrder(i);
                    if (order != null) {
                        pickingQueue.offer(order);
                        BusinessLogger.logOrderReceived(i);
                    }
                    Thread.sleep(500); // New order every 500ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("Order intake completed");
        });
    }
    
    private void startPickingStation() {
        for (int i = 1; i <= 4; i++) {
            final int stationId = i;
            pickingExecutor.submit(() -> {
                Thread.currentThread().setName("Picker-" + stationId);
                PickingStation picker = new PickingStation(stationId, pickingStationCapacity);
                
                while (simulationRunning) {
                    try {
                        Order order = pickingQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (order != null) {
                            BusinessLogger.logOrderPicking(order, stationId);
                            Order pickedOrder = picker.pickOrder(order);
                            if (pickedOrder != null) {
                                packingQueue.offer(pickedOrder);
                            } else {
                                BusinessLogger.logOrderPickingRejected(order.getId(), "Missing items");
                                totalRejected.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }
    
    private void startPackingStation() {
        packingExecutor.submit(() -> {
            Thread.currentThread().setName("Packer-1");
            PackingStation packer = new PackingStation(packingLock);
            
            while (simulationRunning) {
                try {
                    Order order = packingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (order != null) {
                        Order packedOrder = packer.packOrder(order);
                        if (packedOrder != null) {
                            BusinessLogger.logOrderPacked(packedOrder);
                            boxesPacked.incrementAndGet();
                            labellingQueue.offer(packedOrder);
                        } else {
                            BusinessLogger.logOrderPackingRejected(order.getId(), "Packing error");
                            totalRejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void startLabellingStation() {
        labellingExecutor.submit(() -> {
            Thread.currentThread().setName("Labeller-2");
            LabellingStation labeller = new LabellingStation();
            
            while (simulationRunning) {
                try {
                    Order order = labellingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (order != null) {
                        Order labelledOrder = labeller.labelOrder(order);
                        if (labelledOrder != null) {
                            BusinessLogger.logOrderLabelled(labelledOrder, labelledOrder.getTrackingNumber());
                            sortingQueue.offer(labelledOrder);
                        } else {
                            BusinessLogger.logOrderLabellingRejected(order.getId(), "Label error");
                            totalRejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void startSortingArea() {
        sortingExecutor.submit(() -> {
            Thread.currentThread().setName("Sorter-1");
            SortingArea sorter = new SortingArea(loadingQueue, containersCreated);
            
            while (simulationRunning) {
                try {
                    Order order = sortingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (order != null) {
                        sorter.sortOrder(order);
                        totalProcessed.incrementAndGet();
                        totalProcessingTime.addAndGet(System.currentTimeMillis() - order.getCreationTime());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            sorter.flushRemaining(); // Handle any remaining orders
        });
    }
    
    private void startLoadingBay() {
        for (int i = 1; i <= 3; i++) {
            final int loaderId = i;
            loadingExecutor.submit(() -> {
                Thread.currentThread().setName("Loader-" + loaderId);
                AutonomousLoader loader = new AutonomousLoader(loaderId, loadingBay, trucksDispatched, truckWaitTimes, truckLoadTimes);
                
                while (simulationRunning) {
                    try {
                        Container container = loadingQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (container != null) {
                            loader.loadContainer(container);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }
    
    private void monitorProgress() {
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("=== Progress Update: " + elapsed / 1000 + " seconds elapsed ===");
            System.out.println("Orders processed: " + totalProcessed.get() + 
                ", Rejected: " + totalRejected.get() + 
                ", Trucks dispatched: " + trucksDispatched.get());
            System.out.println("Queue sizes - Picking: " + pickingQueue.size() + 
                ", Packing: " + packingQueue.size() + 
                ", Labelling: " + labellingQueue.size() + 
                ", Sorting: " + sortingQueue.size() + 
                ", Loading: " + loadingQueue.size());
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void stopSimulation() {
        System.out.println("=== Simulation Time Limit Reached - Shutting Down ===");
        simulationRunning = false;
        
        // Shutdown all executors
        shutdownExecutor(orderIntakeExecutor, "Order Intake");
        shutdownExecutor(pickingExecutor, "Picking Station");
        shutdownExecutor(packingExecutor, "Packing Station");
        shutdownExecutor(labellingExecutor, "Labelling Station");
        shutdownExecutor(sortingExecutor, "Sorting Area");
        shutdownExecutor(loadingExecutor, "Loading Bay");
        
        // Print final statistics
        printFinalStatistics();
        System.exit(0);
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println(name + " executor did not terminate gracefully");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    private void printFinalStatistics() {
        System.out.println("\n=== FINAL SIMULATION STATISTICS ===");
        System.out.println("Total orders processed: " + totalProcessed.get());
        System.out.println("Total orders rejected: " + totalRejected.get());
        System.out.println("Total boxes packed: " + boxesPacked.get());
        System.out.println("Total containers filled: " + containersCreated.get());
        System.out.println("Total trucks dispatched: " + trucksDispatched.get());
        
        if (totalProcessed.get() > 0) {
            double avgProcessingTime = totalProcessingTime.get() / (double) totalProcessed.get() / 1000.0;
            System.out.printf("Average order processing time: %.2f seconds%n", avgProcessingTime);
        }
        
        // Truck timing statistics
        if (!truckWaitTimes.isEmpty()) {
            long minWait = Collections.min(truckWaitTimes);
            long maxWait = Collections.max(truckWaitTimes);
            double avgWait = truckWaitTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            System.out.printf("Truck wait times - Min: %.2fs, Max: %.2fs, Avg: %.2fs%n", 
                minWait/1000.0, maxWait/1000.0, avgWait/1000.0);
        } else {
            System.out.println("No truck timing data available");
        }
        
        if (!truckLoadTimes.isEmpty()) {
            long minLoad = Collections.min(truckLoadTimes);
            long maxLoad = Collections.max(truckLoadTimes);
            double avgLoad = truckLoadTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            System.out.printf("Truck load times - Min: %.2fs, Max: %.2fs, Avg: %.2fs%n", 
                minLoad/1000.0, maxLoad/1000.0, avgLoad/1000.0);
        } else {
            System.out.println("No truck load timing data available");
        }
        
        System.out.println("Simulation duration: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds");
        System.out.println("=== SwiftCart Simulation Complete ===\n");
    }
    
    // BusinessLogger class for clean output formatting
    public static class BusinessLogger {
        
        public static void logOrderReceived(int orderId) {
            System.out.printf("OrderIntake: Order #%d received (Thread: %s)%n", orderId, Thread.currentThread().getName());
        }
        
        public static void logOrderRejected(int orderId, String reason) {
            System.out.printf("OrderIntake: Order #%d rejected - %s (Thread: %s)%n", orderId, reason, Thread.currentThread().getName());
        }
        
        public static void logOrderPicking(Order order, int stationId) {
            System.out.printf("PickingStation: Picking Order #%d (Thread: %s)%n", order.getId(), Thread.currentThread().getName());
        }
        
        public static void logOrderPickingRejected(int orderId, String reason) {
            System.out.printf("PickingStation: Order #%d rejected - %s (Thread: %s)%n", orderId, reason, Thread.currentThread().getName());
        }
        
        public static void logOrderPackingRejected(int orderId, String reason) {
            System.out.printf("PackingStation: Order #%d rejected - %s (Thread: %s)%n", orderId, reason, Thread.currentThread().getName());
        }
        
        public static void logOrderLabellingRejected(int orderId, String reason) {
            System.out.printf("LabellingStation: Order #%d rejected - %s (Thread: %s)%n", orderId, reason, Thread.currentThread().getName());
        }
        
        public static void logOrderPacked(Order order) {
            System.out.printf("PackingStation: Packed Order #%d (Thread: %s)%n", order.getId(), Thread.currentThread().getName());
        }
        
        public static void logOrderLabelled(Order order, String trackingId) {
            System.out.printf("LabellingStation: Labelled Order #%d with Tracking ID #%s (Thread: %s)%n", 
                order.getId(), trackingId, Thread.currentThread().getName());
        }
        
        public static void logOrderSorted(Order order, int batchNumber) {
            System.out.printf("Sorter: Added Order #%d to Batch #%d (Thread: %s)%n", 
                order.getId(), batchNumber, Thread.currentThread().getName());
        }
        
        public static void logContainerLoading(int loaderId, int containerId, int bayId) {
            System.out.printf("Loader-%d: Moving Container #%d to Loading Bay-%d%n", loaderId, containerId, bayId);
        }
        
        public static void logTruckDeparture(int truckId) {
            System.out.printf("Truck-%d: Fully loaded with 18 containers. Departing to Distribution Centre.%n", truckId);
        }
        
        public static void logTruckWaiting(int truckId) {
            System.out.printf("Truck-%d: Waiting for loading bay to be free.%n", truckId);
        }
        
        public static void logLoaderBreakdown(int loaderId) {
            System.out.printf("Loader-%d: BREAKDOWN - Maintenance required%n", loaderId);
        }
        
        public static void logLoaderRepaired(int loaderId) {
            System.out.printf("Loader-%d: Repaired and operational%n", loaderId);
        }
        
        public static void logDispatchPaused(int containersAtBay) {
            System.out.printf("Supervisor: Dispatch paused. %d containers at bay – waiting for truck%n", containersAtBay);
        }
        
        public static void configureSimpleLogging() {
            // Disable all existing logging to use direct System.out.println calls
            Logger.getLogger("").setLevel(Level.OFF);
        }
    }
}