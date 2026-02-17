package awele.bot.competitor.noname.algorithms.heuristics;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @autor Lucas Fagioli
 * Heuristique basée sur le potentiel de capture
 * Simule rapidement le coup pour compter les graines capturées
 */
public final class CaptureHeuristic implements MoveHeuristic
{
	private static final int WEIGHT = 100;
	
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		// On récupère une copie du plateau de jeu pour simuler le coup
		final BitBoard copy = board.clone();
		
		final double[] decision = new double[NB_HOLES];
		decision[hole] = 1.0;
		
		// On simule le coup et on regarde combien de graines sont capturées
		final int captured = copy.playMove(decision);
		
		// Score = nombre de graines capturées
		// Si coup invalide (= capture négative), on considère que le score est 0
		return (captured > 0) ? captured : 0;
	}
	
	@Override
	public int getWeight()
	{
		return WEIGHT;
	}
}
