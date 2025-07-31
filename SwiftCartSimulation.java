import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class SwiftCartSimulation {
    private static final int TOTAL_ORDERS = 600;
    private static final int SIMULATION_DURATION_MS = 300000;
    private static final int ORDER_ARRIVAL_RATE_MS = 500;
    private static final int ORDER_INTAKE_DURATION_MS = 300000;
    
    private final ExecutorService orderIntakeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pickingExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService packingExecutor = Executors.newFixedThreadPool(5);
    private final ExecutorService labellingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sortingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService loadingExecutor = Executors.newFixedThreadPool(3);
    
    private final BlockingQueue<Order> pickingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> packingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> labellingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Order> sortingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Container> loadingQueue = new LinkedBlockingQueue<>();
    
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);
    private final AtomicInteger boxesPacked = new AtomicInteger(0);
    private final AtomicInteger containersCreated = new AtomicInteger(0);
    private final AtomicInteger trucksDispatched = new AtomicInteger(0);
    
    private final RejectHandler rejectHandler = new RejectHandler(totalRejected);
    
    private final List<Long> truckWaitTimes = Collections.synchronizedList(new ArrayList<Long>());
    private final List<Long> truckLoadTimes = Collections.synchronizedList(new ArrayList<Long>());
    
    private final Semaphore pickingStationCapacity = new Semaphore(4, true);
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
        System.out.println("=== SwiftCart E-commerce Centre Simulation Starting ===");
        System.out.println("Processing " + TOTAL_ORDERS + " orders through automated facility");
        System.out.println("Location: Selangor, Malaysia");
        System.out.println("Expected Duration: 5 minutes");
        
        rejectHandler.startHandler();
        
        startOrderIntake();
        startPickingStation();
        startPackingStation();
        startLabellingStation();
        startSortingArea();
        startLoadingBay();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(this::stopSimulation, SIMULATION_DURATION_MS, TimeUnit.MILLISECONDS);
        
        scheduler.scheduleAtFixedRate(this::checkAndDispatchWaitingTrucks, 10, 10, TimeUnit.SECONDS);
        
        monitorProgress();
    }
    
    private void startOrderIntake() {
        orderIntakeExecutor.submit(() -> {
            Thread.currentThread().setName("OrderThread-1");
            OrderIntakeSystem intakeSystem = new OrderIntakeSystem(totalRejected);
            
            long orderIntakeStartTime = System.currentTimeMillis();
            for (int i = 1; i <= TOTAL_ORDERS && simulationRunning; i++) {
                try {
                    long elapsed = System.currentTimeMillis() - orderIntakeStartTime;
                    if (elapsed >= ORDER_INTAKE_DURATION_MS) {
                        System.out.printf("OrderIntake: Completed processing all orders. Processed %d orders%n", i-1);
                        break;
                    }
                    
                    Order order = intakeSystem.receiveOrder(i);
                    if (order != null) {
                        System.out.printf("OrderIntake: Order #%d received (Thread: %s)%n",
                            i, Thread.currentThread().getName());
                        pickingQueue.put(order);
                        totalProcessed.incrementAndGet();
                    }
                    
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
    
    private void startPackingStation() {
        for (int i = 1; i <= 5; i++) {
            final int packerId = i;
            packingExecutor.submit(() -> {
                Thread.currentThread().setName("Packer-" + packerId);
                PackingStation packingStation = new PackingStation(packingLock);
                
                while (simulationRunning) {
                    try {
                        if (loadingQueue.size() >= 10) {
                            System.out.printf("Supervisor: Dispatch paused. %d containers at bay – waiting for truck.%n",
                                loadingQueue.size());
                            Thread.sleep(500);
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
                        System.out.println("Packer-" + packerId + " interrupted during shutdown");
                        break;
                    } catch (Exception e) {
                        System.err.println("Packer-" + packerId + " error: " + e.getMessage());
                    }
                }
            });
        }
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
                } catch (Exception e) {
                    System.err.println("Labelling error: " + e.getMessage());
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
                        sortingArea.sortBox(box);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Sorting error: " + e.getMessage());
                }
            }
            
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
            
            System.out.printf("Queue Status - Picking: %d, Packing: %d, Labelling: %d, Sorting: %d%n",
                pickingQueue.size(), packingQueue.size(), labellingQueue.size(), sortingQueue.size());
            
            if (loadingQueue.size() >= 10) {
                System.out.printf("*** PACKING PAUSED *** (Loading bay full: %d containers)%n", loadingQueue.size());
            }
            
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void stopSimulation() {
        System.out.println("%n=== Simulation Time Complete - Stopping ===%n");
        simulationRunning = false;
        
        rejectHandler.stop();
        
        System.out.println("Allowing 20 seconds for final processing...");
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        performFinalCleanup();
        
        shutdownExecutor(orderIntakeExecutor, "Order Intake");
        shutdownExecutor(pickingExecutor, "Picking");
        shutdownExecutor(packingExecutor, "Packing");
        shutdownExecutor(labellingExecutor, "Labelling");
        shutdownExecutor(sortingExecutor, "Sorting");
        shutdownExecutor(loadingExecutor, "Loading");
        
        forceDispatchRemainingTrucks();
        
        printFinalStatistics();
    }
    
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
        
        if (totalTime > 0) {
            double ordersPerMinute = (totalProcessed.get() * 60000.0) / totalTime;
            System.out.printf("Order Processing Rate: %.1f orders/minute%n", ordersPerMinute);
            
            if (trucksDispatched.get() > 0) {
                double trucksPerHour = (trucksDispatched.get() * 3600000.0) / totalTime;
                System.out.printf("Truck Dispatch Rate: %.1f trucks/hour%n", trucksPerHour);
            }
        }
        
        System.out.println("%n--- Final System Status ---");
        System.out.printf("Orders in processing: Picking: %d, Packing: %d, Labelling: %d, Sorting: %d%n",
            pickingQueue.size(), packingQueue.size(), labellingQueue.size(), sortingQueue.size());
        System.out.printf("Containers awaiting dispatch: %d%n", loadingQueue.size());
        System.out.println("✓ SwiftCart simulation completed successfully");
        
        System.out.println("=".repeat(60));
        System.out.println("Simulation completed successfully!");
    }
    
    private void checkAndDispatchWaitingTrucks() {
        if (!simulationRunning) return;
        
        try {
            int forcedDispatches = loadingBay.forceDispatchOldTrucks(15000);
            if (forcedDispatches > 0) {
                trucksDispatched.addAndGet(forcedDispatches);
                System.out.printf("Supervisor: Force dispatched %d trucks due to timeout%n", forcedDispatches);
            }
        } catch (Exception e) {
            System.err.println("Error during periodic truck dispatch check: " + e.getMessage());
        }
    }
    
    private void forceDispatchRemainingTrucks() {
        try {
            int remainingTrucks = loadingBay.forceAllTrucksDeparture();
            if (remainingTrucks > 0) {
                trucksDispatched.addAndGet(remainingTrucks);
                System.out.printf("Simulation End: Force dispatched %d remaining trucks%n", remainingTrucks);
            }
        } catch (Exception e) {
            System.err.println("Error during final truck dispatch: " + e.getMessage());
        }
    }
    
    private void performFinalCleanup() {
        System.out.println("=== Starting Final Cleanup ===");
        
        long cleanupStartTime = System.currentTimeMillis();
        long maxCleanupTime = 15000;
        
        int lastReportedTotal = -1;
        while (System.currentTimeMillis() - cleanupStartTime < maxCleanupTime) {
            int currentTotal = pickingQueue.size() + packingQueue.size() + labellingQueue.size() +
                              sortingQueue.size() + loadingQueue.size();
            
            if (currentTotal == 0) {
                System.out.println("Final cleanup: All queues cleared successfully");
                break;
            }
            
            if (currentTotal != lastReportedTotal) {
                System.out.printf("Final cleanup: Processing remaining items - Picking: %d, Packing: %d, Labelling: %d, Sorting: %d, Loading: %d%n",
                    pickingQueue.size(), packingQueue.size(), labellingQueue.size(), sortingQueue.size(), loadingQueue.size());
                lastReportedTotal = currentTotal;
            }
            
            while (!loadingQueue.isEmpty()) {
                try {
                    Container container = loadingQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (container != null) {
                        System.out.printf("Final cleanup: Force processing Container #%d%n", container.getId());
                        int dispatched = loadingBay.forceAllTrucksDeparture();
                        if (dispatched > 0) {
                            trucksDispatched.addAndGet(dispatched);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("=== Final Cleanup Complete ===");
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