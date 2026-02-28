package awele.bot.competitor.noname.search.minmax;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Noeud Min : estimation du meilleur coup possible pour l'adversaire
 */
public final class NNMinNode extends NNMinMaxNode
{
	/**
	 * Constructeur pour un noeud initial (racine)
	 * @param board La situation de jeu pour laquelle il faut prendre une décision
	 */
	public NNMinNode(BitBoard board)
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
	public NNMinNode(BitBoard board, int depth, double alpha, double beta)
	{
		super(board, depth, alpha, beta);
	}

	@Override
	protected double getWorstScore()
	{
		return Double.MAX_VALUE;
	}

	@Override
	protected double updateEvaluation(double newEval, double currentEval)
	{
		return Math.min(newEval, currentEval);
	}

	@Override
	protected double updateAlpha(double evaluation, double alpha)
	{
		return alpha;
	}

	@Override
	protected double updateBeta(double evaluation, double beta)
	{
		return Math.min(evaluation, beta);
	}

	@Override
	protected boolean shouldPrune(double evaluation, double alpha, double beta)
	{
		return evaluation <= alpha;
	}

	@Override
	protected NNMinMaxNode createNextNode(BitBoard board, int depth, double alpha, double beta)
	{
		return new NNMaxNode(board, depth, alpha, beta);
	}


	
}
