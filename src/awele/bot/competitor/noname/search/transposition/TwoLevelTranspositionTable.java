package awele.bot.competitor.noname.search.transposition;

/**
 * @author Lucas Fagioli
 * Table de transposition à deux niveaux
 * - primaryTable : plus grande
 * - secondaryTable : plus petite
 * 
 * probe() : cherche d'abord dans primaryTable, puis dans secondaryTable si pas trouvé
 * store() : tente de stocker dans primaryTable, si échec, stocke dans secondaryTable
 */
public final class TwoLevelTranspositionTable
{
	/**
	 * Table principale (plus grande)
	 */
	private final TranspositionTable primaryTable;
	
	/**
	 * Table secondaire (plus petite, pour les entrées plus récentes et plus pertinentes)
	 */
	private final TranspositionTable secondaryTable;
	
	/**
	 * Crée une table de transposition à deux niveaux
	 * @param primarySize Taille de la table principale (en nombre d'entrées)
	 * @param secondarySize Taille de la table secondaire (en nombre d'entrées)
	 * @param useLocks32 Indique si on utilise des locks 32-bit au lieu de clés 64-bit pour économiser la mémoire
	 */
	public TwoLevelTranspositionTable(int primarySize, int secondarySize, boolean useLocks32)
	{
		this.primaryTable = new TranspositionTable(primarySize, useLocks32);
		this.secondaryTable = new TranspositionTable(secondarySize, useLocks32);
	}
	
	public void incrementAge()
	{
		this.primaryTable.incrementAge();
		this.secondaryTable.incrementAge();
	}
	
	public void clear()
	{
		this.primaryTable.clear();
		this.secondaryTable.clear();
	}
	
	public TranspositionEntry probe(long key)
	{
		TranspositionEntry entry = primaryTable.probe(key);
		
		return (entry != null) ? entry : secondaryTable.probe(key);
	}
	
	public void store(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
	{
		if (!primaryTable.storeInternal(key, evaluation, depthRemaining, type, bestMove))
			secondaryTable.storeInternal(key, evaluation, depthRemaining, type, bestMove);
	}
	
	public TranspositionTable getPrimaryTable()
	{
		return primaryTable;
	}
	
	public TranspositionTable getSecondaryTable()
	{
		return secondaryTable;
	}
}
