package awele.bot.competitor.noname.ordering;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Interface pour les heuristiques d'évaluation de coups
 */
public interface MoveHeuristic
{
	/**
	 * Évalue la qualité d'un coup
	 * @param board L'état actuel du plateau de jeu
	 * @param player Le numéro du joueur pour lequel on évalue le coup (0 ou 1)
	 * @param hole Le numéro du trou (0 à 5) pour lequel on évalue le coup
	 * @return Une évaluation numérique de la qualité du coup (plus c'est élevé, mieux c'est pour le joueur)
	 */
	int evaluate(BitBoard board, int player, int hole);
}
