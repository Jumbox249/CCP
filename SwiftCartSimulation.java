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
    
    // Dispatch pause tracking
    // Removed - now handled in LoadingBay with thread-safe AtomicBoolean
    
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
            Thread.current