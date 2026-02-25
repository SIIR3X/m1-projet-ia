package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.bot.competitor.noname.search.minmax.BitMaxNode;
import awele.bot.competitor.noname.search.minmax.BitMinMaxNode;
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
	private static final long LEARN_BUDGET_MS = 55L * 60L * 1_000L;
	
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
	}
	
	@Override
	public void learn()
	{
//	    try (TrainingLogger logger = new TrainingLogger())
//	    {
//	        final BotEvaluator botEvaluator = new BotEvaluator();
//	        
//	        double wMin = 0.0;
//	        double wMax = 15.0;
//	        
//	        CMAESConfig cfg = new CMAESConfig(
//	        		PositionEvaluator.getDefautltWeights(),
//	        		0.8, // sigma
//	        		10_000_000L, // inutile pour moi
//	        		LEARN_BUDGET_MS,
//	        		128, // nbGames
//	        		5, // depth
//	        		wMin, wMax); // bornes
//	        
//	        CMAES cmaes = new CMAES(cfg, botEvaluator);
//	        cmaes.optimize(BitMinMaxNode.positionEvaluator, logger, "CMAES");
//	    }
	}

	@Override
	public void finish()
	{
		System.out.println(lastDepthReached);
		System.out.println("VIsite : " + BitMinMaxNode.nodeCount);
		System.out.println("Hit : " + BitMinMaxNode.transpositionTable.count());
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
