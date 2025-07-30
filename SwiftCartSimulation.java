import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * SwiftCart E-commerce Centre Simulation
 * Simulates a highly automated e-commerce processing center with concurrent operations
 * 
 * Key Features:
 * - Order Intake System (1 order every 500ms)
 * - Picking Station (4 concurrent robotic arms)
 * - Packing Station (1 order at a time)
 * - Labelling Station (tracking assignment)
 * - Sorting Area (batches of 6 boxes, containers of 30 boxes)
 * - Loading Bay (3 autonomous loaders, 2 bays, trucks with 18 containers)
 * 
 * Concurrency Features:
 * - Thread pools for different stations
 * - Blocking queues for inter-station communication
 * - Semaphores for capacity control
 * - Atomic counters for statistics
 * - Synchronized blocks for critical sections
 */
public class SwiftCartSimulation {
    // Constants as per assignment requirements
    private static final int TOTAL_ORDERS = 600;
    private static final int SIMULATION_DURATION_MS = 300000; // 5 minutes
    private static final int ORDER_ARRIVAL_RATE_MS = 500; // Orders arrive every 500ms
    
    // Thread pools for different stations
    private final ExecutorService orderIntakeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pickingExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService packingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService labellingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sortingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService loadingExecutor = Executors.newFixedThreadPool(3);
    
    // Blocking queues for inter-station communication
    private final BlockingQueue<Order> pickingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> packingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> labellingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> sortingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Container> loadingQueue = new LinkedBlockingQueue<>();
    
    // Statistics tracking with atomic counters for thread safety
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);
    private final AtomicInteger boxesPacked = new AtomicInteger(0);
    private final AtomicInteger containersCreated = new AtomicInteger(0);
    private final AtomicInteger trucksDispatched = new AtomicInteger(0);
    
    // Reject Handler for defective orders
    private final RejectHandler rejectHandler = new RejectHandler(totalRejected);
    
    // Truck timing statistics with synchronized collections
    private final List<Long> truckWaitTimes = Collections.synchronizedList(new ArrayList<Long>());
    private final List<Long> truckLoadTimes = Collections.synchronizedList(new ArrayList<Long>());
    
    // Synchronization objects
    private final Semaphore pickingStationCapacity = new Semaphore(4, true); // Fair semaphore
    private final Object packingLock = new Object();
    private final LoadingBay loadingBay = new LoadingBay();
    
    // Simulation control
    private volatile boolean simulationRunning = true;
    private final long startTime = System.currentTimeMillis();
    
    /**
     * Main method to start the simulation
     */
    public static void main(String[] args) {
        configureLogging();
        SwiftCartSimulation simulation = new SwiftCartSimulation();
        simulation.start();
    }
    
    /**
     * Configure simple logging for the simulation
     */
    private static void configureLogging() {
        BusinessLogger.configureSimpleLogging();
    }
    
    /**
     * Start the complete simulation with all stations
     */
    public void start() {
        System.out.println("=== SwiftCart E-commerce Centre Simulation Starting ===");
        System.out.println("Processing " + TOTAL_ORDERS + " orders through automated facility");
        System.out.println("Location: Selangor, Malaysia");
        System.out.println("Expected Duration: 5 minutes");
        
        // Start reject handler first
        rejectHandler.startHandler();
        
        // Start all stations in proper order
        startOrderIntake();
        startPickingStation();
        startPackingStation();
        startLabellingStation();
        startSortingArea();
        startLoadingBay();
        
        // Schedule simulation end after 5 minutes
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(this::stopSimulation, SIMULATION_DURATION_MS, TimeUnit.MILLISECONDS);
        
        // Monitor progress every 30 seconds
        monitorProgress();
    }
    
    /**
     * Order Intake System - Receives orders at 500ms intervals
     * Verifies payment, inventory, and shipping address
     */
    private void startOrderIntake() {
        orderIntakeExecutor.submit(() -> {
            Thread.currentThread().setName("OrderThread-1");
            OrderIntakeSystem intakeSystem = new OrderIntakeSystem(totalRejected);
            
            for (int i = 1; i <= TOTAL_ORDERS && simulationRunning; i++) {
                try {
                    Order order = intakeSystem.receiveOrder(i);
                    if (order != null) {
                        System.out.printf("OrderIntake: Order #%d received (Thread: %s)%n", 
                            i, Thread.currentThread().getName());
                        pickingQueue.put(order);
                        totalProcessed.incrementAndGet();
                    }
                    
                    // Fixed 500ms interval as specified
                    Thread.sleep(ORDER_ARRIVAL_RATE_MS);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("OrderIntake interrupted during shutdown");
                    break;
                } catch (Exception e) {
                    System.err.println("OrderIntake error processing order " + i + ": " + e.getMessage());
                }
            }
            System.out.println("Order Intake: Completed processing " + TOTAL_ORDERS + " orders");
        });
    }
    
    /**
     * Picking Station - 4 concurrent robotic arms
     * Up to 4 orders can be picked simultaneously
     */
    private void startPickingStation() {
        for (int i = 1; i <= 4; i++) {
            final int stationId = i;
            pickingExecutor.submit(() -> {
                Thread.currentThread().setName("Picker-" + stationId);
                PickingStation station = new PickingStation(stationId, pickingStationCapacity);
                
                while (simulationRunning) {
                    try {
                        Order order = pickingQueue.poll(1, TimeUnit.SECONDS);
                        if (order != null) {
                            Order processedOrder = station.pickOrder(order);
                            if (processedOrder != null) {
                                packingQueue.put(processedOrder);
                            } else {
                                totalRejected.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("Picker-" + stationId + " error: " + e.getMessage());
                    }
                }
            });
        }
    }
    
    /**
     * Packing Station - Processes one order at a time
     * Includes scanner verification and capacity constraints
     */
    private void startPackingStation() {
        packingExecutor.submit(() -> {
            Thread.currentThread().setName("Packer-1");
            PackingStation packingStation = new PackingStation(packingLock);
            
            while (simulationRunning) {
                try {
                    // Check loading bay capacity constraint (10 containers max)
                    if (loadingQueue.size() >= 10) {
                        System.out.printf("Supervisor: Dispatch paused. %d containers at bay – waiting for truck.%n", 
                            loadingQueue.size());
                        Thread.sleep(2000); // Pause packing when bay is full
                        continue;
                    }
                    
                    Order order = packingQueue.poll(1, TimeUnit.SECONDS);
                    if (order != null) {
                        Order packedOrder = packingStation.packOrder(order);
                        if (packedOrder != null) {
                            System.out.printf("PackingStation: Packed Order #%d (Thread: %s)%n", 
                                order.getId(), Thread.currentThread().getName());
                            labellingQueue.put(packedOrder);
                            boxesPacked.incrementAndGet();
                        } else {
                            totalRejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Packer interrupted during shutdown");
                    break;
                } catch (Exception e) {
                    System.err.println("Packing error: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Labelling Station - Assigns tracking numbers and quality scanning
     * Processes one box at a time through quality scanner
     */
    private void startLabellingStation() {
        labellingExecutor.submit(() -> {
            Thread.currentThread().setName("Labeller-1");
            LabellingStation labellingStation = new LabellingStation();
            
            while (simulationRunning) {
                try {
                    Order order = labellingQueue.poll(1, TimeUnit.SECONDS);
                    if (order != null) {
                        Order labelledOrder = labellingStation.labelOrder(order);
                        if (labelledOrder != null) {
                            System.out.printf("LabellingStation: Labelled Order #%d with Tracking ID #%s (Thread: %s)%n", 
                                order.getId(), labelledOrder.getTrackingNumber(), Thread.currentThread().getName());
                            sortingQueue.put(labelledOrder);
                        } else {
                            totalRejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Labelling error: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Sorting Area - Groups boxes into batches of 6, loads into containers of 30
     * Sorts by regional zones and creates transport containers
     */
    private void startSortingArea() {
        sortingExecutor.submit(() -> {
            Thread.currentThread().setName("Sorter-1");
            SortingArea sortingArea = new SortingArea(loadingQueue, containersCreated);
            
            while (simulationRunning) {
                try {
                    Order box = sortingQueue.poll(1, TimeUnit.SECONDS);
                    if (box != null) {
                        sortingArea.sortBox(box);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Sorting error: " + e.getMessage());
                }
            }
            
            // Flush remaining boxes at simulation end
            sortingArea.flushRemaining();
        });
    }
    
    /**
     * Loading Bay - 3 autonomous loaders working across 2 loading bays
     * Each truck holds up to 18 containers
     * Includes loader breakdown simulation and congestion handling
     */
    private void startLoadingBay() {
        for (int i = 1; i <= 3; i++) {
            final int loaderId = i;
            loadingExecutor.submit(() -> {
                Thread.currentThread().setName("Loader-" + loaderId);
                AutonomousLoader loader = new AutonomousLoader(loaderId, loadingBay, 
                    trucksDispatched, truckWaitTimes, truckLoadTimes);
                
                while (simulationRunning) {
                    try {
                        Container container = loadingQueue.poll(1, TimeUnit.SECONDS);
                        if (container != null) {
                            loader.loadContainer(container, loadingQueue.size());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("Loader-" + loaderId + " interrupted during shutdown");
                        break;
                    } catch (Exception e) {
                        System.err.println("Loader-" + loaderId + " encountered error: " + e.getMessage());
                    }
                }
            });
        }
    }
    
    /**
     * Progress monitoring - Reports system status every 30 seconds
     */
    private void monitorProgress() {
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> {
            if (!simulationRunning) {
                monitor.shutdown();
                return;
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            int processed = totalProcessed.get();
            int rejected = totalRejected.get();
            int packed = boxesPacked.get();
            int containers = containersCreated.get();
            int dispatched = trucksDispatched.get();
            
            System.out.printf("%n=== Progress Report (%.1f minutes) ===%n", elapsed / 60000.0);
            System.out.printf("Orders: %d processed, %d rejected, %d packed%n", processed, rejected, packed);
            System.out.printf("Containers: %d created, %d in loading queue%n", containers, loadingQueue.size());
            System.out.printf("Trucks: %d dispatched, %d active, %d available bays%n", 
                dispatched, loadingBay.getActiveTrucks(), loadingBay.getAvailableBays());
            
            // Queue status monitoring
            System.out.printf("Queue Status - Picking: %d, Packing: %d, Labelling: %d, Sorting: %d%n",
                pickingQueue.size(), packingQueue.size(), labellingQueue.size(), sortingQueue.size());
            
            // Loading bay congestion monitoring
            if (loadingQueue.size() >= 10) {
                System.out.printf("*** PACKING PAUSED *** (Loading bay full: %d containers)%n", loadingQueue.size());
            }
            
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Stop simulation after 5 minutes and generate final statistics
     */
    private void stopSimulation() {
        System.out.println("%n=== Simulation Time Complete - Stopping ===%n");
        simulationRunning = false;
        
        // Stop reject handler
        rejectHandler.stop();
        
        // Allow time for final processing
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown all executors gracefully
        shutdownExecutor(orderIntakeExecutor, "Order Intake");
        shutdownExecutor(pickingExecutor, "Picking");
        shutdownExecutor(packingExecutor, "Packing");
        shutdownExecutor(labellingExecutor, "Labelling");
        shutdownExecutor(sortingExecutor, "Sorting");
        shutdownExecutor(loadingExecutor, "Loading");
        
        // Generate final statistics report
        printFinalStatistics();
    }
    
    /**
     * Gracefully shutdown executor service
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println(name + " executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Print comprehensive final statistics as required by assignment
     */
    private void printFinalStatistics() {
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("%n" + "=".repeat(60));
        System.out.println("         SWIFTCART SIMULATION RESULTS");
        System.out.println("=".repeat(60));
        System.out.printf("Simulation Duration: %.2f minutes%n", totalTime / 60000.0);
        System.out.printf("Total Orders Processed: %d%n", totalProcessed.get());
        System.out.printf("Orders Rejected: %d (%.1f%%)%n", 
            totalRejected.get(), (totalRejected.get() * 100.0) / TOTAL_ORDERS);
        System.out.printf("Boxes Packed: %d%n", boxesPacked.get());
        System.out.printf("Containers Created: %d%n", containersCreated.get());
        System.out.printf("Trucks Dispatched: %d%n", trucksDispatched.get());
        System.out.printf("Total Trucks Created: %d%n", loadingBay.getTotalTrucksCreated());
        
        // Calculate truck timing statistics with proper null checking
        if (!truckWaitTimes.isEmpty()) {
            try {
                OptionalDouble avgWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average();
                
                OptionalLong maxWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max();
                    
                OptionalLong minWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min();
                
                if (avgWaitTime.isPresent() && maxWaitTime.isPresent() && minWaitTime.isPresent()) {
                    System.out.printf("Truck Wait Times - Max: %.2f seconds, Min: %.2f seconds, Average: %.2f seconds%n", 
                        maxWaitTime.getAsLong() / 1000.0, minWaitTime.getAsLong() / 1000.0, avgWaitTime.getAsDouble() / 1000.0);
                }
            } catch (Exception e) {
                System.out.println("Error calculating truck timing statistics: " + e.getMessage());
            }
        }
        
        // Calculate loading times
        if (!truckLoadTimes.isEmpty()) {
            try {
                OptionalDouble avgLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average();
                
                OptionalLong maxLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max();
                    
                OptionalLong minLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min();
                
                if (avgLoadTime.isPresent() && maxLoadTime.isPresent() && minLoadTime.isPresent()) {
                    System.out.printf("Truck Load Times - Max: %.2f seconds, Min: %.2f seconds, Average: %.2f seconds%n", 
                        maxLoadTime.getAsLong() / 1000.0, minLoadTime.getAsLong() / 1000.0, avgLoadTime.getAsDouble() / 1000.0);
                }
            } catch (Exception e) {
                System.out.println("Error calculating truck load times: " + e.getMessage());
            }
        }
        
        // Throughput calculations
        if (totalTime > 0) {
            double ordersPerMinute = (totalProcessed.get() * 60000.0) / totalTime;
            System.out.printf("Order Processing Rate: %.1f orders/minute%n", ordersPerMinute);
            
            if (trucksDispatched.get() > 0) {
                double trucksPerHour = (trucksDispatched.get() * 3600000.0) / totalTime;
                System.out.printf("Truck Dispatch Rate: %.1f trucks/hour%n", trucksPerHour);
            }
        }
        
        // System clearance verification as required
        System.out.println("%n--- System Clearance Verification ---");
        boolean systemCleared = true;
        
        if (pickingQueue.size() > 0) {
            System.out.printf("WARNING: %d orders remain in picking queue%n", pickingQueue.size());
            systemCleared = false;
        }
        if (packingQueue.size() > 0) {
            System.out.printf("WARNING: %d orders remain in packing queue%n", packingQueue.size());
            systemCleared = false;
        }
        if (labellingQueue.size() > 0) {
            System.out.printf("WARNING: %d orders remain in labelling queue%n", labellingQueue.size());
            systemCleared = false;
        }
        if (sortingQueue.size() > 0) {
            System.out.printf("WARNING: %d orders remain in sorting queue%n", sortingQueue.size());
            systemCleared = false;
        }
        if (loadingQueue.size() > 0) {
            System.out.printf("WARNING: %d containers remain in loading queue%n", loadingQueue.size());
            systemCleared = false;
        }
        
        if (systemCleared) {
            System.out.println("✓ All orders, boxes, and containers have been cleared from the system");
        } else {
            System.out.println("✗ System not fully cleared - items remain in queues");
        }
        
        System.out.println("=".repeat(60));
        System.out.println("Simulation completed successfully!");
    }
    
    /**
     * Business Logger for tracking key events in the simulation
     * Provides centralized logging for business operations
     */
    public static class BusinessLogger {
        private static Logger logger = Logger.getLogger("SwiftCartBusiness");
        
        /**
         * Configure simple console logging
         */
        public static void configureSimpleLogging() {
            logger.setUseParentHandlers(false);
            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%tT] %s%n", 
                        new Date(record.getMillis()), record.getMessage());
                }
            });
            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
        }
        
        /**
         * Log order rejection with reason
         */
        public static void logOrderRejected(int orderId, String reason) {
            System.out.printf("Order #%d REJECTED: %s%n", orderId, reason);
        }
        
        /**
         * Log container loading operation
         */
        public static void logContainerLoading(int loaderId, int containerId, int bayId) {
            System.out.printf("Loader #%d: Loading Container #%d at Bay #%d%n", 
                loaderId, containerId, bayId);
        }
        
        /**
         * Log truck waiting for bay availability
         */
        public static void logTruckWaiting(int truckId) {
            System.out.printf("Supervisor: Truck #%d waiting for available loading bay%n", truckId);
        }
        
        /**
         * Log truck departure
         */
        public static void logTruckDeparture(int truckId, int containerCount) {
            System.out.printf("Supervisor: Truck #%d departed with %d containers%n", 
                truckId, containerCount);
        }
        
        /**
         * Log autonomous loader breakdown
         */
        public static void logLoaderBreakdown(int loaderId) {
            System.out.printf("Maintenance: Loader #%d broke down - repair needed%n", loaderId);
        }
        
        /**
         * Log loader repair completion
         */
        public static void logLoaderRepaired(int loaderId) {
            System.out.printf("Maintenance: Loader #%d repaired and operational%n", loaderId);
        }
        
        /**
         * Log dispatch pause due to capacity constraints
         */
        public static void logDispatchPaused(int containerCount) {
            System.out.printf("Supervisor: Dispatch PAUSED due to container backlog (%d containers waiting)%n", 
                containerCount);
        }
    }
}