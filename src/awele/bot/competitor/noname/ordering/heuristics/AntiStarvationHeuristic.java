package awele.bot.competitor.noname.ordering.heuristics;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.ordering.MoveHeuristic;

/**
 * @autor Lucas Fagioli
 * Heuristique anti-starvation
 * Détection si l'adversaire est affamé et vérifie si le coup le nourrit
 */
public final class AntiStarvationHeuristic implements MoveHeuristic
{
	private static final int FEEDS_BONUS = 10_000;
	private static final int STARVES_PENALTY = -100_000;

	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		final int opponent = 1 - player;
		
		// On vérifie si l'adversaire est affamé
		if (board.getTotalSeeds(opponent) > 0)
			return 0; // Pas affamé = pas bonus/pénalité
		
		// L'adversaire est affamé, on DOIT le nourrir pour éviter de perdre
		final int seeds = board.getSeeds(player, hole);
		final int finalPos = hole + seeds;
		
		// Le coup nourrit si les graines atteignent le côté adverse
		if (finalPos >= NB_HOLES)
			return FEEDS_BONUS;
		else
			return STARVES_PENALTY;
	}
}
