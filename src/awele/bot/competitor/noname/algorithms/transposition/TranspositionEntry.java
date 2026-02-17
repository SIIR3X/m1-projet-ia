package awele.bot.competitor.noname.algorithms.transposition;

/**
 * @autor Lucas Fagioli
 * Entrée dans la table de transposition
 */
public final class TranspositionEntry
{
	/**
	 * Hash Zobrist complet de la position
	 */
	public final long zobristHash;
	
	/**
	 * Évaluation de la position
	 */
	public final double evaluation;
	
	/**
	 * Profondeur à laquelle l'évaluation a été calculée
	 */
	public final int depth;
	
	/**
	 * Type d'évaluation (exacte, borne inférieure ou borne supérieure)
	 */
	public final EntryType type;
	
	/**
	 * Meilleur coup trouvé
	 */
	public final int bestMove;
	
	/**
	 * Âge de l'entrée (nombre de recherches depuis sa création ou sa dernière mise à jour)
	 */
	public final int age;
	
	public TranspositionEntry(
		long zobristHash,
		double evaluation,
		int depth,
		EntryType type,
		int bestMove,
		int age)
	{
		this.zobristHash = zobristHash;
		this.evaluation = evaluation;
		this.depth = depth;
		this.type = type;
		this.bestMove = bestMove;
		this.age = age;
	}
}
