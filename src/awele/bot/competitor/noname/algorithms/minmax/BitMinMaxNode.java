package awele.bot.competitor.noname.algorithms.minmax;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MIN_SEEDS_TO_CONTINUE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.WINNING_SCORE;

import awele.bot.competitor.noname.algorithms.heuristics.MoveEvaluator;
import awele.bot.competitor.noname.algorithms.transposition.TranspositionTable;
import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Noeud abstrait pour l'algorithme MinMax utilisant BitBoard
 * 
 * Cette classe implémente la strtucture classique de MinMax avec deux types de nœuds :
 * - MaxNode : cherche à maximiser le score du joueur
 * - MinNode : cherche à minimiser le score (maximiser pour l'adversaire)
 */
public abstract class BitMinMaxNode
{
	// ===== Variables statiques =====
	
	/**
	 * Numéro de joueur de l'IA (0 ou 1)
	 */
	protected static int player;
	
	/**
	 * Profondeur maximale pour la recherche MinMax
	 */
	protected static int maxDepth;
	
	/**
	 * Timestamp de début de la recherche
	 */
	protected static long searchStartTime;
	
	/**
	 * Durée maximale autorisée pour la recherche
	 */
	protected static long maxSearchTime;
	
	/*
	 * Flag indiquant si le temps est écoulé
	 */
	protected static volatile boolean timeExpired;

	// ===== Variables d'instance =====
	
	/**
	 * Évaluation du noeud : estimation de la valeur de la position pour le joueur actuel
	 */
	private double evaluation;
	
	/**
	 * Tableau de décisions pour chaque trou possible (indexé de 0 à 5)
	 */
	private final double[] decision;
	
	/**
	 * Indique si la recherche a été interrompue
	 */
	private boolean interrupted;
	
	public static TranspositionTable transpositionTable = new TranspositionTable();
	
	/**
	 * Constructeur
	 * @param board L'état de la grille de jeu
	 * @param depth La profondeur du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 */
	public BitMinMaxNode(BitBoard board, int depth, double alpha, double beta)
	{
		// Initialisation du tableau des décisions
		this.decision = new double[NB_HOLES];

		// Flag d'interruption
		this.interrupted = false;
		
		this.evaluation = getWorstScore();

		if (depth > 0 && isTimeExpired())
		{
			//this.evaluation = evaluatePosition(board);
			this.interrupted = true;
			return;
		}

		final int currentPlayer = board.getCurrentPlayer();
		
		// On utilise le MoveEvaluator pour ordonner les coups par qualité
		// avant de les explrer
		final MoveEvaluator moveEvaluator = new MoveEvaluator(depth);
		int[] orderedMoves = moveEvaluator.orderMoves(board, currentPlayer);

		// Variables locales pour éviter les accès répétés aux champs
		double currentAlpha = alpha;
		double currentBeta = beta;
		final boolean isRootNode = (depth == 0);

		// On parcours les coups ordonnés pour explorer les branches les plus prometteuses en premier
		for (int moveIndex = 0; moveIndex < orderedMoves.length; moveIndex++)
		{
			final int i = orderedMoves[moveIndex];
			
			// Si le temps de recherche est écoulé à une profondeur critique (profondeur 2 ou moins), 
			// on interrompt la recherche pour éviter de dépasser le temps imparti
			if (depth <= 2 && isTimeExpired())
			{
				this.interrupted = true;
				break;
			}
			
			final double[] decisionArray = new double[NB_HOLES];
			decisionArray[i] = 1.0;
			
			final BitBoard copy = board.clone();
			final int score = copy.playMove(decisionArray);
			
			// On calcul l'évaluation pour ce coup
			final double moveEvaluation;
			
			// On vérifie les conditions de fin de partie
			final int opponentScore = copy.getScore(1 - copy.getCurrentPlayer());
			final int totalSeeds = copy.getTotalSeeds(0) + copy.getTotalSeeds(1);
			
			if (score < 0 || opponentScore >= WINNING_SCORE || totalSeeds <= MIN_SEEDS_TO_CONTINUE)
				// Fin de partie détectée : évaluation directe
				moveEvaluation = evaluatePosition(copy);
			else if (depth < maxDepth)
			{
				// Profondeur non atteinte : exploration recursive
				final BitMinMaxNode child = createNextNode(copy, depth + 1, currentAlpha, currentBeta);
				
				// Si la recherche a été interrompue dans le noeud fils, on propage l'interruption vers le haut pour arrêter toute la recherche
				if (child.interrupted)
				{
					this.interrupted = true;
					break;
				}
				
				moveEvaluation = child.getEvaluation();
			}
			else
				// Profondeur maximale atteinte : évaluation de la position
				moveEvaluation = evaluatePosition(copy);
			
			// On stocke l'évaluation du coup
			this.decision[i] = moveEvaluation;
			
			// On met à jour l'évaluation du noeud selon min/max
			this.evaluation = updateEvaluation(moveEvaluation, this.evaluation);
			
			// Élagage Alpha-Beta
			if (!isRootNode)
			{
				currentAlpha = updateAlpha(this.evaluation, currentAlpha);
				currentBeta = updateBeta(this.evaluation, currentBeta);
				
				// Vérification de la condition de coupe
				if (shouldPrune(this.evaluation, currentAlpha, currentBeta))
				{
					// On enregistre ce coup comme un killer move pour cette profondeur, car il a causé une coupe alpha-beta
					// KillerMoveTable.store(depth, i);
					break;
				}
			}
		}
	}
		
	public final double getEvaluation()
	{
		return this.evaluation;
	}
	
	public final double[] getDecision()
	{
		return this.decision;
	}
	
	public final boolean isInterrupted()
	{
		return this.interrupted;
	}
	
	// ===== Méthodes statiques =====
	
	/**
	 * Initialisation des paramètres statiques
	 * @param board Le plateau de départ
	 * @param depth La profondeur maximale pour la recherche MinMax
	 */
	public static void initialize(BitBoard board, int depth)
	{
		BitMinMaxNode.maxDepth = depth;
		BitMinMaxNode.player = board.getCurrentPlayer();
	}
	
	/**
	 * Initialisation du timer pour la recherche MinMax
	 * @param maxTimeMs Le temps maximum autorisé pour la recherche en millisecondes
	 */
	public static void startTimer(long maxTimeMs)
	{
		BitMinMaxNode.searchStartTime = System.nanoTime();
		BitMinMaxNode.maxSearchTime = maxTimeMs * 1_000_000L;
		BitMinMaxNode.timeExpired = false;
	}
	
	/**
	 * Reset le timer
	 */
	public static void resetTimer()
	{
		BitMinMaxNode.searchStartTime = 0;
		BitMinMaxNode.maxSearchTime = Long.MAX_VALUE;
		BitMinMaxNode.timeExpired = false;
	}
	
	/**
	 * Vérifie si le temps de recherche est écoulé
	 * @return Un booléen qui indique si le temps de recherche est écoulé
	 */
	public static boolean isTimeExpired()
	{
		if (timeExpired)
			return true;
		
		final long elapsedTime = System.nanoTime() - searchStartTime;
		if (elapsedTime >= maxSearchTime)
		{
			timeExpired = true;
			return true;
		}
		
		return false;
	}
	
	/**
	 * Retourne le temps écoulé depuis le début de la recherche en millisecondes
	 * @return Le temps écoulé depuis le début de la recherche en millisecondes
	 */
	public static long getElapsedTimeMs()
	{
		return (System.nanoTime() - searchStartTime) / 1_000_000L;
	}
	
	// ===== Méthodes d'évaluation =====
	
	/**
	 * Calcule la différence de score du point de vue de l'IA
	 * @param board L'état du plateau de jeu
	 * @return La différence de score entre l'IA et l'adversaire
	 */
	private double evaluatePosition(BitBoard board)
	{
		//return board.getScore(player) - board.getScore(1 - player);
		
	    double eval = (board.getScore(player) - board.getScore(1-player)) * 10.0;
	    
	    for (int i = 0; i < 6; i++) {
	        int my = board.getSeeds(player, i);
	        int opp = board.getSeeds(1-player, i);
	        
	        if (my > 0 && my < 3) eval -= 5.0;      // Trous vulnérables
	        if (opp > 0 && opp < 3) eval += 5.0;
	        if (my >= 12) eval += 6.0;              // Concentrations
	        if (opp >= 12) eval -= 6.0;
	    }
	    
	    return eval;
	}
	
	// ===== Méthodes abstraites =====
	
	/**
	 * Retourne le pire score possible pour ce type de noeud (Min ou Max)
	 * @return Le pire score possible pour ce type de noeud
	 */
	protected abstract double getWorstScore();
	
	/**
	 * Met à jour l'évaluation selon le type de noeud (Min ou Max)
	 * @param newEval L'évaluation à comparer avec l'évaluation courante
	 * @param currentEval L'évaluation courante du noeud
	 * @return La nouvelle évaluation mise à jour selon le type de noeud
	 */
	protected abstract double updateEvaluation(double newEval, double currentEval);
	
	/**
	 * Met à jour la valeur d'Alpha
	 * @param evaluation L'évaluation courante du noeud
	 * @param alpha L'ancienne valeur d'Alpha
	 * @return La nouvelle valeur d'Alpha mise à jour selon le type de noeud
	 */
	protected abstract double updateAlpha(double evaluation, double alpha);
	
	/**
	 * Met à jour la valeur de Beta
	 * @param evaluation L'évaluation courante du noeud
	 * @param beta L'ancienne valeur de Beta
	 * @return La nouvelle valeur de Beta mise à jour selon le type de noeud
	 */
	protected abstract double updateBeta(double evaluation, double beta);
	
	/**
	 * Indique s'il faut faire une coupe alpha-beta
	 * @param evaluation L'évaluation courante du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 * @return Un booléen qui indique s'il faut faire une coupe alpha-beta
	 */
	protected abstract boolean shouldPrune(double evaluation, double alpha, double beta);
	
	/**
	 * Crée un noeud du niveau suivant (Min ou Max selon le type de noeud actuel)
	 * @param board L'état de la grille de jeu
	 * @param depth La profondeur du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 * @return Un noeud du niveau suivant (Min ou Max selon le type de noeud actuel)
	 */
	protected abstract BitMinMaxNode createNextNode(BitBoard board, int depth, double alpha, double beta);
}
