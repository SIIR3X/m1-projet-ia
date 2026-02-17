package awele.bot.competitor.noname.algorithms.transposition;

public final class TranspositionTable
{
	/**
	 * Taille de la table
	 * Environ 32MB
	 */
	private static final int TABLE_SIZE = 1_000_000;
	
	/**
	 * Table principale
	 */
	private final TranspositionEntry[] table;
	
	/**
	 * Âge actuel de la table
	 */
	private int currentAge;
	
	public TranspositionTable()
	{
		this.table = new TranspositionEntry[TABLE_SIZE];
		this.currentAge = 0;
	}
	
	/**
	 * Recherche l'index dans la table à partir du hash
	 * @param hash Le hash Zobrist de la position
	 * @return L'index dans la table
	 */
	public TranspositionEntry probe(long hash)
	{
		int index = getIndex(hash);
		TranspositionEntry entry = table[index];
		
		// Si l'entrée correspond au hash demandé, on la retourne, 
		// sinon on considère qu'il n'y a pas d'entrée pour ce hash
		if (entry != null && entry.zobristHash == hash)
			return entry;

		return null;
	}
	
	/**
	 * Stocke une position dans la table
	 * @param hash Le hash Zobrist de la position
	 * @param evaluation L'évaluation de la position
	 * @param depth La profondeur à laquelle l'évaluation a été calculée
	 * @param type Le type d'évaluation (exacte, borne inférieure ou borne supérieure)
	 * @param bestMove Le meilleur coup trouvé pour cette position
	 */
	public void store(long hash, double evaluation, int depth, EntryType type, int bestMove)
	{
		int index = getIndex(hash);
		TranspositionEntry existing = table[index];
		
		// Pour savoir si on doit remplacer :
		// 1. La case est vide
		// 2. L'entrée existant est obsolète (age > 1)
		// 3. La nouvelle évaluation est plus profonde
		boolean shouldReplace = (existing == null) ||
								(existing.age < currentAge - 1) ||
								(depth >= existing.depth);
		
		if (shouldReplace)
			table[index] = new TranspositionEntry(hash, evaluation, depth, type, bestMove, currentAge);
	}
	
	public void incrementAge()
	{
		currentAge++;
	}
	
	public void clear()
	{
		for (int i = 0; i < TABLE_SIZE; i++)
			table[i] = null;
		
		currentAge = 0;
	}
	
	private int getIndex(long hash)
	{
		return (int)((hash & Long.MAX_VALUE) % TABLE_SIZE);
	}
	
	public double getOccupancyRate()
	{
		int occupied = 0;
		for (TranspositionEntry entry : table)
		{
			if (entry != null)
				occupied++;
		}
		
		return (double)occupied / TABLE_SIZE;
	}
}
