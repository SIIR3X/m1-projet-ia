package awele.bot.competitor.noname.algorithms.heuristics.ordering;

import java.util.ArrayList;
import java.util.List;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @autor Lucas Fagioli
 * Évaluateur de coups qui combine plusieurs heuristiques pour donner une évaluation globale de la qualité d'un coup
 * Calcule ensuite un score pondéré pour chaque coup
 */
public final class MoveEvaluator
{
	/**
	 * Liste des heuristiques utilisées pour évaluer les coups
	 */
	private final List<MoveHeuristic> heuristics;

	public MoveEvaluator(int ttBestMove)
	{
		this.heuristics = new ArrayList<>();
		
		// Enregistrement des heuristiques utilisées pour évaluer les coups
		this.heuristics.add(new TTMoveHeuristic(ttBestMove));
		this.heuristics.add(new AntiStarvationHeuristic());
		this.heuristics.add(new CaptureOrderingHeuristic());
	}
	
	/**
	 * Évalue la qualité d'un coup en appliquant toutes les heuristiques et en pondérant leurs scores
	 * @param board L'état actuel du plateau de jeu
	 * @param player Le numéro du joueur pour lequel on évalue le coup (0 ou 1)
	 * @param hole Le numéro du trou (0 à 5) pour lequel on évalue le coup
	 * @return Une évaluation numérique de la qualité du coup (plus c'est élevé, mieux c'est pour le joueur)
	 */
	public int evaluateMove(BitBoard board, int player, int hole)
	{
		int totalScore = 0;
		
		// On applique chaque heuristique et on additionne les scores pondérés
		for (MoveHeuristic heuristic : this.heuristics)
		{
			int score = heuristic.evaluate(board, player, hole);
			totalScore += score * heuristic.getWeight();
		}

		return totalScore;
	}
	
	/**
	 * Retourne les coups valides triés par ordre décroissant de qualité selon l'évaluation des heuristiques
	 * @param board L'état actuel du plateau de jeu
	 * @param player Le numéro du joueur pour lequel on évalue les coups (0 ou 1)
	 * @return Un tableau de numéros de trous (0 à 5) représentant les coups valides triés par ordre décroissant de qualité
	 */
	public int[] orderMoves(BitBoard board, int player)
	{
		final boolean[] validMoves = board.getValidMoves(player);
		
		// On compte le nombre de coups valides pour dimensionner le tableau de scores
		// pas le choix car limitation mémoire
		int validCount = 0;
		for (boolean valid : validMoves)
			if (valid)
				validCount++;
		
		// Allocation exacte
		final int[] moves = new int[validCount];
		final int[] scores = new int[validCount];
		
		// On évalue chaque coup valide et on stocke les scores
		int index = 0;
		for (int hole = 0; hole < validMoves.length; hole++)
		{
			if (validMoves[hole])
			{
				moves[index] = hole;
				scores[index] = evaluateMove(board, player, hole);
				index++;
			}
		}
		
		// On tri les coups par ordre décroissant de score
		for (int i = 1; i < validCount; i++)
		{
			final int keyMove = moves[i];
			final int keyScore = scores[i];
			int j = i - 1;
			
			while (j >= 0 && scores[j] < keyScore)
			{
				moves[j + 1] = moves[j];
				scores[j + 1] = scores[j];
				j--;
			}
			
			moves[j + 1] = keyMove;
			scores[j + 1] = keyScore;
		}
		
		return moves;
	}
}
