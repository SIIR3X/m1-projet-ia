package awele.bot.competitor.noname.ordering;

import java.util.Arrays;

public final class PositionHistory
{
    private static final int MAP_SIZE = 1024;
    private static final int MAP_MASK = MAP_SIZE - 1;
    private static final int MAX_STACK_SIZE = 200;
    
    private static final long[] keys = new long[MAP_SIZE];
    private static final int[] counts = new int[MAP_SIZE];
    private static final long[] stack = new long[MAX_STACK_SIZE];
    
    private static int stackSize = 0;
    private static int maxStackSize = 0;
    private static int uniqueRepetitions = 0;
    
    public static void push(long key)
    {
        if (stackSize >= MAX_STACK_SIZE)
            return;
        
        stack[stackSize++] = key;
        increment(key);
        
        if (stackSize > maxStackSize)
            maxStackSize = stackSize;
    }
    
    public static void pop(long key)
    {
        if (stackSize <= 0)
            return;
        
        stackSize--;
        decrement(key);
    }
    
    public static int count(long key)
    {
        return get(key);
    }
    
    public static void clear()
    {
        stackSize = 0;
        maxStackSize = 0;
        uniqueRepetitions = 0;
        Arrays.fill(keys, 0L);
        Arrays.fill(counts, 0);
    }
    
    public static int size()
    {
        return stackSize;
    }
    
    public static int maxSize()
    {
        return maxStackSize;
    }
    
    public static int getRepetitionsDetected()
    {
        return uniqueRepetitions;
    }
    
    private static void increment(long key)
    {
        int idx = hash(key);
        
        while (true)
        {
            if (counts[idx] == 0)
            {
                keys[idx] = key;
                counts[idx] = 1;
                return;
            }
            
            if (keys[idx] == key)
            {
                counts[idx]++;
                
                if (counts[idx] == 2)
                    uniqueRepetitions++;
                
                return;
            }
            
            idx = (idx + 1) & MAP_MASK;
        }
    }
    
    private static void decrement(long key)
    {
        int idx = hash(key);
        
        while (true)
        {
            if (counts[idx] == 0)
                return;
            
            if (keys[idx] == key)
            {
                counts[idx]--;
                
                if (counts[idx] == 0)
                    keys[idx] = 0L;
                
                return;
            }
            
            idx = (idx + 1) & MAP_MASK;
        }
    }
    
    private static int get(long key)
    {
        int idx = hash(key);
        int probes = 0;
        
        while (probes < MAP_SIZE)
        {
            if (counts[idx] == 0)
                return 0;
            
            if (keys[idx] == key)
                return counts[idx];
            
            idx = (idx + 1) & MAP_MASK;
            probes++;
        }
        
        return 0;
    }
    
    private static int hash(long key)
    {
        key ^= (key >>> 32);
        key ^= (key >>> 16);
        return ((int) key) & MAP_MASK;
    }
    
    private PositionHistory()
    {
        throw new AssertionError();
    }
}