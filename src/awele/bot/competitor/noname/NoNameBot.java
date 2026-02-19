package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.algorithms.training.BotEvaluator;
import awele.bot.competitor.noname.algorithms.training.SPSA;
import awele.bot.competitor.noname.algorithms.training.SPSAConfig;
import awele.bot.competitor.noname.algorithms.training.TrainingLogger;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.core.Board;
import awele.core.InvalidBotException;

public final class NoNameBot extends CompetitorBot
{
	// ===== Constantes =====
	
	/**
	 * Temps maximum autorisé pour la recherche de décision
	 */
	private static final long MAX_TIME_MS = 98;
	
	/**
	 * Profondeur maximale de recherche
	 */
	private static final int MAX_DEPTH = 999;
	
	/**
	 * Profondeur minimale pour laquelle on applique la recherche MinMax (en dessous, on peut faire une recherche exhaustive)
	 */
	private static final int MIN_DEPTH = 0;
	
	// ===== Variables d'instance =====
	
	private int lastDepthReached;
	
	// ===== Constantes d'entraînement =====
	
	/**
	 * Budget total dispobile pour l'apprentissage (en ms)
	 */
	private static final long LEARN_BUDGET_MS = 60L * 60L * 1_000L;
	
	public NoNameBot() throws InvalidBotException
	{
		this.setBotName("NoName");
		this.addAuthor("Lucas Fagioli");
		
		this.lastDepthReached = 0;
	}

	@Override
	public void initialize()
	{
		this.lastDepthReached = 0;
		
		BitMinMaxNode.transpositionTable.clear();
	}
	
	@Override
	public void learn()
	{
		try (TrainingLogger logger = new TrainingLogger())
		{
//			final SPSAConfig config = new SPSAConfig(
//				0.602, // alpha : exposant de décroissance du taux d'apprentissage
//				0.101, // gamma : exposant de décroissance du taux de perturbation
//				0.05, // a : amplitude initiale du taux d'apprentissage
//				1, // c : amplitude initiale du taux de perturbation
//				5000.0, // A : paramètre de stabilité
//				2, // nbGamesPerEstimate : nombre de parties à simuler à chaque évaluation
//				6, // trainingDepth : profondeur de recherche pendant l'entraînement
//				LEARN_BUDGET_MS,
//				-50.0, 50.0
//			);
			
			// 0.85 en 10Min
//			final SPSAConfig config = new SPSAConfig(
//					0.602, 0.101,   // alpha, gamma (Spall 1998)
//					0.5,   0.2,     // a, c : exploration large sur toute la durée
//					100.0,          // A : stabilisation en début d'entraînement
//					2,              // nbGamesPerEstimate
//					5,              // trainingDepth
//					LEARN_BUDGET_MS,
//					-50.0, 50.0     // weightMin, weightMax
//				);
			
			// 0.38 en 38min
//			final SPSAConfig config = new SPSAConfig(
//				0.602, 0.101,   // alpha, gamma (Spall 1998)
//				0.5,   0.2,     // a, c : exploration large sur toute la durée
//				100.0,          // A : stabilisation en début d'entraînement
//				8,              // nbGamesPerEstimate
//				6,              // trainingDepth
//				LEARN_BUDGET_MS,
//				-50.0, 50.0     // weightMin, weightMax
//			);
			
			final long PHASE1_MS = 25L * 60L * 1_000L;
			final long PHASE2_MS = LEARN_BUDGET_MS - PHASE1_MS;
			
	        // Phase 1 : exploration rapide
			final SPSAConfig configPhase1 = new SPSAConfig(
				    0.602, 0.101,   // alpha, gamma
				    0.6,   0.6,     // a, c  (ck plus grand et plus durable)
				    10.0,           // A     (démarrage plus réactif)
				    4,              // nbGamesPerEstimate
				    5,              // trainingDepth
				    PHASE1_MS,
				    -50.0, 50.0
				);


	        // Phase 2 : raffinement stable
			final SPSAConfig configPhase2 = new SPSAConfig(
				    0.602, 0.101,
				    0.5,   0.3,     // a, c  (perturbation suffisante)
				    30.0,           // A
				    6,              // nbGamesPerEstimate (plus rapide que 8, assez stable)
				    6,              // trainingDepth
				    PHASE2_MS,
				    -50.0, 50.0
				);

				
	        final BotEvaluator botEvaluator = new BotEvaluator();

	        // Phase 1
	        new SPSA(configPhase1).optimize(
	            BitMinMaxNode.positionEvaluator,
	            botEvaluator,
	            logger,
	            "FAST_25MIN"
	        );

	        // Phase 2 (repart des meilleurs poids trouvés en phase 1 car optimize() fait evaluator.setWeights(bestWeights))
	        new SPSA(configPhase2).optimize(
	            BitMinMaxNode.positionEvaluator,
	            botEvaluator,
	            logger,
	            "REFINE_REST"
	        );
			
//			final BotEvaluator botEvaluator = new BotEvaluator();
//			final SPSA spsa = new SPSA(config);
//			
//			spsa.optimize(BitMinMaxNode.positionEvaluator, botEvaluator, logger, "ADAPTIVE");
		}
	}

	@Override
	public void finish()
	{
		System.out.println(lastDepthReached);
		System.out.println("VIsite : " + BitMinMaxNode.nodeCount);
	}

	@Override
	public double[] getDecision(Board board)
	{
		BitMinMaxNode.nodeCount = 0;
		
		// On convertit le Board classique en BitBoard pour utiliser nos algorithmes optimisés
		final BitBoard bitBoard = BitBoardConverter.fromBoard(board);

		// Si c'est le premier coup, on joue a droite (très bon coup d'ouverture) sans faire de recherche pour économiser du temps
		if (bitBoard.isFirstMove())
		{
			this.lastDepthReached = 0;
			double[] opening = new double[6];
			opening[5] = 1.0;
			return opening;
		}
			
		BitMinMaxNode.transpositionTable.incrementAge();

		// On lance le timer
		BitMinMaxNode.startTimer(MAX_TIME_MS);
		
		// Variables pour la recherche itérative
		double[] bestDecision = null;
		double[] currentDecision;
		int depthReached = 0;
		
		// Recherche itérative prograssive (iterative deepening)
		// On commence à profondeur 1 et on augmente jusqu'à ce que le temps soit écoulé
		for (int depth = 1; depth <= MAX_DEPTH; depth++)
		{
			// On s'assure d'avoir au moins exploré MIN_DEPTH
			if (depth > MIN_DEPTH && BitMinMaxNode.isTimeExpired())
				break;
			
			// On initialise l'algorithme pour cette profondeur
			BitMinMaxNode.initialize(bitBoard, depth);
			
			// On crée un BitMaxNode pour le BitBoard actuel et on récupère la décision à partir de ce noeud
			BitMaxNode rootNode = new BitMaxNode(bitBoard);
			
			// Si la recherche a été interrompue, on garde le résultat de la profondeur précédente
			if (rootNode.isInterrupted())
				break;
			
			// La recherche s'est terminée complètement pour cette profondeur
			currentDecision = rootNode.getDecision();
			bestDecision = currentDecision;
			depthReached = depth;
			
			// Si on atteint la profondeur maximale, on arrête
			if (depth >= MAX_DEPTH)
				break;
			
			// On vérifie une dernière fois le temps
			if (BitMinMaxNode.isTimeExpired())
				break;
		}
		
		this.lastDepthReached = depthReached;

		// On reset le timer
		BitMinMaxNode.resetTimer();
		
		// Si aucune décision d'a été trouvée on fait une recherche minimale à profondeur 1
		if (bestDecision == null)
		{
			BitMinMaxNode.initialize(bitBoard, 1);
			bestDecision = new BitMaxNode(bitBoard).getDecision();
			this.lastDepthReached = 1;
		}

		return bestDecision;
	}
	
	public int getLastDepthReached()
	{
	    return this.lastDepthReached;
	}
}
