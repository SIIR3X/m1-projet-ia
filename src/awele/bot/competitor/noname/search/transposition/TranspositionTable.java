package awele.bot.competitor.noname.search.transposition;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 * Table de transposition
 * 
 * Layout par entrée (64 bits) :
 * - lock : 32 bits (si useLocks32 = true)
 * - meta : 16 bits
 *      - bestMove : 3 bits (0-7)
 *      - type : 2 bits
 *      - depth : 6 bits (0-63)
 *      - generation : 4 bits (1-15, 0 = vide)
 * - evaluation : 16 bits (short quantifié)
 * 
 * Normalement la réduction de l'évaluation pose pas problème
 * A vérifier
 * bornes [0 ; +15] / poids [-1 ; +1] => [-75 ; +75]
 */
public final class TranspositionTable
{
    // ===== Constantes de configuration =====
    
    private static final byte T_EXACT = 0;
    private static final byte T_LOWER = 1;
    private static final byte T_UPPER = 2;

    // ===== Layout meta (16 bits) =====
    
    private static final int SHIFT_MOVE = 0; // 3 bits
    private static final int SHIFT_TYPE = 3; // 2 bits
    private static final int SHIFT_DEPTH = 5; // 6 bits
    private static final int SHIFT_GEN = 11;// 4 bits

    private static final int MASK_MOVE = 0x7; // 0b111
    private static final int MASK_TYPE = 0x3; // 0b11
    private static final int MASK_DEPTH = 0x3F; // 0b111111
    private static final int MASK_GEN = 0xF; // 0b1111

    // ===== Evaluation =====
    
    private static final float EMAX = 75f;

    // ===== Configuration de la table =====
    
    private final int pow2;
    private final int size;
    private final int mask;

    // ===== Tables =====
    
    /**
     * Premier mode : clé complète (64 bits)
     */
    private final long[] keys;

    /**
     * Deuxième mode : clé partielle (32 bits)
     */
    private final int[] locks;

    /**
     * Données encodées :
     * - meta : informations compactées (16 bits)
     * - evalQ : évaluation quantifiée (16 bits)
     */
    private final short[] meta;
    private final short[] evalQ;

    /**
     * Indique si on utilise le mode lock (32 bits) ou le mode clé complète (64 bits)
     */
    private final boolean useLocks32;

    /**
     * Génération actuelle (1-15, 0 = vide)
     */
    private int generation;

    public TranspositionTable()
    {
        this(21, false);
    }

    public TranspositionTable(int pow2, boolean useLock32)
    {
        if (pow2 < 1 || pow2 > 30)
            throw new IllegalArgumentException("pow2 must be in [1..30]");

        this.pow2 = pow2;
        this.size = 1 << pow2;
        this.mask = size - 1;

        this.useLocks32 = useLock32;

        this.meta = new short[size];
        this.evalQ = new short[size];

        if (useLock32)
        {
            this.keys = null;
            this.locks = new int[size];
        }
        else
        {
            this.keys = new long[size];
            this.locks = null;
        }

        this.generation = 1;
    }

    /**
     * Incrémente la génération de la table de transposition
     */
    public void incrementAge()
    {
        generation++;
        if (generation > 15)
            generation = 1;
    }

    /**
     * Clear la table de transposition
     */
    public void clear()
    {
        generation = 1;
        Arrays.fill(meta, (short) 0);
        Arrays.fill(evalQ, (short) 0);

        if (useLocks32)
            Arrays.fill(locks, 0);
        else
            Arrays.fill(keys, 0L);
    }

    /**
     * Probe la table de transposition pour une clé donnée
     * @param key la clé à chercher
     * @return une entrée si trouvée, sinon null
     */
    public TranspositionEntry probe(long key)
    {
        final int idx = ((int) key) & mask;

        final int packedMeta = meta[idx] & 0xFFFF;
        final int storedGeneration = (packedMeta >>> SHIFT_GEN) & MASK_GEN;

        if (storedGeneration == 0)
            return null;

        if (useLocks32)
        {
            final int lock = (int) (key >>> 32);
            if (locks[idx] != lock)
                return null;
        }
        else
        {
            final long storedKey = keys[idx];
            if (storedKey == 0L || storedKey != key)
                return null;
        }

        final int bestMove = (packedMeta >>> SHIFT_MOVE) & MASK_MOVE;
        final byte typeEncoded = (byte) ((packedMeta >>> SHIFT_TYPE) & MASK_TYPE);
        final int depth = (packedMeta >>> SHIFT_DEPTH) & MASK_DEPTH;

        final double evaluation = unpackEval16(evalQ[idx]);
        final EntryType type = decodeType(typeEncoded);

        return new TranspositionEntry(key, evaluation, depth, type, bestMove, storedGeneration);
    }

    /**
     * Stocke une entrée dans la table de transposition
     */
    public void store(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
    {
        storeInternal(key, evaluation, depthRemaining, type, bestMove);
    }

    /**
     * Stocke une entrée selon les règles de remplacement
     * @return true si stockée, sinon false
     */
    public boolean storeInternal(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
    {
        final int idx = ((int) key) & mask;

        final int existingMeta = meta[idx] & 0xFFFF;
        final int existingGeneration = (existingMeta >>> SHIFT_GEN) & MASK_GEN;
        final int existingDepth = (existingMeta >>> SHIFT_DEPTH) & MASK_DEPTH;

        final boolean empty = (existingGeneration == 0);

        final boolean sameKey;
        if (useLocks32)
        {
            final int lock = (int) (key >>> 32);
            sameKey = (!empty && locks[idx] == lock);
        }
        else
        {
            final long existingKey = keys[idx];
            sameKey = (!empty && existingKey == key);
        }

        final boolean older = (!empty && existingGeneration != generation);

        final int newDepth = clampDepth(depthRemaining);
        final boolean deeper = (newDepth >= existingDepth);

        if (!(empty || sameKey || older || deeper))
            return false;

        final int bm = clampMove(bestMove);
        final int t = encodeType(type) & MASK_TYPE;

        int packed = 0;
        packed |= (bm & MASK_MOVE) << SHIFT_MOVE;
        packed |= (t & MASK_TYPE) << SHIFT_TYPE;
        packed |= (newDepth & MASK_DEPTH) << SHIFT_DEPTH;
        packed |= (generation & MASK_GEN) << SHIFT_GEN;

        meta[idx] = (short) packed;
        evalQ[idx] = packEval16(evaluation);

        if (useLocks32)
            locks[idx] = (int) (key >>> 32);
        else
            keys[idx] = key;

        return true;
    }

    public int size()
    {
        return size;
    }

    public int pow2()
    {
        return pow2;
    }

    public boolean isUsingLocks32()
    {
        return useLocks32;
    }

    /**
     * Convertit un type en code binaire
     */
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
            		throw new IllegalStateException("Type invalide");
        }
    }

    /**
     * Convertit un code en type
     */
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
            		throw new IllegalStateException("Type invalide");
        }
    }

    /**
     * Clamp un coup à la plage [0..7] pour l'encodage dans 3 bits
     * @param m Le coup à clamp
     * @return Le coup clampé entre 0 et 7
     */
    private static int clampMove(int m)
    {
        if (m < 0)
        		return 0;
        if (m > 7)
        		return 7;
        return m;
    }

    /**
     * Clamp la profondeur à la plage [0..63] pour l'encodage dans 6 bits
     * @param d La profondeur à clamp
     * @return La profondeur clampée entre 0 et 63
     */
    private static int clampDepth(int d)
    {
        if (d < 0)
        		return 0;
        if (d > 63)
        		return 63;
        return d;
    }

    /**
     * Quantifie une évaluation double en un short sur 16 bits
     * @param eval L'évaluation à quantifier (double)
     * @return L'évaluation quantifiée (short)
     */
    private static short packEval16(double eval)
    {
        float x = (float) eval;
        
        if (x >  EMAX)
        		x = EMAX;
        if (x < -EMAX)
        		x = -EMAX;
        return (short)Math.round(x * 32767f / EMAX);
    }

    /**
     * Déquantifie une évaluation stockée sur 16 bits
     * @param q L'évaluation quantifiée (short)
     * @return L'évaluation déquantifiée (double)
     */
    private static double unpackEval16(short q)
    {
        return (q * (double)EMAX) / 32767.0;
    }

    
    
    
    
    
    
    public int count()
    {
        int c = 0;

        if (useLocks32)
        {
            for (int i = 0; i < size; i++)
            {
                if (meta[i] != 0)
                    c++;
            }
        }
        else
        {
            for (int i = 0; i < size; i++)
            {
                if (keys[i] != 0L)
                    c++;
            }
        }

        return c;
    }
}