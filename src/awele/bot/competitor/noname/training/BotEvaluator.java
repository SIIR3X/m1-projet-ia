package awele.bot.competitor.noname.training;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import java.util.Random;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.search.minmax.BitMaxNode;
import awele.bot.competitor.noname.search.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.search.transposition.TwoLevelTranspositionTable;

public final class BotEvaluator
{
	// ===== État interne =====
	
	/**
	 * Sélecteur adaptatif de profils d'adversaires pour l'entraînement
	 */
	private final AdaptiveOpponentSelector selector;
	
	/**
	 * Table de transposition du bot candidat
	 */
	private final TwoLevelTranspositionTable candidateTT;
	
	/**
	 * Table de transposition de l'adversaire
	 */
	private final TwoLevelTranspositionTable opponentTT;
	
	/**
	 * Générateur de nombres aléatoires pour les ouvertures aléatoires
	 */
	private final Random random = new Random(0xFFDABB);
	
	/**
	 * Plateau de départ réutilisé
	 */
	private final BitBoard startBoard;
	
	// ===== Constantes =====
	
	/**
	 * Nombre max de coup à jour dans une partie
	 */
	private static final int MAX_MOVES_PER_GAME = 200;
	
	/**
	 * Nombre de coups d'ouverture aléatoires à jouer avant de commencer la recherche
	 */
	private static final int RANDOM_OPENING_PLIES = 6;
	
	/**
	 * Points attribués pour une victoire (inspiré de Saillot)
	 */
	private static final double WIN_BONUS  = 200.0;

	/**
	 * Points attribués pour un match nul
	 */
	private static final double DRAW_BONUS = 100.0;
	
	/**
	 * Nombre de partie contre le profil de boss pour l'évaluation
	 */
	private static final int NB_BOSSE_GAMES = 4;
	
	public BotEvaluator()
	{
		this.selector = new AdaptiveOpponentSelector();
	    this.candidateTT = new TwoLevelTranspositionTable(22, 18, true);
	    this.opponentTT = new TwoLevelTranspositionTable(22, 18, true);
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
		double totalFitness = 0.0;
		double totalResult = 0.0;
		
		// Parties contre le profil normal
		final PositionEvaluator opponentEvaluator = selector.getEvaluator(profile);
		final double normalMultiplier = selector.getMultiplier(profile);
		final int normalOpponentDepth = resolveDepth(profile, depth);
		
		for (int game = 0; game < nbGames; game++)
		{
			final boolean candidateFirst = (game % 2 == 0);
			final double gameFitness = playSingleGame(
				candidateEvaluator,
				opponentEvaluator,
				candidateFirst,
				depth,
				normalOpponentDepth) * normalMultiplier;
			
			totalFitness += gameFitness;
			totalResult += outcomeSign(gameFitness, normalMultiplier);
		}
		
		selector.reportResult(profile, totalResult > 0 ? 1 : (totalResult < 0 ? -1 : 0));
		
		// Partie contre le BOSS
		final AdaptiveOpponentSelector.OpponentProfile bossProfile = AdaptiveOpponentSelector.OpponentProfile.BOSS;
		
		final PositionEvaluator bossEvaluator = selector.getEvaluator(bossProfile);
		final int bossDepth = selector.getDepth(bossProfile);
		final double bossMultiplier = selector.getMultiplier(bossProfile);
		
		for (int game = 0; game < NB_BOSSE_GAMES; game++)
		{
			final boolean candidateFirst = (game % 2 == 0);
			final double gameFitness = playSingleGame(
				candidateEvaluator,
				bossEvaluator,
				candidateFirst,
				depth,
				bossDepth) * bossMultiplier;
			
			totalFitness += gameFitness;
		}
		
		return totalFitness;
	}
	
	/**
	 * Sélectionne un profil d'adversaire à utiliser pour l'entraînement, en fonction des poids candidats et des performances passées de chaque profil
	 * @param candidateWeights Les poids du bot candidat, qui peuvent être utilisés par le sélecteur pour estimer les performances de chaque profil
	 * @return Le profil d'adversaire sélectionné pour l'entraînement
	 */
	public AdaptiveOpponentSelector.OpponentProfile sampleProfile(double[] candidateWeights)
	{
		return selector.selectProfile(candidateWeights);
	}
	
	/**
	 * Met à jour les poids du profil de l'adversaire sélectionné
	 * @param weights Poids à utiliser pour le profil de l'adversaire sélectionné
	 */
	public void updateBestWeights(double[] weights)
	{
	    selector.updateBestWeights(weights);
	}
		
	private double playSingleGame(
		PositionEvaluator candidateEvaluator,
		PositionEvaluator opponentEvaluator,
		boolean candidateIsPlayer0,
		int candidateDepth,
		int opponentDepth)
	{
		startBoard.initialize();
		BitBoard board = startBoard.clone();
		candidateTT.clear();
		opponentTT.clear();
		playRandomOpening(board, RANDOM_OPENING_PLIES);
		
		final TwoLevelTranspositionTable savedTT = BitMinMaxNode.transpositionTable;
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
				final int currentDepth = isCandidateTurn
					? candidateDepth
					: opponentDepth;
				
				BitMinMaxNode.transpositionTable = isCandidateTurn ? candidateTT : opponentTT;
				
				final double[] decision = getBestDecision(board, currentEval, currentDepth);
				board.playMove(decision);
				
				movesPlayed++;
			}
			
			final int candidatePlayer = candidateIsPlayer0 ? 0 : 1;
			final int opponentPlayer = 1 - candidatePlayer;
			final int candidateScore = board.getScore(candidatePlayer);
			final int opponentScore = board.getScore(opponentPlayer);
			final int scoreDelta = candidateScore - opponentScore;
			final int winner = board.getWinner();
			
			if (winner == candidatePlayer)
				return WIN_BONUS + scoreDelta;
			if (winner == -1)
				return DRAW_BONUS;
			return scoreDelta;
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
	 * Joue un nombre donné de coups d'ouverture aléatoires
	 * @param board Plateau de jeu sur lequel jouer les coups d'ouverture
	 * @param plies Nombre de coups d'ouverture à jouer
	 */
	private void playRandomOpening(BitBoard board, int plies)
	{
	    for (int p = 0; p < plies && !board.isGameOver(); p++)
	    {
	        int player = board.getCurrentPlayer();
	        boolean[] valid = board.getValidMoves(player);

	        int count = 0;
	        for (boolean v : valid) if (v) count++;
	        if (count == 0) return;

	        int pick = random.nextInt(count);
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
	
	private int resolveDepth(AdaptiveOpponentSelector.OpponentProfile profile, int normalDepth)
	{
		final int depth = selector.getDepth(profile);
		return (depth == AdaptiveOpponentSelector.DYNMIC_DEPTH) ? normalDepth : depth;
	}
	
	private int outcomeSign(double gameFitness, double multiplier)
	{
		final double normalised = gameFitness / multiplier;
		
		if (normalised > DRAW_BONUS)
			return 1;
		if (normalised < -DRAW_BONUS)
			return -1;
		return 0;
	}
}
