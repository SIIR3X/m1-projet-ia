package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.PositionHistory;
import awele.bot.competitor.noname.search.minmax.BitMaxNode;
import awele.bot.competitor.noname.search.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.test.TrainingLogger;
import awele.bot.competitor.noname.training.BotEvaluator;
import awele.bot.competitor.noname.training.cmaes.CMAES;
import awele.bot.competitor.noname.training.cmaes.CMAESConfig;
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
	 * Temps pour apprendre les poids
	 */
	private static final long LEARN_WEIGHTS_MS = 45L * 60L * 1_000L;
	
	/**
	 * Temps pour apprendre les catégories
	 */
	private static final long LEARN_CATEGORIES_MS = 10L * 60L * 1_000L;
	
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
	    try (TrainingLogger logger = new TrainingLogger())
	    {
	        final BotEvaluator botEvaluator = new BotEvaluator();
	        
	        double wMin = 0.0;
	        double wMax = 15.0;
	        
	        // Phase 1 : poids
	        CMAESConfig cfg = new CMAESConfig(
	        		PositionEvaluator.getDefaultPhaseAwareWeights(),
	        		0.6, // sigma
	        		10_000_000L, // inutile pour moi
	        		LEARN_WEIGHTS_MS,
	        		12, // nbGames
	        		7, // depth
	        		wMin, wMax); // bornes
	        
	        CMAES cmaes = new CMAES(cfg, botEvaluator);
	        //cmaes.optimize(BitMinMaxNode.positionEvaluator, logger, "CMAES");
	        
	        // Phase 2 : catégories
	        final PositionEvaluator evaluator = BitMinMaxNode.positionEvaluator;
	        //botEvaluator.trainCategoriesSelfPlay(evaluator, 500, 7, LEARN_CATEGORIES_MS);
	    }
	}

	@Override
	public void finish()
	{
	    System.out.println("=== FINAL STATISTICS ===");
	    System.out.println("Depth reached: " + lastDepthReached);
	    System.out.println("Nodes visited: " + BitMinMaxNode.nodeCount);
	    System.out.println("Nodes/sec: " + (BitMinMaxNode.nodeCount * 1000 / 98) + "k");
	    System.out.println("TT1: " + BitMinMaxNode.transpositionTable.getPrimaryTable().count()
	            + " / " + BitMinMaxNode.transpositionTable.getPrimaryTable().size()
	            + " (" + (100 * BitMinMaxNode.transpositionTable.getPrimaryTable().count() 
	                     / BitMinMaxNode.transpositionTable.getPrimaryTable().size()) + "%)");
	    System.out.println("TT2: " + BitMinMaxNode.transpositionTable.getSecondaryTable().count()
	            + " / " + BitMinMaxNode.transpositionTable.getSecondaryTable().size());
	    System.out.println("Position history max: " + PositionHistory.maxSize());
	    System.out.println("Repetitions detected: " + PositionHistory.getRepetitionsDetected());

	}

	@Override
	public double[] getDecision(Board board)
	{
	    BitMinMaxNode.nodeCount = 0;
	    
	    final BitBoard bitBoard = BitBoardConverter.fromBoard(board);

	    // Premier coup : joue à droite (coup d'ouverture fort)
	    if (bitBoard.isFirstMove())
	    {
	        this.lastDepthReached = 0;
	        double[] opening = new double[6];
	        opening[5] = 1.0;
	        return opening;
	    }
	    
	    // Préparation de la recherche
	    BitMinMaxNode.transpositionTable.incrementAge();
	    BitMinMaxNode.startTimer(MAX_TIME_MS);
	    
	    // Reset de l'historique (UNE SEULE FOIS avant toute la recherche)
	    PositionHistory.clear();
	    
	    double[] bestDecision = null;
	    int depthReached = 0;
	    
	    // Iterative Deepening : profondeur croissante jusqu'à timeout
	    for (int depth = 1; depth <= MAX_DEPTH; depth++)
	    {
	        if (depth > MIN_DEPTH && BitMinMaxNode.isTimeExpired())
	            break;
	        
	        BitMinMaxNode.initialize(bitBoard, depth);
	        
	        BitMaxNode rootNode = new BitMaxNode(bitBoard);
	        
	        if (rootNode.isInterrupted())
	            break;
	        
	        bestDecision = rootNode.getDecision();
	        depthReached = depth;
	        
	        if (depth >= MAX_DEPTH || BitMinMaxNode.isTimeExpired())
	            break;
	    }
	    
	    this.lastDepthReached = depthReached;
	    BitMinMaxNode.resetTimer();
	    
	    // Fallback si aucune décision trouvée
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
