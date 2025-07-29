import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class SwiftCartSimulation {
    private static final Logger logger = Logger.getLogger(SwiftCartSimulation.class.getName());
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
    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> pickingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> packingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> labellingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> sortingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Container> loadingQueue = new LinkedBlockingQueue<>();
    
    // Statistics tracking
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger trucksLoaded = new AtomicInteger(0);
    
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
    
    @SuppressWarnings("LoggerStringConcat")
    public void start() {
        logger.info("=== SwiftCart Simulation Starting ===");
        logger.info("Processing " + TOTAL_ORDERS + " orders through automated e-commerce center");
        
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
    
    @SuppressWarnings("LoggerStringConcat")
    private void startOrderIntake() {
        orderIntakeExecutor.submit(() -> {
            Thread.currentThread().setName("OrderIntake-1");
            OrderIntakeSystem intakeSystem = new OrderIntakeSystem(orderQueue, totalRejected);
            
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
            logger.info("Thread [" + Thread.currentThread().getName() + "]: Order intake completed");
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
                            Order pickedOrder = picker.pickOrder(order);
                            if (pickedOrder != null) {
                                packingQueue.offer(pickedOrder);
                            } else {
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
                            labellingQueue.offer(packedOrder);
                        } else {
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
            Thread.currentThread().setName("Labeller-1");
            LabellingStation labeller = new LabellingStation();
            
            while (simulationRunning) {
                try {
                    Order order = labellingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (order != null) {
                        Order labelledOrder = labeller.labelOrder(order);
                        if (labelledOrder != null) {
                            sortingQueue.offer(labelledOrder);
                        } else {
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
            SortingArea sorter = new SortingArea(loadingQueue);
            
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
                AutonomousLoader loader = new AutonomousLoader(loaderId, loadingBay, trucksLoaded);
                
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
            logger.info(String.format("=== Progress Update: %d seconds elapsed ===", elapsed / 1000));
            logger.info(String.format("Orders processed: %d, Rejected: %d, Trucks loaded: %d",
                totalProcessed.get(), totalRejected.get(), trucksLoaded.get()));
            logger.info(String.format("Queue sizes - Picking: %d, Packing: %d, Labelling: %d, Sorting: %d, Loading: %d",
                pickingQueue.size(), packingQueue.size(), labellingQueue.size(), 
                sortingQueue.size(), loadingQueue.size()));
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void stopSimulation() {
        logger.info("=== Simulation Time Limit Reached - Shutting Down ===");
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
    
    @SuppressWarnings("LoggerStringConcat")
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning(name + " executor did not terminate gracefully");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    @SuppressWarnings("LoggerStringConcat")
    private void printFinalStatistics() {
        logger.info("=== FINAL SIMULATION STATISTICS ===");
        logger.info("Total orders processed: " + totalProcessed.get());
        logger.info("Total orders rejected: " + totalRejected.get());
        logger.info("Total trucks loaded: " + trucksLoaded.get());
        
        if (totalProcessed.get() > 0) {
            double avgProcessingTime = totalProcessingTime.get() / (double) totalProcessed.get() / 1000.0;
            logger.info(String.format("Average order processing time: %.2f seconds", avgProcessingTime));
        }
        
        logger.info("Simulation duration: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds");
        logger.info("=== SwiftCart Simulation Complete ===");
    }
    
    // BusinessLogger class for clean output formatting
    public static class BusinessLogger {
        
        public static void logOrderReceived(int orderId) {
            System.out.printf("OrderIntake: Order #%d received (Thread: OrderThread-1)%n", orderId);
        }
        
        public static void logOrderRejected(int orderId, String reason) {
            System.out.printf("OrderIntake: Order #%d rejected - %s (Thread: OrderThread-1)%n", orderId, reason);
        }
        
        public static void logOrderPicking(Order order, int stationId) {
            System.out.printf("PickingStation: Picking Order #%d (Thread: Picker-%d)%n", order.getId(), stationId);
        }
        
        public static void logOrderPacked(Order order) {
            System.out.printf("PackingStation: Packed Order #%d (Thread: Packer-1)%n", order.getId());
        }
        
        public static void logOrderLabelled(Order order, String trackingId) {
            System.out.printf("LabellingStation: Labelled Order #%d with Tracking ID #%s (Thread: Labeller-2)%n", 
                order.getId(), trackingId);
        }
        
        public static void logOrderSorted(Order order) {
            System.out.printf("Sorter: Added Order #%d to current batch (Thread: Sorter-1)%n", order.getId());
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
        
        public static void configureSimpleLogging() {
            // Disable all existing logging to use direct System.out.println calls
            Logger.getLogger("").setLevel(Level.OFF);
        }
    }
}