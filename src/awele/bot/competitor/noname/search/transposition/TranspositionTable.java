package awele.bot.competitor.noname.search.transposition;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 */
public final class TranspositionTable
{
    private long probeCount;
    private long hitCount;
    private long storeCount;

    private static final byte T_EXACT = 0;
    private static final byte T_LOWER = 1;
    private static final byte T_UPPER = 2;

    private static final int SHIFT_MOVE  = 0;
    private static final int SHIFT_TYPE  = 3;
    private static final int SHIFT_DEPTH = 5;
    private static final int SHIFT_GEN   = 11;

    private static final int MASK_MOVE  = 0x7;
    private static final int MASK_TYPE  = 0x3;
    private static final int MASK_DEPTH = 0x3F;
    private static final int MASK_GEN   = 0xF;

    private static final float EMAX = 75f;

    private final int size;
    private final int mask;

    private final long[] keys;
    private final short[] meta;
    private final short[] evalQ;

    private int generation;
    private int used;

    public TranspositionTable(int pow2)
    {
        if (pow2 < 1 || pow2 > 30)
            throw new IllegalArgumentException("pow2 doit être entre 1 et 30");

        this.size = 1 << pow2;
        this.mask = size - 1;

        this.keys = new long[size];
        this.meta = new short[size];
        this.evalQ = new short[size];

        this.generation = 1;
        this.used = 0;
    }

    public void incrementAge()
    {
        generation++;
        if (generation > 15)
            generation = 1;
    }

    public void clear()
    {
        probeCount = 0;
        hitCount = 0;
        storeCount = 0;

        generation = 1;
        used = 0;

        Arrays.fill(keys, 0L);
        Arrays.fill(meta, (short) 0);
        Arrays.fill(evalQ, (short) 0);
    }

    public TranspositionEntry probe(long key)
    {
        probeCount++;

        int idx = ((int) key) & mask;

        if (keys[idx] != key)
            return null;

        int packedMeta = meta[idx] & 0xFFFF;
        int storedGen = (packedMeta >>> SHIFT_GEN) & MASK_GEN;

        if (storedGen == 0)
            return null;

        hitCount++;

        int bestMove = (packedMeta >>> SHIFT_MOVE) & MASK_MOVE;
        byte typeEncoded = (byte)((packedMeta >>> SHIFT_TYPE) & MASK_TYPE);
        int depth = (packedMeta >>> SHIFT_DEPTH) & MASK_DEPTH;

        double evaluation = unpackEval16(evalQ[idx]);
        EntryType type = decodeType(typeEncoded);

        return new TranspositionEntry(key, evaluation, depth, type, bestMove, storedGen);
    }

    public void store(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
    {
        storeCount++;

        int idx = ((int) key) & mask;

        int existingMeta = meta[idx] & 0xFFFF;
        int existingGen = (existingMeta >>> SHIFT_GEN) & MASK_GEN;
        int existingDepth = (existingMeta >>> SHIFT_DEPTH) & MASK_DEPTH;

        boolean empty = (existingGen == 0);
        boolean sameKey = (!empty && keys[idx] == key);
        boolean older = (!empty && existingGen != generation);

        int newDepth = clampDepth(depthRemaining);
        boolean deeper = (newDepth >= existingDepth);

        if (!(empty || sameKey || older || deeper))
            return;

        if (empty)
            used++;

        int bm = clampMove(bestMove);
        int t = encodeType(type) & MASK_TYPE;

        int packed = 0;
        packed |= (bm & MASK_MOVE) << SHIFT_MOVE;
        packed |= (t & MASK_TYPE) << SHIFT_TYPE;
        packed |= (newDepth & MASK_DEPTH) << SHIFT_DEPTH;
        packed |= (generation & MASK_GEN) << SHIFT_GEN;

        keys[idx] = key;
        meta[idx] = (short) packed;
        evalQ[idx] = packEval16(evaluation);
    }

    public int size()
    {
    	return size;
    }
    
    public int used()
    {
    	return used;
    }
    
    public double fillRatio()
    {
    	return (double) used / size;
    }

    public long probes()
    {
    	return probeCount;
    }
    
    public long hits()
    {
    	return hitCount;
    }
    
    public long stores()
    {
    	return storeCount;
    }

    public double hitRate()
    {
        return probeCount == 0 ? 0.0 : (double) hitCount / probeCount;
    }

    private static byte encodeType(EntryType type)
    {
        switch (type)
        {
            case EXACT:
            	return T_EXACT;
            case LOWER_BOUND:
            	return T_LOWER;
            case UPPER_BOUND:
            	return T_UPPER;
            default:
            	throw new IllegalStateException();
        }
    }

    private static EntryType decodeType(byte t)
    {
        switch (t)
        {
            case T_EXACT:
            	return EntryType.EXACT;
            case T_LOWER:
            	return EntryType.LOWER_BOUND;
            case T_UPPER:
            	return EntryType.UPPER_BOUND;
            default:
            	throw new IllegalStateException();
        }
    }

    private static int clampMove(int m)
    {
        return Math.max(0, Math.min(7, m));
    }

    private static int clampDepth(int d)
    {
        return Math.max(0, Math.min(63, d));
    }

    private static short packEval16(double eval)
    {
        float x = (float) eval;
        
        if (x > EMAX)
        	x = EMAX;
        if (x < -EMAX)
        	x = -EMAX;
        return (short) Math.round(x * 32767f / EMAX);
    }

    private static double unpackEval16(short q)
    {
        return (q * (double)EMAX) / 32767.0;
    }
}