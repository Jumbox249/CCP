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