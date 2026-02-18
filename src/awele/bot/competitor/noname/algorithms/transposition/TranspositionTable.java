package awele.bot.competitor.noname.algorithms.transposition;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 * Classe représentant une table de transposition pour un moteur de jeu
 */
public final class TranspositionTable
{
	private static final int POW2 = 20; // 2^20 entrées
	private static final int SIZE = 1 << POW2;
	private static final int MASK = SIZE - 1;
	
	private final long[] keys = new long[SIZE];
	private final double[] evaluations = new double[SIZE];
	private final int[] depths = new int[SIZE];
	private final byte[] types = new byte[SIZE];
	private final byte[] bestMoves = new byte[SIZE];
	private final int[] generations = new int[SIZE];
	
	private int generation = 1;
	
	private static final byte T_EXACT = 0;
	private static final byte T_LOWER = 1;
	private static final byte T_UPPER = 2;
	
	public TranspositionTable()
	{
		Arrays.fill(bestMoves, (byte)-1);
	}
	
	/**
	 * Incrémente la génération
	 */
	public void incrementAge()
	{
		generation++;
		
		if (generation == Integer.MAX_VALUE)
		{
			// Pour éviter overflow
			generation = 1;
			Arrays.fill(generations, 0);
			Arrays.fill(bestMoves, (byte)-1);
		}
	}
	
	/**
	 * Clear la table de transposition
	 */
	public void clear()
	{
		Arrays.fill(generations, 0);
		Arrays.fill(bestMoves, (byte)-1);
		generation = 1;
	}
	
	/**
	 * Probe la table de transposition pour une clé donnée
	 * @param key la clé à chercher
	 * @return une entrée de la table de transposition si la clé est trouvée, sinon null
	 */
	public TranspositionEntry probe(long key)
	{
		final int idx = ((int)key) & MASK;
		
		if (generations[idx] != 0 && keys[idx] == key)
		{
			final EntryType type = decodeType(types[idx]);
			final int bm = bestMoves[idx];
			
			return new TranspositionEntry(key, evaluations[idx], depths[idx], type, bm, generations[idx]);
		}
		
		return null;
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
		
		final int slotGeneration = generations[idx];
		final long slotKey = keys[idx];
		final int slotDepth = depths[idx];
		
		final boolean empty = (slotGeneration == 0);
		final boolean sameKey = (!empty && slotKey == key);
		final boolean older = (!empty && slotGeneration != generation);
		final boolean deeper = (depthRemaining >= slotDepth);
		
		// Si une condition on rempalce
		if (empty || sameKey || older || deeper)
		{
			keys[idx] = key;
			evaluations[idx] = evaluation;
			depths[idx] = depthRemaining;
			types[idx] = encodeType(type);
			bestMoves[idx] = (byte)bestMove;
			generations[idx] = generation;
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
