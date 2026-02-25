package awele.bot.competitor.noname.search.transposition;

/**
 * @author Lucas Fagioli
 * Table de transposition ultra-compacte
 * Utilise seulement 2 tableaux pour minimiser la mémoire
 */
public final class TranspositionTable
{
	// ===== Configuration de la table =====
	
	/**
	 * Puissance de 2 pour la taille de la table
	 */
	private static final int POW2 = 21;
	
	/**
	 * Taille de la table
	 */
	private static final int SIZE = 1 << POW2;
	
	/**
	 * Masque pour calculer l'index à partir de la clé
	 */
	private static final int MASK = SIZE - 1;
	
	// ===== Tables =====
	
	/**
	 * Tableau des clés (64 bits chacune)
	 */
	private final long[] keys;
	
	/**
	 * Tableau des données encodées (64 bits chacune)
	 */
	private final long[] data;
	
	/**
	 * Génération actuelle
	 */
	private int generation;
	
	// ===== Constantes (pour l'encodage) =====
	
	private static final byte T_EXACT = 0;
	private static final byte T_LOWER = 1;
	private static final byte T_UPPER = 2;
	
	// ===== Masques =====
	
    private static final long MASK_BEST_MOVE = 0xFFL;
    private static final long MASK_TYPE = 0xFF00L;
    private static final long MASK_GENERATION = 0xFF0000L;
    private static final long MASK_DEPTH = 0xFF000000L;
	
    // ===== Offsets pour l'extraction =====
    
    private static final int OFFSET_BEST_MOVE = 0;
    private static final int OFFSET_TYPE = 8;
    private static final int OFFSET_GENERATION = 16;
    private static final int OFFSET_DEPTH = 24;
    private static final int OFFSET_EVALUATION = 32;

    public TranspositionTable()
	{
		this.keys = new long[SIZE];
		this.data = new long[SIZE];
		this.generation = 1;
	}
    
    /**
     * Incrémente la génération de la table de transposition
     */
    public void incrementAge()
    {
        generation++;
        
        // Réinitialisation après 255 (overflow)
        if (generation > 255)
            generation = 1;
    }
    
    /**
     * Clear la table de transposition
     */
    public void clear()
    {
        generation = 1;
        java.util.Arrays.fill(keys, 0L);
        java.util.Arrays.fill(data, 0L);
    }
    
    /**
     * Probe la table de transposition pour une clé donnée
     * @param key la clé à chercher
     * @return une entrée de la table de transposition si la clé est trouvée, sinon null
     */
    public TranspositionEntry probe(long key)
    {
        final int idx = ((int)key) & MASK;
        
        // On vérifie si la clé est présente à l'index calculé
        final long storedKey = keys[idx];
        if (storedKey == 0 || storedKey != key)
            return null;
        
        final long packedData = data[idx];
        
        // Extraction de la génération
        final int storedGeneration = (int)((packedData & MASK_GENERATION) >>> OFFSET_GENERATION);
        
        // On vérifie que l'entrée est valide
        if (storedGeneration == 0)
            return null;
        
        // On extrait les données
        final int bestMove = (int)((packedData & MASK_BEST_MOVE) >>> OFFSET_BEST_MOVE);
        final byte typeEncoded = (byte)((packedData & MASK_TYPE) >>> OFFSET_TYPE);
        final int depth = (int)((packedData & MASK_DEPTH) >>> OFFSET_DEPTH);
        
        // On décode l'évaluation (conversion float 32 bits vers double)
        final int evalBits = (int)(packedData >>> OFFSET_EVALUATION);
        final double evaluation = Float.intBitsToFloat(evalBits);
        
        final EntryType type = decodeType(typeEncoded);
        
        return new TranspositionEntry(key, evaluation, depth, type, bestMove, storedGeneration);
    }
    
    /**
     * Stocke une entrée dans la table de transposition
     * @param key la clé de l'entrée à stocker
     * @param evaluation l'évaluation de l'entrée à stocker
     * @param depthRemaining la profondeur restante de l'entrée à stocker
     * @param type le type d'entrée à stocker
     * @param bestMove le meilleur coup de l'entrée à stocker
     */
    public void store(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
    {
        final int idx = ((int)key) & MASK;
        
        final long existingKey = keys[idx];
        final long existingData = data[idx];
        
        final int existingGeneration = (int)((existingData & MASK_GENERATION) >>> OFFSET_GENERATION);
        final int existingDepth = (int)((existingData & MASK_DEPTH) >>> OFFSET_DEPTH);
        
        // Conditions de remplacement
        final boolean empty = (existingKey == 0 || existingGeneration == 0);
        final boolean sameKey = (!empty && existingKey == key);
        final boolean older = (!empty && existingGeneration != generation);
        final boolean deeper = (depthRemaining >= existingDepth);
        
        if (empty || sameKey || older || deeper)
        {
            // Encodage de l'évaluation en float	32 bits
            final int evalBits = Float.floatToIntBits((float)evaluation);
            
            // Construction du long encodé avec toutes les données
            long packedData = 0L;
            packedData |= ((long)(bestMove & 0xFF)) << OFFSET_BEST_MOVE;
            packedData |= ((long)(encodeType(type) & 0xFF)) << OFFSET_TYPE;
            packedData |= ((long)(generation & 0xFF)) << OFFSET_GENERATION;
            packedData |= ((long)(depthRemaining & 0xFF)) << OFFSET_DEPTH;
            packedData |= ((long)evalBits) << OFFSET_EVALUATION;
            
            keys[idx] = key;
            data[idx] = packedData;
        }
    }
    
    /**
     * Convertit un type d'entrée en un bit pour le stocker dans la table de transposition
     * @param type le type d'entrée à convertir
     * @return le bit correspondant au type d'entrée
     */
    private static byte encodeType(EntryType type)
    {
        switch (type)
        {
            case EXACT: return T_EXACT;
            case LOWER_BOUND: return T_LOWER;
            case UPPER_BOUND: return T_UPPER;
            default: throw new IllegalStateException("Invalid entry type");
        }
    }
    
    /**
     * Convertit un bit en une entrée de la table de transposition
     * @param t le bit à convertir
     * @return le type d'entrée correspondant
     */
    private static EntryType decodeType(byte t)
    {
        switch (t)
        {
            case T_EXACT: return EntryType.EXACT;
            case T_LOWER: return EntryType.LOWER_BOUND;
            case T_UPPER: return EntryType.UPPER_BOUND;
            default: throw new IllegalStateException("Invalid entry type");
        }
    }
}