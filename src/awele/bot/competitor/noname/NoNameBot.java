package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.algorithms.training.BotEvaluator;
import awele.bot.competitor.noname.algorithms.training.SPSA;
import awele.bot.competitor.noname.algorithms.training.SPSAConfig;
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
	
	/**
	 * Proportion du budget allouée à la phase Joan (Sala)
	 */
	private static final double JOAN_SALA_RATIO = 0.75;

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
		// PHASE 1 - JOAN SALA
		final long joanSalaBudgetMs = (long)(LEARN_BUDGET_MS * JOAN_SALA_RATIO);
		final SPSAConfig configPhase1 = new SPSAConfig(
			0.602,
			0.101,
			0.5,
			0.2,
			100.0,
			2,
			5,
			joanSalaBudgetMs,
			-50.0,
			50.0
		);
		final BotEvaluator botEvaluator = new BotEvaluator();
		final SPSA spsaPhase1 = new SPSA(configPhase1);
		spsaPhase1.optimize(BitMinMaxNode.positionEvaluator, botEvaluator);
		
		// PHASE 2 - SELF-PLAY
		final long selfPlayBudgetMs = LEARN_BUDGET_MS - joanSalaBudgetMs;
		final SPSAConfig configPhase2 = new SPSAConfig(
			0.602,
			0.101,
			0.5,
			0.2,
			100.0,
			2,
			5,
			selfPlayBudgetMs,
			-50.0,
			50.0
		);
		final BotEvaluator selfPlayEvaluator = new BotEvaluator(BotEvaluator.OpponentProfile.SELF_PLAY);
		final SPSA spsaPhase2 = new SPSA(configPhase2);
		spsaPhase2.optimize(BitMinMaxNode.positionEvaluator, selfPlayEvaluator);
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
