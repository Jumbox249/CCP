private void printFinalStatistics() {
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("         SWIFTCART SIMULATION RESULTS");
        System.out.println("=".repeat(50));
        System.out.printf("Simulation Duration: %.2f minutes%n", totalTime / 60000.0);
        System.out.printf("Total Orders Processed: %d%n", totalProcessed.get());
        System.out.printf("Orders Rejected: %d (%.1f%%)%n", 
            totalRejected.get(), (totalRejected.get() * 100.0) / TOTAL_ORDERS);
        System.out.printf("Boxes Packed: %d%n", boxesPacked.get());
        System.out.printf("Containers Created: %d%n", containersCreated.get());
        System.out.printf("Trucks Dispatched: %d%n", trucksDispatched.get());
        System.out.printf("Total Trucks Created: %d%n", loadingBay.getTotalTrucksCreated());
        
        // Calculate truck timing statistics (with null safety)
        if (!truckWaitTimes.isEmpty()) {
            try {
                double avgWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
                
                long maxWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
                    
                long minWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
                
                System.out.printf("Truck Wait Times - Max: %.2f seconds, Min: %.2f seconds, Average: %.2f seconds%n", 
                    maxWaitTime / 1000.0, minWaitTime / 1000.0,import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class SwiftCartSimulation {
    private static final int TOTAL_ORDERS = 600;
    private static final int SIMULATION_DURATION_MS = 300000; // 5 minutes as required
    private static final int ORDER_ARRIVAL_RATE_MS = 500; // Fixed 500ms as specified
    
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
    
    // Reject Handler for defective orders
    private final RejectHandler rejectHandler = new RejectHandler(totalRejected);
    
    // Truck timing statistics (with size limits to prevent memory leaks)
    private final List<Long> truckWaitTimes = Collections.synchronizedList(new ArrayList<>()) {
        @Override
        public boolean add(Long e) {
            if (size() > 1000) removeFirst(); // Keep only last 1000 entries
            return super.add(e);
        }
    };
    private final List<Long> truckLoadTimes = Collections.synchronizedList(new ArrayList<>()) {
        @Override
        public boolean add(Long e) {
            if (size() > 1000) removeFirst(); // Keep only last 1000 entries
            return super.add(e);
        }
    };
    
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
        System.out.println("Processing " + TOTAL_ORDERS + " orders through automated e-commerce centre");
        
        // Start reject handler first
        rejectHandler.startHandler();
        
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
                        System.out.printf("OrderIntake: Order #%d received (Thread: %s)%n", 
                            i, Thread.currentThread().getName());
                        pickingQueue.put(order);
                        totalProcessed.incrementAndGet();
                    }
                    
                    // Orders arrive every 500ms as specified
                    Thread.sleep(ORDER_ARRIVAL_RATE_MS);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("OrderIntake interrupted during shutdown");
                    break;
                } catch (Exception e) {
                    System.err.println("OrderIntake error processing order " + i + ": " + e.getMessage());
                    // Continue with next order
                }
            }
            System.out.println("Order Intake: Completed processing " + TOTAL_ORDERS + " orders");
        });
    }
    
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
                    }
                }
            });
        }
    }
    
    private void startPackingStation() {
        packingExecutor.submit(() -> {
            Thread.currentThread().setName("Packer-1");
            PackingStation packingStation = new PackingStation(packingLock);
            
            while (simulationRunning) {
                try {
                    // CORRECTED: Check if loading bay is full (10 containers)
                    // If full, packing must pause as per requirements
                    if (loadingQueue.size() >= 10) {
                        System.out.printf("Supervisor: Dispatch paused. %d containers at bay – waiting for truck.%n", 
                            loadingQueue.size());
                        Thread.sleep(2000); // Pause for 2 seconds
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
                }
            }
        });
    }
    
    private void startSortingArea() {
        sortingExecutor.submit(() -> {
            Thread.currentThread().setName("Sorter-1");
            SortingArea sortingArea = new SortingArea(loadingQueue, containersCreated);
            
            while (simulationRunning) {
                try {
                    Order box = sortingQueue.poll(1, TimeUnit.SECONDS);
                    if (box != null) {
                        sortingArea.sortBox(box); // Sort boxes, not orders
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Flush remaining orders at simulation end
            sortingArea.flushRemaining();
        });
    }
    
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
                            // Simplified - no container queue size needed since packing pauses instead
                            loader.loadContainer(container, 0);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("Loader-" + loaderId + " interrupted during shutdown");
                        break;
                    } catch (Exception e) {
                        System.err.println("Loader-" + loaderId + " encountered error: " + e.getMessage());
                        // Continue running unless it's an InterruptedException
                    }
                }
            });
        }
    }
    
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
            
            System.out.printf("\n=== Progress Report (%.1f minutes) ===%n", elapsed / 60000.0);
            System.out.printf("Orders: %d processed, %d rejected, %d packed%n", processed, rejected, packed);
            System.out.printf("Containers: %d created, %d in loading queue%n", containers, loadingQueue.size());
            System.out.printf("Trucks: %d dispatched, %d active, %d available bays%n", 
                dispatched, loadingBay.getActiveTrucks(), loadingBay.getAvailableBays());
            
            // Show queue status
            System.out.printf("Queue Status - Picking: %d, Packing: %d, Labelling: %d, Sorting: %d%n",
                pickingQueue.size(), packingQueue.size(), labellingQueue.size(), sortingQueue.size());
            
            // Show loading bay status instead of dispatch pause
            System.out.printf("Loading Bay: %d containers queued, %d active trucks, %d available bays%n",
                loadingQueue.size(), loadingBay.getActiveTrucks(), loadingBay.getAvailableBays());
            
            // Show if packing is paused due to full loading bay
            if (loadingQueue.size() >= 10) {
                System.out.printf("*** PACKING PAUSED *** (Loading bay full: %d containers)%n", loadingQueue.size());
            }
            
        }, 30, 30, TimeUnit.SECONDS); // Back to 30 seconds as originally specified
    }
    
    private void stopSimulation() {
        System.out.println("\n=== Simulation Time Complete - Stopping ===");
        simulationRunning = false;
        
        // Stop reject handler
        rejectHandler.stop();
        
        // Allow shorter time for final processing
        try {
            Thread.sleep(3000); // 3 seconds for final processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown all executors
        shutdownExecutor(orderIntakeExecutor, "Order Intake");
        shutdownExecutor(pickingExecutor, "Picking");
        shutdownExecutor(packingExecutor, "Packing");
        shutdownExecutor(labellingExecutor, "Labelling");
        shutdownExecutor(sortingExecutor, "Sorting");
        shutdownExecutor(loadingExecutor, "Loading");
        
        // Final statistics
        printFinalStatistics();
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) { // 5 seconds instead of 10
                System.out.println(name + " executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void printFinalStatistics() {
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("         SWIFTCART SIMULATION RESULTS");
        System.out.println("=".repeat(50));
        System.out.printf("Simulation Duration: %.2f minutes%n", totalTime / 60000.0);
        System.out.printf("Total Orders Processed: %d%n", totalProcessed.get());
        System.out.printf("Orders Rejected: %d (%.1f%%)%n", 
            totalRejected.get(), (totalRejected.get() * 100.0) / TOTAL_ORDERS);
        System.out.printf("Boxes Packed: %d%n", boxesPacked.get());
        System.out.printf("Containers Created: %d%n", containersCreated.get());
        System.out.printf("Trucks Dispatched: %d%n", trucksDispatched.get());
        System.out.printf("Total Trucks Created: %d%n", loadingBay.getTotalTrucksCreated());
        
        // Calculate truck timing statistics (with null safety)
        if (!truckWaitTimes.isEmpty()) {
            try {
                double avgWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
                
                long maxWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
                    
                long minWaitTime = truckWaitTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
                
                System.out.printf("Truck Wait Times - Max: %.2f seconds, Min: %.2f seconds, Average: %.2f seconds%n", 
                    maxWaitTime / 1000.0, minWaitTime / 1000.0, avgWaitTime / 1000.0);
            } catch (Exception e) {
                System.out.println("Error calculating truck timing statistics: " + e.getMessage());
            }
        }
        
        // Calculate loading times
        if (!truckLoadTimes.isEmpty()) {
            try {
                double avgLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
                
                long maxLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
                    
                long minLoadTime = truckLoadTimes.stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
                
                System.out.printf("Truck Load Times - Max: %.2f seconds, Min: %.2f seconds, Average: %.2f seconds%n", 
                    maxLoadTime / 1000.0, minLoadTime / 1000.0, avgLoadTime / 1000.0);
            } catch (Exception e) {
                System.out.println("Error calculating truck load times: " + e.getMessage());
            }
        }
        
        // Throughput calculations
        double ordersPerMinute = (totalProcessed.get() * 60000.0) / totalTime;
        System.out.printf("Order Processing Rate: %.1f orders/minute%n", ordersPerMinute);
        
        if (trucksDispatched.get() > 0) {
            double trucksPerHour = (trucksDispatched.get() * 3600000.0) / totalTime;
            System.out.printf("Truck Dispatch Rate: %.1f trucks/hour%n", trucksPerHour);
        }
        
        // Verification that system is cleared
        System.out.println("\n--- System Clearance Verification ---");
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
        
        System.out.println("=".repeat(50));
    }
    
    /**
     * Business Logger for tracking key events in the simulation
     */
    public static class BusinessLogger {
        private static Logger logger = Logger.getLogger("SwiftCartBusiness");
        
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
        
        public static void logOrderRejected(int orderId, String reason) {
            System.out.printf("Order #%d REJECTED: %s%n", orderId, reason);
        }
        
        public static void logBoxSorted(Order box, int batchNumber) {
            System.out.printf("Sorter: Box #%s sorted into Batch #%d%n", 
                box.getTrackingNumber(), batchNumber);
        }
        
        public static void logContainerLoading(int loaderId, int containerId, int bayId) {
            System.out.printf("Loader #%d: Loading Container #%d at Bay #%d%n", 
                loaderId, containerId, bayId);
        }
        
        public static void logTruckWaiting(int truckId) {
            System.out.printf("Supervisor: Truck #%d waiting for available loading bay%n", truckId);
        }
        
        public static void logTruckDeparture(int truckId, int containerCount) {
            System.out.printf("Supervisor: Truck #%d departed with %d containers%n", 
                truckId, containerCount);
        }
        
        public static void logLoaderBreakdown(int loaderId) {
            System.out.printf("Maintenance: Loader #%d broke down - repair needed%n", loaderId);
        }
        
        public static void logLoaderRepaired(int loaderId) {
            System.out.printf("Maintenance: Loader #%d repaired and operational%n", loaderId);
        }
        
        public static void logDispatchPaused(int containerCount) {
            System.out.printf("Supervisor: Dispatch PAUSED due to container backlog (%d containers waiting)%n", 
                containerCount);
        }
    }
}