package awele.bot.competitor.noname.search.minmax;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Noeud max : estimation du meilleur coup possible pour le joueur actuel
 */
public final class NNMaxNode extends NNMinMaxNode
{
	/**
	 * Constructeur pour un noeud initial (racine)
	 * @param board La situation de jeu pour laquelle il faut prendre une décision
	 */
	public NNMaxNode(BitBoard board)
	{
		this(board, 0, -Double.MAX_VALUE, Double.MAX_VALUE);
	}
	
	/**
	 * Constructeur pour un noeud interne
	 * @param board La situation de jeu pour laquelle il faut prendre une décision
	 * @param depth La profondeur du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 */
	public NNMaxNode(BitBoard board, int depth, double alpha, double beta)
	{
		super(board, depth, alpha, beta);
	}

	@Override
	protected double getWorstScore()
	{
		return -Double.MAX_VALUE;
	}

	@Override
	protected double updateEvaluation(double newEval, double currentEval)
	{
		return Math.max(newEval, currentEval);
	}

	@Override
	protected double updateAlpha(double evaluation, double alpha)
	{
		return Math.max(evaluation, alpha);
	}

	@Override
	protected double updateBeta(double evaluation, double beta)
	{
		return beta;
	}

	@Override
	protected boolean shouldPrune(double evaluation, double alpha, double beta)
	{
		return evaluation >= beta;
	}

	@Override
	protected NNMinMaxNode createNextNode(BitBoard board, int depth, double alpha, double beta)
	{
		return new NNMinNode(board, depth, alpha, beta);
	}
	
}
