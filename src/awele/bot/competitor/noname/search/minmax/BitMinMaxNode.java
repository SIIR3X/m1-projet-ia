package awele.bot.competitor.noname.search.minmax;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MIN_SEEDS_TO_CONTINUE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.WINNING_SCORE;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.MoveEvaluator;
import awele.bot.competitor.noname.search.transposition.EntryType;
import awele.bot.competitor.noname.search.transposition.TranspositionEntry;
import awele.bot.competitor.noname.search.transposition.TwoLevelTranspositionTable;

/**
 * @author Lucas Fagioli
 * Classe abstraite représentant un noeud dans l'arbre de recherche MinMax pour le jeu Awélé.
 */
public abstract class BitMinMaxNode
{
	// ===== Variables statiques =====

	/**
	 * Profondeur maximale pour le LMR
	 */
	private static final int LMR_MAX_DEPTH = 64;
	
	/**
	 * Table de réduction pour le LMR
	 * (pré-calculée pour éviter les calculs de log à l'exécution)
	 */
	private static final int[][] LMR_TABLE = buildLmrTable();
	
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
	public static long searchStartTime;

	/**
	 * Durée maximale autorisée pour la recherche
	 */
	public static long maxSearchTime;

	/**
	 * Flag indiquant si le temps est écoulé
	 */
	public static volatile boolean timeExpired;

	/**
	 * Table de transposition partagée pour stocker les évaluations des positions
	 * déjà explorées
	 */
	public static TwoLevelTranspositionTable transpositionTable = new TwoLevelTranspositionTable(22, 19, true);
	
	/**
	 * Évaluateur de position partagé pour calculer l'évaluation des positions de jeu
	 */
	public static PositionEvaluator positionEvaluator = new PositionEvaluator();
	
	// ===== Variables d'instance =====

	/**
	 * Évaluation du noeud : estimation de la valeur de la position pour le joueur
	 * actuel
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
	
	public static long nodeCount = 0;

	/**
	 * Constructeur
	 * @param board L'état de la grille de jeu
	 * @param depth La profondeur du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 */
	public BitMinMaxNode(BitBoard board, int depth, double alpha, double beta)
	{
		nodeCount++;
		
		this.decision = new double[NB_HOLES];
		this.interrupted = false;
		this.evaluation = getWorstScore();

		if (depth > 0 && isTimeExpired()) {
			this.interrupted = true;
			return;
		}

		// Ici on garde la fenêtre originale pour déterminer le flag TT en sortie
		final double alpha0 = alpha;
		final double beta0 = beta;

		// Profondeur restante
		final int depthRemaining = Math.max(0, maxDepth - depth);

		final long key = board.ttKey();
		final TranspositionEntry tt = transpositionTable.probe(key);

		if (tt != null && tt.depth >= depthRemaining) {
			switch (tt.type) {
			case EXACT:
				// Hors racine : retour immédiat
				if (depth > 0) {
					this.evaluation = tt.evaluation;
					return;
				}
				break;

			case LOWER_BOUND:
				alpha = Math.max(alpha, tt.evaluation);
				break;

			case UPPER_BOUND:
				beta = Math.min(beta, tt.evaluation);
				break;

			default:
				break;
			}

			if (alpha >= beta && depth > 0) {
				this.evaluation = tt.evaluation;
				return;
			}
		}

		final int currentPlayer = board.getCurrentPlayer();
		
		int ttBestMove = (tt != null && tt.bestMove >= 0 && tt.bestMove < NB_HOLES) ? tt.bestMove : -1;

		// On utilise le MoveEvaluator pour ordonner les coups par qualité
		// avant de les explorer
		final MoveEvaluator moveEvaluator = new MoveEvaluator(ttBestMove);
		int[] orderedMoves = moveEvaluator.orderMoves(board, currentPlayer);

		double currentAlpha = alpha;
		double currentBeta = beta;
		final boolean isRootNode = (depth == 0);
		int bestMove = -1;
		
		// On parcours les coups ordonnés pour explorer les branches les plus
		// prometteuses en premier
		for (int moveIndex = 0; moveIndex < orderedMoves.length; moveIndex++) {
			final int i = orderedMoves[moveIndex];

			// Si le temps de recherche est écoulé à une profondeur critique (profondeur 2
			// ou moins),
			// on interrompt la recherche pour éviter de dépasser le temps imparti
			if (depth <= 2 && isTimeExpired()) {
				this.interrupted = true;
				break;
			}

			final boolean isCapture = board.simulateMoveScore(currentPlayer, i) > 0;
			final double[] decisionArray = new double[NB_HOLES];
			decisionArray[i] = 1.0;

			final BitBoard copy = board.clone();
			final int score = copy.playMove(decisionArray);

			// On calcul l'évaluation pour ce coup
			double moveEvaluation;

			// On vérifie les conditions de fin de partie
			final int opponentScore = copy.getScore(1 - copy.getCurrentPlayer());
			final int totalSeeds = copy.getTotalSeeds(0) + copy.getTotalSeeds(1);

			if (score < 0 || opponentScore >= WINNING_SCORE || totalSeeds <= MIN_SEEDS_TO_CONTINUE)
				// Fin de partie détectée : évaluation directe
				moveEvaluation = evaluatePosition(copy);
			else if (depth < maxDepth)
			{
				// J'applique LMR seulement aux coups tardifs, non capturants
				final int reduction = lmrReduction(depthRemaining, moveIndex, isCapture);
				
				int reducedDepth = depth + reduction + 1;
				
				if (reducedDepth > maxDepth)
					reducedDepth = maxDepth;
				
				BitMinMaxNode child = createNextNode(copy, reducedDepth, currentAlpha, currentBeta);

				// Si la recherche a été interrompue dans le noeud fils, on propage
				// l'interruption vers le haut pour arrêter toute la recherche
				if (child.interrupted)
				{
					this.interrupted = true;
					break;
				}

				moveEvaluation = child.getEvaluation();
				
				if (reduction > 0 && moveEvaluation > currentAlpha + 1e-6)
				{
					child = createNextNode(copy, depth + 1, currentAlpha, currentBeta);
					
					if (child.interrupted)
					{
						this.interrupted = true;
						break;
					}
					
					moveEvaluation = child.getEvaluation();
				}
			}
			else
				// Profondeur maximale atteinte : évaluation de la position
				moveEvaluation = evaluatePosition(copy);

			this.decision[i] = moveEvaluation;

			final double newEval = updateEvaluation(moveEvaluation, this.evaluation);
			if (Double.compare(newEval, this.evaluation) != 0) {
				this.evaluation = newEval;
				bestMove = i;
			}

			// Élagage Alpha-Beta
			if (!isRootNode) {
				currentAlpha = updateAlpha(this.evaluation, currentAlpha);
				currentBeta = updateBeta(this.evaluation, currentBeta);

				// Vérification de la condition de coupe
				if (shouldPrune(this.evaluation, currentAlpha, currentBeta))
					break;
			}
		}

		if (!this.interrupted)
		{
			final EntryType type;

			if (this.evaluation <= alpha0)
				type = EntryType.UPPER_BOUND;
			else if (this.evaluation >= beta0)
				type = EntryType.LOWER_BOUND;
			else
				type = EntryType.EXACT;

			transpositionTable.store(key, this.evaluation, depthRemaining, type, bestMove);
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
		return positionEvaluator.evaluate(board, player);
	}
	
	/**
	 * Calcule la réduction de profondeur à appliquer pour un coup donné dans le cadre du LMR
	 * @param depthRemaining La profondeur restante à explorer pour ce noeud
	 * @param moveIndex L'index du coup dans l'ordre d'exploration
	 * @param isCapture Un booléen qui indique si le coup est un coup de capture (score > 0) ou non
	 * @return La réduction de profondeur à appliquer pour ce coup dans le cadre du LMR
	 */
	private static int lmrReduction(int depthRemaining, int moveIndex, boolean isCapture)
	{
	    if (isCapture)
	        return 0;

	    if (depthRemaining < 3)
	        return 0;

	    if (moveIndex < 3)
	        return 0;

	    int d = depthRemaining;
	    if (d > LMR_MAX_DEPTH)
	        d = LMR_MAX_DEPTH;

	    int r = LMR_TABLE[d][moveIndex];

	    if (r > depthRemaining - 1)
	        r = depthRemaining - 1;

	    return r;
	}

	/**
	 * Construit la table de réduction pour le LMR en pré-calculant les valeurs
	 * de réduction en fonction de la profondeur et de l'index du coup
	 * @return La table de réduction pour le LMR, où LMR_TABLE[d][m]
	 * 	donne la réduction à appliquer pour un coup d'index m à une profondeur restante d
	 */
	private static int[][] buildLmrTable()
	{
	    int[][] t = new int[LMR_MAX_DEPTH + 1][NB_HOLES + 1];

	    for (int d = 0; d <= LMR_MAX_DEPTH; d++)
	    {
	        for (int m = 0; m <= NB_HOLES; m++)
	        {
	            // m = moveIndex (0..5)
	        		// repri de Stockfish
	            double ld = Math.log(d + 1.0);
	            double lm = Math.log(m + 2.0);

	            int r = (int)Math.floor(0.70 * ld * lm - 0.90);

	            if (r < 0)
	            		r = 0;
	            if (r > 3)
	            		r = 3;
	            t[d][m] = r;
	        }
	    }

	    return t;
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
	 * @param beta  Le seuil pour la coupe beta
	 * @return Un noeud du niveau suivant (Min ou Max selon le type de noeud actuel)
	 */
	protected abstract BitMinMaxNode createNextNode(BitBoard board, int depth, double alpha, double beta);
}
