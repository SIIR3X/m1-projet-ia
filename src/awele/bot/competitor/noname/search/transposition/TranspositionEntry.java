package awele.bot.competitor.noname.search.transposition;

/**
 * @author Lucas Fagioli
 * Classe représentant une entrée de la table de transposition
 */
public final class TranspositionEntry
{
	public final long key;
	
	public final double evaluation;
	
	public final int depth;
	
	public final EntryType type;
	
	public final int bestMove;
	
	public final int generation;
	
	public TranspositionEntry(long key, double evaluation, int depth, EntryType type, int bestMove, int generation)
	{
		this.key = key;
		this.evaluation = evaluation;
		this.depth = depth;
		this.type = type;
		this.bestMove = bestMove;
		this.generation = generation;
	}
}
