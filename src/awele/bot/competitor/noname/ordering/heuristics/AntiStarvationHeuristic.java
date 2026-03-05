package awele.bot.competitor.noname.ordering.heuristics;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.ordering.MoveHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique anti-starvation
 * Détection si l'adversaire est affamé et vérifie si le coup le nourrit
 */
public final class AntiStarvationHeuristic implements MoveHeuristic
{
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
		
		return (finalPos >= NB_HOLES) ? 1 : 0;
	}
}
