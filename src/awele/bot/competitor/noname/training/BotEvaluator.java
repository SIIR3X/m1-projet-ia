// BotEvaluator.java  (version complète, avec méthodes "learn" (remplissage TT train) ici)
package awele.bot.competitor.noname.training;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import java.util.Random;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.CategoryMoveOrdering;
import awele.bot.competitor.noname.ordering.PositionHistory;
import awele.bot.competitor.noname.search.minmax.NNMaxNode;
import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
import awele.bot.competitor.noname.search.transposition.TranspositionTable;

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
	private final TranspositionTable candidateTT;

	/**
	 * Table de transposition de l'adversaire
	 */
	private final TranspositionTable opponentTT;

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
	private static final int RANDOM_OPENING_PLIES = 4;

	/**
	 * Points attribués pour une victoire
	 */
	private static final double WIN_BONUS  = 50.0;

	/**
	 * Points attribués pour un match nul
	 */
	private static final double DRAW_BONUS = 25.0;

	/**
	 * Pourcetange contre le boss
	 */
	private static final double BOSS_GAME_RATIO = 0.10;

	public BotEvaluator()
	{
		this.selector = new AdaptiveOpponentSelector();

		this.candidateTT = new TranspositionTable(21, false);
		this.opponentTT  = new TranspositionTable(21, false);

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

		candidateTT.clear();
		opponentTT.clear();

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
			int result = outcomeSign(gameFitness, normalMultiplier);
			selector.reportResult(profile, result);
		}

		// Partie contre le BOSS
		final AdaptiveOpponentSelector.OpponentProfile bossProfile = AdaptiveOpponentSelector.OpponentProfile.BOSS;

		final PositionEvaluator bossEvaluator = selector.getEvaluator(bossProfile);
		final int bossDepth = resolveDepth(bossProfile, depth);
		final double bossMultiplier = selector.getMultiplier(bossProfile);

		int nbBossGames = Math.max(2, (int)(nbGames * BOSS_GAME_RATIO));

		for (int game = 0; game < nbBossGames; game++)
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

	/**
	 * Entraîne les catégories de coups en jouant des parties en self-play
	 * @param evaluator L'évaluateur de position
	 * @param depth Profondeur de recherche à utiliser pour les parties d'entraînement
	 * @param maxTimeMs Temps maximum à consacrer à l'entraînement, en millisecondes
	 */
	public void trainCategoriesSelfPlay(PositionEvaluator evaluator, int depth, long maxTimeMs)
	{
		CategoryMoveOrdering.resetScores();
		CategoryMoveOrdering.LEARNING_ENABLED = true;

		final long startTime = System.currentTimeMillis();

		int game = 0;
		
		while (true)
		{
			final long elapsed = System.currentTimeMillis() - startTime;
			if (elapsed >= maxTimeMs)
				break;

			final boolean candidateFirst = (game % 2 == 0);
			
			System.out.println("Self-play game " + (game + 1) + " / " + "elapsed: " + (elapsed / 1000) + "s / " + (maxTimeMs / 1000) + "s");
			
			playSingleGame(
				evaluator,
				evaluator,
				candidateFirst,
				depth,
				depth);
			
			game++;
		}

		CategoryMoveOrdering.LEARNING_ENABLED = false;
	}

	/**
	 * Joue une partie entre le bot candidat et un adversaire donné
	 * @param candidateEvaluator L'évaluateur de position du bot candidat
	 * @param opponentEvaluator L'évaluateur de position de l'adversaire
	 * @param candidateIsPlayer0 Vrai si le bot candidat joue en premier (joueur 0), faux s'il joue en second (joueur 1)
	 * @param candidateDepth Profondeur de recherche à utiliser pour le bot candidat
	 * @param opponentDepth Profondeur de recherche à utiliser pour l'adversaire
	 * @return Un score numérique représentant le résultat de la partie
	 */
	private double playSingleGame(
		PositionEvaluator candidateEvaluator,
		PositionEvaluator opponentEvaluator,
		boolean candidateIsPlayer0,
		int candidateDepth,
		int opponentDepth)
	{
		startBoard.initialize();
		BitBoard board = startBoard.clone();

		PositionHistory.clear();

		playRandomOpening(board, RANDOM_OPENING_PLIES);

		final TranspositionTable savedTT = NNMinMaxNode.transpositionTable;
		final PositionEvaluator savedEval = NNMinMaxNode.positionEvaluator;
		final boolean savedExpired = NNMinMaxNode.timeExpired;
		final long savedStart = NNMinMaxNode.searchStartTime;
		final long savedMax = NNMinMaxNode.maxSearchTime;

		// Pas de timer pendant l'évaluation brute (comme avant)
		NNMinMaxNode.timeExpired = false;
		NNMinMaxNode.searchStartTime = 0L;
		NNMinMaxNode.maxSearchTime = Long.MAX_VALUE;

		try
		{
			int movesPlayed = 0;

			while (!board.isGameOver() && movesPlayed < MAX_MOVES_PER_GAME)
			{
				final int currentPlayer = board.getCurrentPlayer();

				final boolean isCandidateTurn =
					(candidateIsPlayer0 && currentPlayer == 0) ||
					(!candidateIsPlayer0 && currentPlayer == 1);

				final PositionEvaluator currentEval = isCandidateTurn ? candidateEvaluator : opponentEvaluator;
				final int currentDepth = isCandidateTurn ? candidateDepth : opponentDepth;

				NNMinMaxNode.transpositionTable = isCandidateTurn ? candidateTT : opponentTT;

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
			NNMinMaxNode.transpositionTable = savedTT;
			NNMinMaxNode.positionEvaluator = savedEval;
			NNMinMaxNode.timeExpired = savedExpired;
			NNMinMaxNode.searchStartTime = savedStart;
			NNMinMaxNode.maxSearchTime = savedMax;
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

	private double[] getBestDecision(BitBoard board, PositionEvaluator evaluator, int depth)
	{
		NNMinMaxNode.positionEvaluator = evaluator;

		NNMinMaxNode.initialize(board, depth);

		final NNMaxNode rootNode = new NNMaxNode(board);

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
		return selector.resolveDepth(profile, normalDepth);
	}

	private int outcomeSign(double gameFitness, double multiplier)
	{
		// signe du résultat (win/draw/loss), en tenant compte du multiplicateur (boss etc.)
		final double normalized = gameFitness / multiplier;
		if (normalized > 0.0) return 1;
		if (normalized < 0.0) return -1;
		return 0;
	}
}