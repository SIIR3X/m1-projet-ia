package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitBoardConverter;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.PositionHistory;
import awele.bot.competitor.noname.search.minmax.NNMaxNode;
import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
import awele.bot.competitor.noname.training.BotEvaluator;
import awele.bot.competitor.noname.training.cmaes.CMAES;
import awele.bot.competitor.noname.training.cmaes.CMAESConfig;
import awele.core.Board;
import awele.core.InvalidBotException;

/**
 * @author Lucas Fagioli
 */
public final class NoNameBot extends CompetitorBot
{
	/**
	 * Temps maximum autorisé pour la recherche de décision
	 * Volontairement moins que 100ms
	 * Perte de 2% du temps
	 */
	private static final long MAX_TIME_MS = 98;
	
	/**
	 * Profondeur maximale de recherche
	 */
	private static final int MAX_DEPTH = 999;
	
	/**
	 * Profondeur minimale pour laquelle on applique la recherche MinMax
	 */
	private static final int MIN_DEPTH = 0;
	
	/**
	 * Temps pour apprendre les poids
	 */
	private static final long LEARN_WEIGHTS_MS = 30L * 60L * 1_000L;
	
	/**
	 * Temps pour apprendre les catégories
	 */
	private static final long LEARN_CATEGORIES_MS = 25L * 60L * 1_000L;
	
	public NoNameBot() throws InvalidBotException
	{
		this.setBotName("NoName");
		this.addAuthor("Lucas Fagioli");
	}

	@Override
	public void initialize()
	{
		// Clear de la TT
		// même si on préfèrerai la garder pour avoir un avantage en début de game
		NNMinMaxNode.transpositionTable.clear();
		
		NNMinMaxNode.pvTable.clearAll();
		NNMinMaxNode.currentPly = 0;
	}
	
	@Override
	public void learn()
	{
        final BotEvaluator botEvaluator = new BotEvaluator();
        
        // Bornes des poids
        double wMin = 0.0;
        double wMax = 15.0;
        
        // Phase 1 : poids
        // Durée 30min
        CMAESConfig cfg = new CMAESConfig(
        		PositionEvaluator.getDefaultWeights(),
        		0.8, // sigma = variation
        		10_000_000L, // inutile pour moi, dans l'algo de base mais ici on s'arrête au temps
        		LEARN_WEIGHTS_MS,
        		36, // nbGames
        		6, // depth
        		wMin, wMax); // bornes
        
        CMAES cmaes = new CMAES(cfg, botEvaluator);
        cmaes.optimize(NNMinMaxNode.positionEvaluator, "CMAES");
        
        // Phase 2 : catégories
        // Durée : 25min
        final PositionEvaluator evaluator = NNMinMaxNode.positionEvaluator;
        botEvaluator.trainCategoriesSelfPlay(evaluator, 7, LEARN_CATEGORIES_MS);
        
        // Marge de sécurité de 5min
	}

	@Override
	public double[] getDecision(Board board)
	{
	    final BitBoard bitBoard = BitBoardConverter.fromBoard(board);

	    // Premier coup : joue à droite
	    if (bitBoard.isFirstMove())
	    {
	        double[] opening = new double[6];
	        opening[5] = 1.0;
	        return opening;
	    }

	    NNMinMaxNode.transpositionTable.incrementAge();
	    NNMinMaxNode.startTimer(MAX_TIME_MS);

	    // Reset de l'historique
	    PositionHistory.clear();

	    double[] bestDecision = null;

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

	        if (depth >= MAX_DEPTH || NNMinMaxNode.isTimeExpired())
	            break;
	    }

	    NNMinMaxNode.resetTimer();

	    // Fallback si aucune décision trouvée
	    if (bestDecision == null)
	    {
	        NNMinMaxNode.initialize(bitBoard, 1);
	        bestDecision = new NNMaxNode(bitBoard).getDecision();
	    }

	    return bestDecision;
	}
	
	@Override
	public void finish()
	{

	}
}
