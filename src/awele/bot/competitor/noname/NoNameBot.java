package awele.bot.competitor.noname;

import java.util.Arrays;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.PositionHistory;
import awele.bot.competitor.noname.search.minmax.NNMaxNode;
import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
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
	 * Volontairement moins que 100ms
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
		
		// Clear de la TT
		// même si on préfèrerai la garder
		// fairplay
		NNMinMaxNode.transpositionTable.clear();
		
		NNMinMaxNode.pvTable.clearAll();
		NNMinMaxNode.currentPly = 0;
	}
	
	@Override
	public void learn()
	{
	    try (TrainingLogger logger = new TrainingLogger())
	    {
	        final BotEvaluator botEvaluator = new BotEvaluator();
	        
	        double wMin = 0.0;
	        double wMax = 15.0;
	        
	        double[] w = new double[14];
	        Arrays.fill(w, 7.5);
	        
	        // Phase 1 : poids
	        CMAESConfig cfg = new CMAESConfig(
	        		PositionEvaluator.getDefaultPhaseAwareWeights(),
	        		//w,
	        		1.0, // sigma
	        		10_000_000L, // inutile pour moi
	        		LEARN_WEIGHTS_MS,
	        		12, // nbGames
	        		4, // depth
	        		wMin, wMax); // bornes
	        
	        CMAES cmaes = new CMAES(cfg, botEvaluator);
	        //cmaes.optimize(NNMinMaxNode.positionEvaluator, logger, "CMAES");
	        
	        // Phase 2 : catégories
	        final PositionEvaluator evaluator = NNMinMaxNode.positionEvaluator;
	        //botEvaluator.trainCategoriesSelfPlay(evaluator, 750, 7, LEARN_CATEGORIES_MS);
	    }
	}

	@Override
	public double[] getDecision(Board board)
	{
	    NNMinMaxNode.nodeCount = 0;

	    final BitBoard bitBoard = BitBoardConverter.fromBoard(board);

	    // Premier coup : joue à droite
	    if (bitBoard.isFirstMove())
	    {
	        this.lastDepthReached = 0;
	        double[] opening = new double[6];
	        opening[5] = 1.0;
	        return opening;
	    }

	    // Préparation de la recherche
	    NNMinMaxNode.transpositionTable.incrementAge();
	    NNMinMaxNode.startTimer(MAX_TIME_MS);

	    // Reset de l'historique
	    PositionHistory.clear();

	    double[] bestDecision = null;
	    int depthReached = 0;

	    // Iterative Deepening : profondeur croissante jusqu'à timeout
	    for (int depth = 1; depth <= MAX_DEPTH; depth++)
	    {
	        if (depth > MIN_DEPTH && NNMinMaxNode.isTimeExpired())
	            break;

	        NNMinMaxNode.initialize(bitBoard, depth);

	        NNMaxNode rootNode = new NNMaxNode(bitBoard);

	        if (rootNode.isInterrupted())
	            break;

	        bestDecision = rootNode.getDecision();
	        depthReached = depth;

	        if (depth >= MAX_DEPTH || NNMinMaxNode.isTimeExpired())
	            break;
	    }

	    this.lastDepthReached = depthReached;
	    NNMinMaxNode.resetTimer();

	    // Fallback si aucune décision trouvée
	    if (bestDecision == null)
	    {
	        NNMinMaxNode.initialize(bitBoard, 1);
	        bestDecision = new NNMaxNode(bitBoard).getDecision();
	        this.lastDepthReached = 1;
	    }

	    return bestDecision;
	}
	
	@Override
	public void finish()
	{
	    System.out.println("=== FINAL STATISTICS ===");
	    System.out.println("Depth reached: " + lastDepthReached);
	    System.out.println("Nodes visited: " + NNMinMaxNode.nodeCount);
	    System.out.println("Transposition Table size: " + NNMinMaxNode.transpositionTable.fillRatio() * 100.0 + "%");
	    System.out.println(NNMinMaxNode.transpositionTable.hitRate() * 100.0 + "% hits");
	}
	
	public int getLastDepthReached()
	{
	    return this.lastDepthReached;
	}
}
