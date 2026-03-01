package awele.bot.competitor.noname.search.minmax;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MIN_SEEDS_TO_CONTINUE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.WINNING_SCORE;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.CategoryMoveOrdering;
import awele.bot.competitor.noname.ordering.KillerMoves;
import awele.bot.competitor.noname.ordering.MoveEvaluator;
import awele.bot.competitor.noname.ordering.PositionHistory;
import awele.bot.competitor.noname.search.pv.PVTable;
import awele.bot.competitor.noname.search.transposition.EntryType;
import awele.bot.competitor.noname.search.transposition.TranspositionEntry;
import awele.bot.competitor.noname.search.transposition.TranspositionTable;

/**
 * @author Lucas Fagioli
 * Classe abstraite représentant un noeud dans l'arbre de recherche MinMax pour le jeu Awélé.
 */
public abstract class NNMinMaxNode
{
	// ===== Variables statiques =====

	/**
	 * Profondeur maximale pour le LMR
	 */
	private static final int LMR_MAX_DEPTH = 64;

	/**
	 * Table de réduction pour le LMR
	 * (on la pré-calcule pour éviter les calculs de log à l'exécution)
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
	public static boolean timeExpired;

	/**
	 * Table de transposition partagée pour stocker les évaluations des positions
	 * déjà explorées
	 *
	 * Nouveau fonctionnement :
	 * - gameTable : utilisée pendant les parties (reset entre les games)
	 * - trainingTable : remplie pendant l'entraînement (persistante sur learn)
	 */
	public static TranspositionTable transpositionTable = new TranspositionTable(21, false);

	/**
	 * Indique si la recherche courante utilise la table d'entraînement
	 * (sinon, utilise la table de game).
	 */
	private static volatile boolean trainingMode = false;

	/**
	 * Évaluateur de position partagé pour calculer l'évaluation des positions de jeu
	 */
	public static PositionEvaluator positionEvaluator = new PositionEvaluator();

	/**
	 * Table de la variation principale
	 */
	public static PVTable pvTable = new PVTable();

	/**
	 * Ply actuel dans la recherche (distance depuis racne)
	 */
	public static int currentPly = 0;

	public static long nodeCount = 0;

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

	/**
	 * PV de ce noeud (sera propagée au parent)
	 */
	private final int[] localPV;

	/**
	 * Longueur de la PV locale
	 */
	private int localPVLength;

	// ===== Constructeur =====

	/**
	 * Constructeur
	 * @param board L'état de la grille de jeu
	 * @param depth La profondeur du noeud
	 * @param alpha Le seuil pour la coupe alpha
	 * @param beta Le seuil pour la coupe beta
	 */
	public NNMinMaxNode(BitBoard board, int depth, double alpha, double beta)
	{
		nodeCount++;

		this.decision = new double[NB_HOLES];
		this.interrupted = false;
		this.evaluation = getWorstScore();
		this.localPV = new int[50];
		this.localPVLength = 0;

		final long currentKey = board.ttKey();
		PositionHistory.push(currentKey);

		final int savedPly = currentPly;

		try
		{
			if (depth > 0 && isTimeExpired())
			{
				this.interrupted = true;
				return;
			}

			final double alpha0 = alpha;
			final double beta0 = beta;
			final int depthRemaining = Math.max(0, maxDepth - depth);
			final long key = board.ttKey();

			final TranspositionEntry tt = ttProbe(key);

			if (tt != null && tt.depth >= depthRemaining)
			{
				switch (tt.type)
				{
					case EXACT:
						if (depth > 0)
						{
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

				if (alpha >= beta && depth > 0)
				{
					this.evaluation = tt.evaluation;
					return;
				}
			}

			final int currentPlayer = board.getCurrentPlayer();

			int ttBestMove = -1;
			final int pvMove = pvTable.probe(maxDepth, currentPly);
			if (pvMove >= 0 && pvMove < NB_HOLES)
				ttBestMove = pvMove;
			else if (tt != null && tt.bestMove >= 0 && tt.bestMove < NB_HOLES)
				ttBestMove = tt.bestMove;

			final MoveEvaluator moveEvaluator = new MoveEvaluator(ttBestMove);
			int[] orderedMoves = moveEvaluator.orderMoves(board, currentPlayer, depthRemaining);

			double currentAlpha = alpha;
			double currentBeta = beta;
			final boolean isRootNode = (depth == 0);
			int bestMove = -1;
			boolean alreadyPenalized = false;

			NNMinMaxNode child = null;

			for (int moveIndex = 0; moveIndex < orderedMoves.length; moveIndex++)
			{
				final int i = orderedMoves[moveIndex];
				final int moveCategory = CategoryMoveOrdering.category(board, currentPlayer, i);

				if (depth <= 2 && isTimeExpired())
				{
					this.interrupted = true;
					break;
				}

				final boolean isCapture = board.simulateMoveScore(currentPlayer, i) > 0;
				final double[] decisionArray = new double[NB_HOLES];
				decisionArray[i] = 1.0;

				final BitBoard copy = board.clone();
				final int score = copy.playMove(decisionArray);

				double moveEvaluation;

				final int opponentScore = copy.getScore(1 - copy.getCurrentPlayer());
				final int totalSeeds = copy.getTotalSeeds(0) + copy.getTotalSeeds(1);

				if (score < 0 || opponentScore >= WINNING_SCORE || totalSeeds <= MIN_SEEDS_TO_CONTINUE)
					moveEvaluation = evaluatePosition(copy);
				else if (depth < maxDepth)
				{
					final int reduction = lmrReduction(depthRemaining, moveIndex, isCapture);
					int reducedDepth = depth + reduction + 1;

					if (reducedDepth > maxDepth)
						reducedDepth = maxDepth;

					currentPly++;
					child = createNextNode(copy, reducedDepth, currentAlpha, currentBeta);
					currentPly--;

					if (child.interrupted)
					{
						this.interrupted = true;
						break;
					}

					moveEvaluation = child.getEvaluation();

					if (reduction > 0 && moveEvaluation > currentAlpha + 1e-6)
					{
						currentPly++;
						child = createNextNode(copy, depth + 1, currentAlpha, currentBeta);
						currentPly--;

						if (child.interrupted)
						{
							this.interrupted = true;
							break;
						}

						moveEvaluation = child.getEvaluation();
					}
				}
				else
					moveEvaluation = evaluatePosition(copy);

				this.decision[i] = moveEvaluation;

				final double newEval = updateEvaluation(moveEvaluation, this.evaluation);
				if (Double.compare(newEval, this.evaluation) != 0)
				{
					this.evaluation = newEval;

					for (int j = 0; j < moveIndex; j++)
					{
						final int previousHole = orderedMoves[j];
						final int previousCat = CategoryMoveOrdering.category(board, currentPlayer, previousHole);
						CategoryMoveOrdering.removeScore(previousCat, 1);
					}

					CategoryMoveOrdering.addScore(moveCategory, Math.max(1, 6 - moveIndex));

					bestMove = i;

					localPV[0] = i;
					localPVLength = 1;
					if (child != null && child.localPVLength > 0)
					{
						int copyLength = Math.min(child.localPVLength, localPV.length - 1);
						for (int k = 0; k < copyLength; k++)
						{
							localPV[1 + k] = child.localPV[k];
						}
						localPVLength = 1 + copyLength;
					}

					alreadyPenalized = true;
				}

				if (!isRootNode)
				{
					currentAlpha = updateAlpha(this.evaluation, currentAlpha);
					currentBeta = updateBeta(this.evaluation, currentBeta);

					if (shouldPrune(this.evaluation, currentAlpha, currentBeta))
					{
						if (!alreadyPenalized)
						{
							for (int j = 0; j < moveIndex; j++)
							{
								final int previousHole = orderedMoves[j];
								final int previousCat = CategoryMoveOrdering.category(board, currentPlayer, previousHole);
								CategoryMoveOrdering.removeScore(previousCat, 1);
							}
						}

						if (MoveEvaluator.ENABLE_CATEGORY_ORDERING)
							CategoryMoveOrdering.addScore(moveCategory, 5);

						if (!isCapture && depthRemaining >= 2 && depthRemaining <= 12 && MoveEvaluator.ENABLE_KILLERS)
							KillerMoves.record(depthRemaining, i);

						break;
					}
				}

				alreadyPenalized = false;
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

				ttStore(key, this.evaluation, depthRemaining, type, bestMove);

				if (depth == 0 && localPVLength > 0)
					pvTable.store(maxDepth, 0, localPV[0], localPV, localPVLength);
			}
		}
		finally
		{
			PositionHistory.pop(currentKey);
			currentPly = savedPly;
		}
	}

	// ===== Accesseurs =====

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
	 * Permet de basculer la recherche sur la table d'entraînement.
	 * À utiliser uniquement pendant la phase learn().
	 */
	public static void setTrainingMode(boolean enable)
	{
		trainingMode = enable;
	}

	public static boolean isTrainingMode()
	{
		return trainingMode;
	}

	private static TranspositionEntry ttProbe(long key)
	{
		return transpositionTable.probe(key);
	}

	private static void ttStore(long key, double evaluation, int depthRemaining, EntryType type, int bestMove)
	{
		transpositionTable.store(key, evaluation, depthRemaining, type, bestMove);
	}

	/**
	 * Initialisation des paramètres statiques
	 * @param board Le plateau de départ
	 * @param depth La profondeur maximale pour la recherche MinMax
	 */
	public static void initialize(BitBoard board, int depth)
	{
		NNMinMaxNode.maxDepth = depth;
		NNMinMaxNode.player = board.getCurrentPlayer();
		KillerMoves.initialize(depth);
	}

	/**
	 * Initialisation du timer pour la recherche MinMax
	 * @param maxTimeMs Le temps maximum autorisé pour la recherche en millisecondes
	 */
	public static void startTimer(long maxTimeMs)
	{
		NNMinMaxNode.searchStartTime = System.nanoTime();
		NNMinMaxNode.maxSearchTime = maxTimeMs * 1_000_000L;
		NNMinMaxNode.timeExpired = false;
	}

	/**
	 * Reset le timer
	 */
	public static void resetTimer()
	{
		NNMinMaxNode.searchStartTime = 0;
		NNMinMaxNode.maxSearchTime = Long.MAX_VALUE;
		NNMinMaxNode.timeExpired = false;
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
	 * Calcule l'évaluation d'une position
	 * @param board L'état de la grille de jeu
	 * @return L'évaluation de la position
	 */
	private double evaluatePosition(BitBoard board)
	{
		double baseEval = positionEvaluator.evaluate(board, player);

		final long key = board.ttKey();
		final int repetitions = PositionHistory.count(key);

		if (repetitions >= 3)
			return 0.0;

		if (repetitions >= 2)
			baseEval -= 30.0;

		return baseEval;
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

	private static int[][] buildLmrTable()
	{
		// Table [depth][moveIndex]
		final int maxMoves = 32; // marge (Awélé = 6 coups max, mais on garde large)
		final int[][] table = new int[LMR_MAX_DEPTH + 1][maxMoves + 1];

		for (int depth = 0; depth <= LMR_MAX_DEPTH; depth++)
		{
			for (int moveIndex = 0; moveIndex <= maxMoves; moveIndex++)
			{
				if (depth < 3 || moveIndex < 3)
				{
					table[depth][moveIndex] = 0;
				}
				else
				{
					// Formule simple empirique (déjà dans ton code : table pré-calculée)
					// On conserve la même logique : plus depth et moveIndex sont grands, plus on réduit.
					int r = (int) (Math.log(depth) * Math.log(moveIndex));
					if (r < 0) r = 0;
					table[depth][moveIndex] = r;
				}
			}
		}

		return table;
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
	protected abstract NNMinMaxNode createNextNode(BitBoard board, int depth, double alpha, double beta);
}