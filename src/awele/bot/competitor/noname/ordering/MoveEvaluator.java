package awele.bot.competitor.noname.ordering;

import java.util.ArrayList;
import java.util.List;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.ordering.heuristics.AntiStarvationHeuristic;
import awele.bot.competitor.noname.ordering.heuristics.CaptureOrderingHeuristic;
import awele.bot.competitor.noname.ordering.heuristics.TTMoveHeuristic;

/**
 * @autor Lucas Fagioli
 * Évaluateur de coups qui combine plusieurs heuristiques pour donner une évaluation globale de la qualité d'un coup
 * Calcule ensuite un score pondéré pour chaque coup
 */
public final class MoveEvaluator
{
	// ===== Poids ======
	
	private static final int W_TT = 1_000_000; // Priorité absolue au coup du TT (0/1)
	private static final int W_STARVATION = 100_000; // (-1/0/1)
	private static final int W_CAPTURE = 100; // (NbGraines capturées)
	private static final int W_CATEGORY = 10; // (score cat)
	private static final int KILLER_BONUS_1 = 50_000; // Bonus pour le coup killer 1
	private static final int KILLER_BONUS_2 = 25_000; // Bonus pour le coup killer 2
	
	public static boolean ENABLE_CATEGORY_ORDERING = true;
	public static boolean ENABLE_KILLERS = true;
	
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
		final int tt = heuristics.get(0).evaluate(board, player, hole);
		final int starvation = heuristics.get(1).evaluate(board, player, hole);
		final int capture = heuristics.get(2).evaluate(board, player, hole);
		
		return (tt * W_TT) + (starvation * W_STARVATION) + (capture * W_CAPTURE);
	}
	
	/**
	 * Retourne les coups valides triés par ordre décroissant de qualité selon l'évaluation des heuristiques
	 * @param board L'état actuel du plateau de jeu
	 * @param player Le numéro du joueur pour lequel on évalue les coups (0 ou 1)
	 * @return Un tableau de numéros de trous (0 à 5) représentant les coups valides triés par ordre décroissant de qualité
	 */
	public int[] orderMoves(BitBoard board, int player, int depthRemaining)
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
		
		final int k0 = KillerMoves.get(depthRemaining, 0);
		final int k1 = KillerMoves.get(depthRemaining, 1);
		
		// On évalue chaque coup valide et on stocke les scores
		int index = 0;
		for (int hole = 0; hole < validMoves.length; hole++)
		{
			if (validMoves[hole])
			{
				moves[index] = hole;
				
				int score = evaluateMove(board, player, hole);
				
				if (ENABLE_CATEGORY_ORDERING)
				{
					final int cat = CategoryMoveOrdering.category(board, player, hole);
					score += CategoryMoveOrdering.score(cat) * W_CATEGORY;
				}
				
				if (ENABLE_KILLERS)
				{
					if (depthRemaining >= 2 && depthRemaining <= 12)
					{
						if (hole == k0)
							score += KILLER_BONUS_1;
						else if (hole == k1)
							score += KILLER_BONUS_2;
					}
				}
				
				scores[index] = score;
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
