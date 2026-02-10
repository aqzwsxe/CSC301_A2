package Utils;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PersistenceManager acts as a serialization bridge between the microservice's live memory and the physical disk.
 * It ensures that the state of the service survives after a server restart
 */
public class PersistenceManager {
    /**
     *  This method is called during the /shutdown event
     *  1: It creates/overwites a.ser file (like order.ser)
     *  2: It saves the entire Maps (contains all User, Product or Order objects)
     *  3: It saves the current state of the AtomicInteger. This ensures that if the last ID created was 105,
     *  the system remembers where to pick up next time
     * @param filename
     * @param data
     * @param counter
     */
    public static void saveServiceData(String filename, Map<?,?> data, AtomicInteger counter){
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(data);
            oos.writeInt(counter.get());
        } catch (Exception e ) {
            System.err.println("Error saving data to " + filename + ": " + e.getMessage());
        }
    }

    /**
     * The method is called during a /restart event. This method follows the last-in, first-out rule
     * 1: If the file does not exist (first time running), it returns an empty ConcurrentHashMap
     * 2: Map Restoration: It reads the object back and casts it to a Map
     * 3: It reads the integer and use counter.set(). This step prevents ID collision. It ensures that
     * new order don't start back at ID 0
     * @param filename
     * @param counter
     * @return
     * @param <K>
     * @param <V>
     */
    @SuppressWarnings("unchecked")
    public static <K,V> Map<K,V> loadServiceData(String filename, AtomicInteger counter){
        File file = new File(filename);
        if(!file.exists()){
            return new ConcurrentHashMap<>();
        }

        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<K,V> data = (Map<K,V>) ois.readObject();
            int savedCounter = ois.readInt();
            counter.set(savedCounter);
            return data;
        } catch (Exception e) {
            System.err.println("Error loading data from " + filename+". starting fresh");
            return new ConcurrentHashMap<>();
        }
    }


}
