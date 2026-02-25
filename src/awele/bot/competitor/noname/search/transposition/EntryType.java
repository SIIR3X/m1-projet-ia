package awele.bot.competitor.noname.search.transposition;

/**
 * @author Lucas Fagioli
 * Type d'entrée TT pour AlphaBeta
 */
public enum EntryType
{
	/**
	 * Exact : l'évaluation est exacte, c'est à dire que la valeur retournée par la recherche est exactement égale à l'évaluation stockée dans la table de transposition
	 */
	EXACT,
	
	/**
	 * Lower bound : l'évaluation est une borne inférieure, c'est à dire que la valeur retournée par la recherche est supérieure ou égale à l'évaluation stock
	 */
	LOWER_BOUND,
	
	/**
	 * Upper bound : l'évaluation est une borne supérieure, c'est à dire que la valeur retournée par la recherche est inférieure ou égale à l'évaluation stock
	 */
	UPPER_BOUND
}
