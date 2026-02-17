package awele.bot.competitor.noname.algorithms.transposition;

/**
 * @author Lucas Fagioli
 * Type d'entrée dans la table de transposition
 */
public enum EntryType
{
	/**
	 * Évaluation exacte : tous les coups ont été exploré complètement
	 */
	EXACT,
	
	/**
	 * Borne inférieure : une coupe beta s'est produite
	 */
	LOWER_BOUND,
	
	/**
	 * Borne supérieure : une coupe alpha s'est produite
	 */
	UPPER_BOUND
}
