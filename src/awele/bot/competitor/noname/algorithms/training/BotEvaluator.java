package awele.bot.competitor.noname.algorithms.training;

import java.util.Random;

import awele.bot.competitor.noname.algorithms.heuristics.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.algorithms.transposition.TranspositionTable;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

public final class BotEvaluator
{
	// ===== État interne =====
	
	/**
	 * Sélecteur adaptatif de profils d'adversaires pour l'entraînement
	 */
	private final AdaptiveOpponentSelector selector;
	
	private final TranspositionTable candidateTT;
	private final TranspositionTable opponentTT;
	
	/**
	 * Plateau de départ réutilisé
	 */
	private final BitBoard startBoard;
	
	// ===== Constantes =====
	
	private static final int NB_HOLES = BitConstants.NB_HOLES;
	private static final int MAX_MOVES_PER_GAME = 200;
	
	
	private final Random rng = new Random(0xC0FFEE);
	private static final int RANDOM_OPENING_PLIES = 6; // 4 à 10 typiquement
	private static final double RANDOM_MOVE_EPS = 1e-9;
	
	
	public BotEvaluator()
	{
		this.selector = new AdaptiveOpponentSelector();
		this.candidateTT = new TranspositionTable();
		this.opponentTT = new TranspositionTable();
		this.startBoard = new BitBoard();
	}
	
	/**
	 * Évalue  un jeu de poids en jouant N partie selon un profile
	 * @param weights Les poods à évaluer
	 * @param nbGames Nombre de parties à jouer pour l'évaluation
	 * @param depth Profondeur de recherche à utiliser pour les parties d'évaluation
	 * @return Le score moyen du bot candidat
	 */
	public double evaluate(double[] weights, int nbGames, int depth, AdaptiveOpponentSelector.OpponentProfile profile)
	{
		final PositionEvaluator candidateEvaluator = new PositionEvaluator(weights);
		final PositionEvaluator opponentEvaluator = selector.getEvaluator(profile);
		double totalScore = 0.0;
		
		for (int game = 0; game < nbGames; game++)
		{
			final boolean candidateIsPlayer0 = (game % 2 == 0);
			final int result = playSingleGame(candidateEvaluator, opponentEvaluator, candidateIsPlayer0, depth);
			totalScore += result;
		}
		
		selector.reportResult(profile, totalScore > 0 ? 1 : (totalScore < 0 ? -1 : 0));
		
		return totalScore / (double)nbGames;
	}
	
	public AdaptiveOpponentSelector.OpponentProfile sampleProfile(double[] candidateWeights)
	{
		return selector.selectProfile(candidateWeights);
	}
	
	private void playRandomOpening(BitBoard board, int plies)
	{
	    for (int p = 0; p < plies && !board.isGameOver(); p++)
	    {
	        int player = board.getCurrentPlayer();
	        boolean[] valid = board.getValidMoves(player);

	        int count = 0;
	        for (boolean v : valid) if (v) count++;
	        if (count == 0) return;

	        int pick = rng.nextInt(count);
	        int move = -1;
	        for (int i = 0; i < valid.length; i++)
	        {
	            if (!valid[i]) continue;
	            if (pick-- == 0) { move = i; break; }
	        }

	        double[] decision = new double[NB_HOLES];
	        if (move >= 0) decision[move] = 1.0;
	        board.playMove(decision);
	    }
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
		candidateTT.clear();
		opponentTT.clear();
		playRandomOpening(board, RANDOM_OPENING_PLIES);
		
		final TranspositionTable savedTT = BitMinMaxNode.transpositionTable;
		final PositionEvaluator savedEval = BitMinMaxNode.positionEvaluator;
		final boolean savedExpired = BitMinMaxNode.timeExpired;
		final long savedStart = BitMinMaxNode.searchStartTime;
		final long savedMax = BitMinMaxNode.maxSearchTime;

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
				
				BitMinMaxNode.transpositionTable = isCandidateTurn ? candidateTT : opponentTT;
				
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
	
	public void updateBestWeights(double[] weights)
	{
	    selector.updateBestWeights(weights);
	}
}
