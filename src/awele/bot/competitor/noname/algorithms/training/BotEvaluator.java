package awele.bot.competitor.noname.algorithms.training;

import awele.bot.competitor.noname.algorithms.heuristics.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.algorithms.transposition.TranspositionTable;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

public final class BotEvaluator
{
	/**
	 * JOAN_SALA : évaluation contre le bot Joan Sala (poids fixes)
	 * SELF_PLAY : évaluation en auto-jouant contre soi-même (poids variables)
	 */
	public enum OpponentProfile
	{
		JOAN_SALA,
		SELF_PLAY
	}
	
	// ===== État interne =====
	
	/**
	 * Profil adversaire à utiliser pour l'évaluation
	 */
	private final OpponentProfile profile;
	
	/**
	 * Évaluateur de position à utiliser pour l'entraînement
	 */
	private final PositionEvaluator evaluator;
	
	/**
	 * Table de transposition à utiliser pour l'entraînement
	 */
	private final TranspositionTable transpositionTable;
	
	/**
	 * Plateau de départ réutilisé
	 */
	private final BitBoard startBoard;
	
	// ===== Constantes =====
	
	private static final int NB_HOLES = BitConstants.NB_HOLES;
	private static final int MAX_MOVES_PER_GAME = 200;
	
	public BotEvaluator()
	{
		this(OpponentProfile.JOAN_SALA);
	}
	
	public BotEvaluator(OpponentProfile profile)
	{
		this.profile = profile;
		this.transpositionTable = new TranspositionTable();
		this.startBoard = new BitBoard();
		
		this.evaluator = (profile == OpponentProfile.JOAN_SALA)
			? new PositionEvaluator(PositionEvaluator.getDefautltWeights())
			: null;
	}
	
	/**
	 * Évalue  un jeu de poids en jouant N partie selon un profile
	 * @param weights Les poods à évaluer
	 * @param nbGames Nombre de parties à jouer pour l'évaluation
	 * @param depth Profondeur de recherche à utiliser pour les parties d'évaluation
	 * @return Le score moyen du bot candidat
	 */
	public double evaluate(double[] weights, int nbGames, int depth)
	{
		final PositionEvaluator candidateEvaluator = new PositionEvaluator(weights);
		
		final PositionEvaluator opponentEvaluator = (profile == OpponentProfile.SELF_PLAY)
			? candidateEvaluator
			: evaluator;
		
		double totalScore = 0.0;
		
		for (int game = 0; game < nbGames; game++)
		{
			final boolean candidateIsPlayer0 = (game % 2 == 0);
			
			final int result = playSingleGame(candidateEvaluator, opponentEvaluator, candidateIsPlayer0, depth);
			
			totalScore += result;
		}
		
		return totalScore / (double)nbGames;
	}
	
	/**
	 * Joue une partie complète entre deux évaluateurs
	 * @param candidateEvaluator Évaluateur de position du bot candidat
	 * @param opponentEvaluator Évaluateur de position de l'adversaire
	 * @param candidateIsPlayer0 Indique si le bot candidat joue en premier (joueur 0) ou en second (joueur 1)
	 * @param depth Profondeur de recherche à utiliser pour les deux joueurs pendant la partie
	 * @return 1 si le bot candidat gagne, -1 s'il perd, 0 en cas de match nul
	 */
	private int playSingleGame(
		PositionEvaluator candidateEvaluator,
		PositionEvaluator opponentEvaluator,
		boolean candidateIsPlayer0,
		int depth)
	{
		startBoard.initialize();
		BitBoard board = startBoard.clone();
		
		final TranspositionTable savedTT = BitMinMaxNode.transpositionTable;
		final PositionEvaluator savedEval = BitMinMaxNode.positionEvaluator;
		final boolean savedExpired = BitMinMaxNode.timeExpired;
		final long savedStart = BitMinMaxNode.searchStartTime;
		final long savedMax = BitMinMaxNode.maxSearchTime;
		
		BitMinMaxNode.transpositionTable = this.transpositionTable;
		BitMinMaxNode.timeExpired = false;
		BitMinMaxNode.searchStartTime = 0L;
		BitMinMaxNode.maxSearchTime = Long.MAX_VALUE;
		
		try
		{
			int movesPlayed = 0;
			
			while (!board.isGameOver() && movesPlayed < MAX_MOVES_PER_GAME)
			{
				final int currentPlayer = board.getCurrentPlayer();
				
				final boolean isCandidateTurn =
					(candidateIsPlayer0 && currentPlayer == 0) ||
					(!candidateIsPlayer0 && currentPlayer == 1);
				
				final PositionEvaluator currentEval = isCandidateTurn
					? candidateEvaluator
					: opponentEvaluator;
				
				final double[] decision = getBestDecision(board, currentEval, depth);
				board.playMove(decision);
				
				movesPlayed++;
			}
			
			final int winner = board.getWinner();
			
			if (winner == -1)
				return 0;
			
			final int candidatePlayer = candidateIsPlayer0 ? 0 : 1;
			
			return (winner == candidatePlayer) ? 1 : -1;
		}
		finally
		{
			BitMinMaxNode.transpositionTable = savedTT;
			BitMinMaxNode.positionEvaluator = savedEval;
			BitMinMaxNode.timeExpired = savedExpired;
			BitMinMaxNode.searchStartTime = savedStart;
			BitMinMaxNode.maxSearchTime = savedMax;
		}
	}
	
	/**
	 * Calcule le meilleur pour un évaluateur donné et une profondeur donnée
	 * @param board Plateau pour lequel on doit prendre une décision
	 * @param evaluator Évaluateur de position à utiliser pour la recherche
	 * @param depth Profondeur de recherche à utiliser pour la recherche
	 * @return Un tableau de probabilités de jouer chaque trou, avec des valeurs entre 0.0 et 1.0, et une somme totale de 1.0
	 */
	private double[] getBestDecision(BitBoard board, PositionEvaluator evaluator, int depth)
	{
		BitMinMaxNode.positionEvaluator = evaluator;
		
		BitMinMaxNode.initialize(board, depth);
		
		final BitMaxNode rootNode = new BitMaxNode(board);
		
		if (rootNode.isInterrupted())
			return getFallbackDecision(board);
		
		return rootNode.getDecision();
	}
	
	/**
	 * Retourne une décision de repli
	 * @param board Plateau pour lequel on doit prendre une décision
	 * @return Un tableau de probabilités de jouer chaque trou, avec 1.0 pour le premier trou valide trouvé, et 0.0 pour les autres
	 */
	private double[] getFallbackDecision(BitBoard board)
	{
		final int player = board.getCurrentPlayer();
		final boolean[] validMoves = board.getValidMoves(player);
		final double[] decision = new double[NB_HOLES];
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			if (validMoves[i])
			{
				decision[i] = 1.0;
				return decision;
			}
		}
		
		return decision;
	}
}
