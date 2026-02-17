package awele.bot.competitor.noname.algorithms.killers;

/**
 * @author Lucas Fagioli
 * Table des killers moves pour otpimiser l'élagage alpha-beta
 * Killer move = un coup qui a causé une coupe alpha-beta à une profondeur donnée
 * On suppose ici qu'ils seront bon à ailleurs à la même profondeur
 */
public final class KillerMoveTable
{
	/**
	 * Profondeur maximale pour laquelle on stocke des coups killers
	 */
	private static final int MAX_DEPTH = 14;
	
	/**
	 * Nombre de coups killers stockés par profondeur
	 */
	private static final int KILLERS_PER_DEPTH = 2;
	
	/**
	 * Table de coups killers
	 * - killerMoves[depth][0] : le meilleur coup killer pour la profondeur depth
	 * - killerMoves[depth][1] : le deuxième meilleur coup killer pour la profondeur depth
	 */
	private static final int[][] killerMoves = new int[MAX_DEPTH][KILLERS_PER_DEPTH];
	
	// Bonus d'évaluation pour les coups killers
	private static final int FIRST_KILLER_BONUS = 10000;
	private static final int SECOND_KILLER_BONUS = 25000;
	
	// Empêche l'instanciation
	private KillerMoveTable()
	{
		throw new AssertionError("Classe utilitaire, ne doit pas être instanciée");
	}
	
	/**
	 * Réinitialise la table des coups killers
	 * IMPORTANT appeler au début de chaque nouvelle recherche
	 */
	public static void reset()
	{
		for (int depth = 0; depth < MAX_DEPTH; depth++)
		{
			killerMoves[depth][0] = -1;
			killerMoves[depth][1] = -1;
		}
	}
	
	/**
	 * Enregistre un killer move
	 * @param depth la profondeur à laquelle le coup killer a été trouvé
	 * @param move le coup killer à enregistrer (index du trou joué)
	 */
	public static void store(int depth, int move)
	{
		// On vérifie que la profondeur est valide
		if (depth < 0 || depth >= MAX_DEPTH)
			return;
		
		// Si jamais c'est déjà le premier killer, on fait rien
		if (killerMoves[depth][0] == move)
			return;
		
		// Si jamais c'est le 2ème killer move, on le remonte en 1er
		if (killerMoves[depth][1] == move)
		{
			killerMoves[depth][1] = killerMoves[depth][0];
			killerMoves[depth][0] = move;
			
			return;
		}
		
		// Nouveau killer move: on le déclare et insère
		killerMoves[depth][1] = killerMoves[depth][0];
		killerMoves[depth][0] = move;
	}
	
	/**
	 * Retourne le bonus associé à un coup selon la table des killers
	 * @param depth la profondeur à laquelle on évalue le coup
	 * @param move le coup à évaluer (index du trou joué)
	 * @return le bonus d'évaluation à ajouter pour ce coup (0 si ce n'est pas un killer, sinon FIRST_KILLER_BONUS ou SECOND_KILLER_BONUS)
	 */
	public static int getBonus(int depth, int move)
	{
		// On vérifie que la profondeur est valide
		if (depth < 0 || depth >= MAX_DEPTH)
			return 0;
		
		// Check du 1er killer move
		if (killerMoves[depth][0] == move)
			return FIRST_KILLER_BONUS;
		
		// Check du 2ème killer move
		if (killerMoves[depth][1] == move)
			return SECOND_KILLER_BONUS;
		
		// Pas un killer
		return 0;
	}
}
