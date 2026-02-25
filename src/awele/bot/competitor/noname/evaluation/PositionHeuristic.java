package awele.bot.competitor.noname.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Interface pour les heuristiques d'évaluation de position
 */
public interface PositionHeuristic
{
	/**
	 * Évalue une composante de la position pour un joueur donné
	 * @param board le plateau de jeu
	 * @param player le joueur pour lequel on évalue la position
	 * @return une évaluation de la position pour le joueur donné, plus élevée est meilleure pour le joueur
	 */
	double evaluate(BitBoard board, int player);

	/**
	 * Identifiant unique pour sérialisation des poids
	 * @return L'identifiant de cette heuristique
	 */
	String getId();
}
