package awele.bot.competitor.noname;

import awele.bot.CompetitorBot;
import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
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
	private static final long MAX_TIME_MS = 95;
	
	/**
	 * Profondeur maximale de recherche
	 */
	private static final int MAX_DEPTH = 14;
	
	/**
	 * Profondeur minimale pour laquelle on applique la recherche MinMax (en dessous, on peut faire une recherche exhaustive)
	 */
	private static final int MIN_DEPTH = 6;
	
	// ===== Variables d'instance =====
	
	private int lastDepthReached;
	private long lastSearchTime;
	
	public NoNameBot() throws InvalidBotException
	{
		this.setBotName("NoNameBot");
		this.addAuthor("Lucas Fagioli");
		
		this.lastDepthReached = 0;
		this.lastSearchTime = 0;
	}

	@Override
	public void initialize()
	{
		this.lastDepthReached = 0;
		this.lastSearchTime = 0;
	}
	
	@Override
	public void learn()
	{

	}

	@Override
	public void finish()
	{
		
	}

	@Override
	public double[] getDecision(Board board)
	{
		// On convertit le Board classique en BitBoard pour utiliser nos algorithmes optimisés
		final BitBoard bitBoard = BitBoardConverter.fromBoard(board);
		
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
		this.lastSearchTime = BitMinMaxNode.getElapsedTimeMs();
		
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
}
