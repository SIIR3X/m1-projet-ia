package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.algorithms.training.BotEvaluator;
import awele.bot.competitor.noname.algorithms.training.SPSA;
import awele.bot.competitor.noname.algorithms.training.SPSAConfig;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.bot.competitor.noname.test.TrainingLogger;
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
	private static final long LEARN_BUDGET_MS = 59L * 60L * 1_000L;
	
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
			// Création de la configuation pour l'algorithme SPSA
			final SPSAConfig config = new SPSAConfig(
				    0.602, // alpha : taux de décroissance du gain (learning rate)
				    0.101, // gamma : taux de décroissance de la perturbation
				    0.4, // a : gain initial (learning rate initial)
				    0.5, // c : perturbation initiale
				    1700.0, // A : nombre d'itérations avant de commencer à décroître le gain
				    4, // nbGamesPerIteration : nombre de parties jouées pour estimer la performance à chaque itération
				    5, // trainingDepth : profondeur de recherche utilisée pendant l'entraînement
				    LEARN_BUDGET_MS, // learnBudgetMs : budget total pour l'apprentissage en ms
				    -50.0, // minWeight : poids minimum pour les paramètres du bot
				    50.0  // maxWeight : poids maximum pour les paramètres du bot
				);
			
			// Création de l'évaluateur de bot qui sera utilisé pour estimer la performance du bot pendant l'entraînement
	        final BotEvaluator botEvaluator = new BotEvaluator();
	        
	        // Création de l'algorithme SPSA avec la configuration et l'évaluateur
	        final SPSA spsa = new SPSA(config);
	        
	        // Lancement de l'optimisation des paramètres du bot avec SPSA
	        spsa.optimize(BitMinMaxNode.positionEvaluator, botEvaluator, logger, "ADAPTIVE");
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
