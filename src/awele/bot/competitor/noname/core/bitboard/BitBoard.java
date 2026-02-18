package awele.bot.competitor.noname.core.bitboard;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.BITS_PER_HOLE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.FIRST_MOVE_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.HOLES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_1_BIT;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_3_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_6_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MAX_CAPTURE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MIN_CAPTURE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MIN_SEEDS_TO_CONTINUE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_MOVES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.OWNER_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.VALID_MOVES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.WINNING_SCORE;

/**
 * @author Lucas Fagioli
 * Représentation bit à bit du plateau d'Awélé sur 2 longs (128 bits)
 * 
 * Encodage :
 * - Bits 0-35   : Trous (6 trous x 6 bits par trou)
 * - Bits 36-41  : Score du joueur (6 bits, max 63)
 * - Bit 42      : ID du propriétaire du plateau (0 ou 1)
 * - Bits 43-48  : Coups valides (6 bits, 1 par trou)
 * - Bits 49-51  : Nombre de coups possibles (3 bits)
 * - Bit 53      : Joueur actuel (0 ou 1)
 * - Bit 54      : Index de l'IA (0 ou 1)
 * - Bit 55      : Premier coup du joueur
 */
public final class BitBoard
{
	// Représentation du pleau sur 2 longs
	private long[] boards;
	
	public BitBoard()
	{
		this.boards = new long[2];
		this.initialize();
	}
	
	/**
	 * Constructeur privé pour clonage
	 * @param boards Tableau de longs représentant le plateau
	 */
	private BitBoard(long[] boards)
	{
		this.boards = boards.clone();
	}
	
	/**
	 * Initialise le plateau avec la configuration de départ standard
	 */
	public void initialize()
	{
		this.boards[0] = 0L; // Joueur 1
		this.boards[1] = 0L; // Joueur 2
		
		// Initialisation des trous avec le nombre de graines initial
		for (int i = 0; i < BitConstants.NB_HOLES; i++)
		{
			this.setSeeds(0, i, BitConstants.INITIAL_SEEDS);
			this.setSeeds(1, i, BitConstants.INITIAL_SEEDS);
		}
		
		// Propriétaires
		this.boards[1] |= (1L << OWNER_OFFSET);
		
		// Premier coup pour les deux joueurs
		this.boards[0] |= (1L << FIRST_MOVE_OFFSET);
		this.boards[1] |= (1L << FIRST_MOVE_OFFSET);
		
		// Calcule les coups valides
		calculateValidMoves(0);
		calculateValidMoves(1);
	}
	
	// ===== Opérations sur les trous =====
	
	/**
	 * Récupère le nombre de graines dans un trou donné pour un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @param hole L'identifiant du trou (0 à 5)
	 * @return Le nombre de graines dans le trou spécifié
	 */
	public int getSeeds(int player, int hole)
	{
		int offset = HOLES_OFFSET + (hole * BITS_PER_HOLE);
		
		return (int)((this.boards[player] >> offset) & MASK_6_BITS);
	}
	
	/**
	 * Met à jour le nombre de graines dans un trou donné pour un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @param hole L'identifiant du trou (0 à 5)
	 * @param seeds Le nombre de graines à mettre dans le trou spécifié
	 */
	private void setSeeds(int player, int hole, int seeds)
	{
		int offset = HOLES_OFFSET + (hole * BITS_PER_HOLE);
		
		// On efface d'abord les graines existantes dans le trou
		this.boards[player] &= ~(MASK_6_BITS << offset);
		
		// On peut ensuite ajouter les nouvelles graines
		this.boards[player] |= ((long)seeds & MASK_6_BITS) << offset;
	}
	
	/**
	 * Ajoute des graines dans un trou donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @param hole L'identifiant du trou (0 à 5)
	 * @param amount Le nombre de graines à ajouter dans le trou spécifié
	 */
	private void addSeeds(int player, int hole, int amount)
	{
		setSeeds(player, hole, getSeeds(player, hole) + amount);
	}
	
	/**
	 * Récupère le nombre de graines dans tous les trous d'un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @return Un tableau contenant le nombre de graines dans chaque trou du joueur spécifié
	 */
	public int[] getHoles(int player)
	{
		int[] holes = new int[BitConstants.NB_HOLES];
		
		for (int i = 0; i < BitConstants.NB_HOLES; i++)
		{
			holes[i] = getSeeds(player, i);
		}
		
		return holes;
	}
	
	/**
	 * Calcule le nombre total de graines dans tous les trous d'un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @return Le nombre total de graines dans les trous du joueur spécifié
	 */
	public int getTotalSeeds(int player)
	{
		int total = 0;
		
		for (int i = 0; i < BitConstants.NB_HOLES; i++)
		{
			total += getSeeds(player, i);
		}
		
		return total;
	}
	
	/**
	 * Vérifie si un joueur n'a plus de graines
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @return true si le joueur n'a plus de graines, false sinon
	 */
	private boolean isEmpty(int player)
	{
		return getTotalSeeds(player) == 0;
	}
	
	// ===== Opérations sur les scores =====
	
	/**
	 * Récupère le score d'un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @return Le score du joueur spécifié
	 */
	public int getScore(int player)
	{
		int offset = BitConstants.SCORE_OFFSET;
		
		return (int)((this.boards[player] >> offset) & MASK_6_BITS);
	}
	
	/**
	 * Définit le score d'un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @param score Le score à attribuer au joueur spécifié
	 */
	private void setScore(int player, int score)
	{
		this.boards[player] &= ~(MASK_6_BITS << BitConstants.SCORE_OFFSET);
		this.boards[player] |= ((long)score & MASK_6_BITS) << BitConstants.SCORE_OFFSET;
	}
	
	/**
	 * Ajoute des points au score d'un joueur donné
	 * @param player L'identifiant du joueur (0 ou 1)
	 * @param points Le nombre de points à ajouter au score du joueur spécifié
	 */
	private void addScore(int player, int points)
	{
		setScore(player, getScore(player) + points);
	}
	
	/**
	 * Récupère les scores des deux joueurs
	 * @return Un tableau contenant les scores des deux joueurs, où l'index 0 correspond au joueur 1 et l'index 1 correspond au joueur 2
	 */
	public int[] getScores()
	{
		return new int[] { getScore(0), getScore(1) };
	}
	
	// ===== Opérations sur les joueurs =====
	
	/**
	 * Récupère l'identifiant du joueur actuel
	 * @return L'identifiant du joueur actuel (0 ou 1)
	 */
	public int getCurrentPlayer()
	{
		return (int)((this.boards[0] >> BitConstants.CURRENT_PLAYER_OFFSET) & MASK_1_BIT);
	}
	
	/**
	 * Change le joueur actuel (de 0 à 1 ou de 1 à 0)
	 */
	private void switchPlayer()
	{
		this.boards[0] ^= (MASK_1_BIT << BitConstants.CURRENT_PLAYER_OFFSET);
	}
	
	/**
	 * Récupère l'identifiant de l'autre joueur
	 * @param player L'identifiant du joueur pour lequel on veut connaître l'autre joueur (0 ou 1)
	 * @return L'identifiant de l'autre joueur (1 ou 0)
	 */
	private static int others(int player)
	{
		return 1 - player;
	}
	
	// ===== Opérations sur les coups valides =====
	
	/**
	 * Calcule et définit les coups valides pour un joueur donné, en fonction de la configuration actuelle du plateau
	 * @param player L'identifiant du joueur pour lequel on veut calculer les coups valides (0 ou 1)
	 */
	private void calculateValidMoves(int player)
	{
		boolean opponentHasSeeds = !isEmpty(others(player));
		int nbValid = 0;
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			int seeds = getSeeds(player, i);
			boolean valid = (seeds > 0) && (opponentHasSeeds || (i + seeds >= NB_HOLES));
			
			// Définit le bit de validité
			int offset = VALID_MOVES_OFFSET + i;
			this.boards[player] &= ~(MASK_1_BIT << offset);
			this.boards[player] |= ((long)(valid ? 1 : 0) << offset);
			
			if (valid)
				nbValid++;
		}
		
		// Définit le nombre de coups valides
		this.boards[player] &= ~(MASK_3_BITS << BitConstants.NB_MOVES_OFFSET);
		this.boards[player] |= ((long)nbValid & MASK_3_BITS) << BitConstants.NB_MOVES_OFFSET;
	}
	
	/**
	 * Récupère tous les coups valides
	 * @param player L'identifiant du joueur pour lequel on veut récupérer les coups valides (0 ou 1)
	 * @return Un tableau de booléens indiquant quels trous sont valides pour le joueur spécifié, où l'index 0 correspond au trou 1 et l'index 5 correspond au trou 6
	 */
	public boolean[] getValidMoves(int player)
	{
		boolean[] valid = new boolean[NB_HOLES];
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			int offset = VALID_MOVES_OFFSET + i;
			
			valid[i] = ((this.boards[player] >> offset) & MASK_1_BIT) == 1L;
		}
		
		return valid;
	}
	
	/**
	 * Vérifie si un coup est valide
	 * @param player L'identifiant du joueur pour lequel on veut vérifier la validité du coup (0 ou 1)
	 * @param hole L'identifiant du trou pour lequel on veut vérifier la validité du coup (0 à 5)
	 * @return true si le coup est valide, false sinon
	 */
	private boolean isValidMove(int player, int hole)
	{
		int offset = VALID_MOVES_OFFSET + hole;
		
		return ((this.boards[player] >> offset) & MASK_1_BIT) == 1L;
	}
	
	/**
	 * Sélectionne le meilleur coup parmi les coups valides, en fonction des valeurs de décision fournies pour chaque trou
	 * @param decisions Un tableau de doubles représentant les valeurs de décision pour chaque trou, où l'index 0 correspond au trou 1 et l'index 5 correspond au trou 6
	 * @return L'identifiant du trou correspondant au meilleur coup parmi les coups valides, ou -1 si aucun coup n'est valide
	 */
	public int selectBestMove(double[] decisions)
	{
		int player = getCurrentPlayer();
		int bestMove = -1;
		double bestValue = Double.NEGATIVE_INFINITY;
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			if (isValidMove(player, i) && decisions[i] > bestValue)
			{
				bestMove = i;
				bestValue = decisions[i];
			}
		}
		
		return bestMove;
	}
	
	// ===== Distribution des graines =====
	
	/**
	 * Distribue les graines d'un trou
	 * @param player L'identifiant du joueur qui effectue la distribution (0 ou 1)
	 * @param hole L'identifiant du trou à partir duquel les graines sont distribuées (0 à 5)
	 * @return Un tableau contenant l'identifiant du joueur et du trou où la dernière graine a été déposée, sous la forme [player, hole]
	 */
	private int[] distribute(int player, int hole)
	{
		int seeds = getSeeds(player, hole);
		setSeeds(player, hole, 0);
		
		int currentHole = hole;
		int currentPlayer = player;

		while (seeds > 0)
		{
			currentHole++;
			
			if (currentHole >= NB_HOLES)
			{
				currentPlayer = others(currentPlayer);
				currentHole = 0;
			}
			
			if (currentPlayer == player && currentHole == hole)
				continue;
			
			addSeeds(currentPlayer, currentHole, 1);
			seeds--;
		}
		
		return new int[] { currentPlayer, currentHole };
	}
	
	// ===== Calcul des captures =====
	
	/**
	 * Vérifie si un nombre de graines est capturable
	 * @param seeds Le nombre de graines à vérifier
	 * @return true si le nombre de graines est capturable (c'est-à-dire égal à MIN_CAPTURE ou MAX_CAPTURE), false sinon
	 */
	private static boolean isCapturable(int seeds)
	{
		return seeds == MIN_CAPTURE || seeds == MAX_CAPTURE;
	}
	
	/**
	 * Vérifie si la capture affamerait l'adversaire
	 * @param opponent L'identifiant de l'adversaire (0 ou 1)
	 * @param finalHole L'identifiant du trou où la dernière graine a été déposée lors de la distribution
	 * @return true si la capture affamerait l'adversaire (c'est-à-dire que tous les trous de l'adversaire contiendraient un nombre de graines capturable), false sinon
	 */
	private boolean wouldStarveOpponent(int opponent, int finalHole)
	{
		for (int i = 0; i <= finalHole; i++)
		{
			int seeds = getSeeds(opponent, i);
			
			if (seeds == 1 || seeds > MAX_CAPTURE)
				return false;
		}
		
		for (int i = finalHole + 1; i < NB_HOLES; i++)
		{
			int seeds = getSeeds(opponent, i);
			
			if (seeds != 0)
				return false;
		}
		
		return true;
	}
	
	/**
	 * Calcule et exécute les captures
	 * @param player L'identifiant du joueur qui effectue la capture (0 ou 1)
	 * @param finalPlayer L'identifiant du joueur chez qui la dernière graine a été déposée lors de la distribution
	 * @param finalHole L'identifiant du trou où la dernière graine a été déposée lors de la distribution
	 * @return Le nombre de graines capturées lors de ce coup
	 */
	private int calculateCaptures(int player, int finalPlayer, int finalHole)
	{
		int opponent = others(player);
		
		// Pas de capture si pas chez l'adversaire
		if (finalPlayer != opponent)
			return 0;
		
		// Vérification du trou final
		int seedsInFinal = getSeeds(opponent, finalHole);
		if (!isCapturable(seedsInFinal))
			return 0;
		
		// Vérification anti-starvation
		if (wouldStarveOpponent(opponent, finalHole))
			return 0;
		
		// Capture en cascade
		int captures = 0;
		int currentHole = finalHole;
		
		while (currentHole >= 0)
		{
			int seeds = getSeeds(opponent, currentHole);
			
			if (!isCapturable(seeds))
				break;
			
			captures += seeds;
			setSeeds(opponent, currentHole, 0);
			currentHole--;
		}
		
		return captures;
	}
	
	// ===== Exécution d'un coup =====
	
	/**
	 * Joue un coup complet
	 * @param player L'identifiant du joueur qui effectue le coup (0 ou 1)
	 * @param hole L'identifiant du trou à partir duquel le coup est joué (0 à 5)
	 * @return Le nombre de graines capturées lors de ce coup, ou -1 si le coup était invalide (dans ce cas, toutes les graines restantes de l'adversaire sont capturées)
	 */
	public int playMove(int player, int hole)
	{
		if (!isValidMove(player, hole))
		{
			// Coup invalide : on récupère toutes les graines restantes
			int currentPlayer = getCurrentPlayer();
			int remaining = getTotalSeeds(currentPlayer);
			
			addScore(currentPlayer, remaining);
			
			for (int i = 0; i < NB_HOLES; i++)
			{
				setSeeds(currentPlayer, i, 0);
			}
			
			return -1;
		}
		
		// Masque que le premier coup a été joué
		this.boards[player] &= ~(MASK_1_BIT << FIRST_MOVE_OFFSET);
		
		// Distribution des graines
		int[] finalPos = distribute(player, hole);
		int finalPlayer = finalPos[0];
		int finalHole = finalPos[1];
		
		// Captures
		int captured = calculateCaptures(player, finalPlayer, finalHole);
		if (captured > 0)
		{
			addScore(player, captured);
		}
		
		// Change de joueur
		switchPlayer();
		
		// Recalcule les coups valides
		calculateValidMoves(0);
		calculateValidMoves(1);
		
		return captured;
	}
	
	/**
	 * Joue un coup à partir d'un tableau de décisions
	 * @param decisions Un tableau de doubles représentant les valeurs de décision pour chaque trou, où l'index 0 correspond au trou 1 et l'index 5 correspond au trou 6
	 * @return Le nombre de graines capturées lors de ce coup, ou -1 si aucun coup n'était valide (dans ce cas, toutes les graines restantes de l'adversaire sont capturées)
	 */
	public int playMove(double[] decisions)
	{
		int player = getCurrentPlayer();
		int bestMove = selectBestMove(decisions);
		
		if (bestMove == -1)
		{
			int remaining = getTotalSeeds(player);
			
			addScore(player, remaining);
			
			for (int i = 0; i < NB_HOLES; i++)
			{
				setSeeds(player, i, 0);
			}
			
			return -1;
		}
		
		return playMove(player, bestMove);
	}
	
	// ===== Simulation =====
	
	/**
	 * Simule un coup et retourne le nouveau plateau
	 * @param player L'identifiant du joueur qui effectue le coup (0 ou 1)
	 * @param hole L'identifiant du trou à partir duquel le coup est joué (0 à 5)
	 * @return Un nouveau BitBoard représentant l'état du plateau après avoir joué le coup spécifié, ou null si le coup était invalide
	 */
	public BitBoard simulateMove(int player, int hole)
	{
		BitBoard copy = this.clone();
		copy.playMove(player, hole);
		return copy;
	}
	
	/**
	 * Simule un coup et retourne le score obtenu
	 * @param player L'identifiant du joueur qui effectue le coup (0 ou 1)
	 * @param hole L'identifiant du trou à partir duquel le coup est joué (0 à 5)
	 * @return Le nombre de graines capturées lors de ce coup, ou -1 si le coup était invalide (dans ce cas, toutes les graines restantes de l'adversaire sont capturées)
	 */
	public int simulateMoveScore(int player, int hole)
	{
		BitBoard clone = this.clone();
		return clone.playMove(player, hole);
	}
	
	// ===== Fin de partie =====
	
	/**
	 * Vérifie si la partie est terminée
	 * @return true si la partie est terminée (c'est-à-dire si un joueur a atteint le score gagnant, ou si les deux joueurs ont moins de graines que le minimum pour continuer, ou s'il n'y a plus de coups valides), false sinon
	 */
	public boolean isGameOver()
	{
		// Score gagnant atteint
		if (getScore(0) >= WINNING_SCORE || getScore(1) >= WINNING_SCORE)
			return true;
		
		// Moins de 6 graines
		if (getTotalSeeds(0) + getTotalSeeds(1) <= MIN_SEEDS_TO_CONTINUE)
			return true;
		
		int current = getCurrentPlayer();
		
		// Pas de coups valides
		return (int)((this.boards[current] >> NB_MOVES_OFFSET) & MASK_3_BITS) == 0;
	}
	
	/**
	 * Détermine le gagnant de la partie
	 * @return L'identifiant du joueur gagnant (0 ou 1), ou -1 en cas d'égalité
	 */
	public int getWinner()
	{
		int score1 = getScore(0);
		int score2 = getScore(1);
		
		if (score1 > score2)
			return 0;
		else if (score2 > score1)
			return 1;
		else
			return -1;
	}
	
	// ===== Clonage =====
	
	@Override
	public BitBoard clone()
	{
		return new BitBoard(this.boards);
	}
	
	/**
	 * Récupère une copie des longs internes
	 * @return Un tableau de longs représentant l'état du plateau, où l'index 0 correspond au joueur 1 et l'index 1 correspond au joueur 2
	 */
	public long[] getBoardsArray()
	{
		return this.boards.clone();
	}
	
	/**
	 * Définit les longs internes
	 * @param boards Un tableau de longs représentant l'état du plateau, où l'index 0 correspond au joueur 1 et l'index 1 correspond au joueur 2. Le tableau doit avoir une longueur de 2.
	 */
	public void setBoardsArray(long[] boards)
	{
		if (boards.length != 2)
			throw new IllegalArgumentException("Le tableau de longs doit avoir une longueur de 2.");
		
		this.boards = boards.clone();
	}
	
	// ===== Table de transposition =====
	
	public long raw0()
	{
		return this.boards[0];
	}
	
	public long raw1()
	{
		return this.boards[1];
	}
	
	/**
	 * Génère une clé de table de transposition à partir des longs internes du plateau
	 * @return Une clé de table de transposition générée à partir des longs internes du plateau, qui peut être utilisée pour stocker et récupérer des entrées dans une table de transposition
	 */
	public long ttKey()
	{
		long a = mix64(this.boards[0]);
		long b = mix64(this.boards[1]);
		return a ^ Long.rotateLeft(b, 1);
	}
	
	/**
	 * Mélange les bits d'un long pour obtenir une clé de table de transposition plus uniforme
	 * @param z Le long à mélanger
	 * @return Un long mélangé, qui peut être utilisé comme clé de table de transposition
	 */
	private static long mix64(long z)
	{
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
	
	// ===== Affichage =====
	
	@Override
	public String toString()
	{
        StringBuilder sb = new StringBuilder();
        
        // Trous joueur 2 (inversés)
        sb.append("|");
        int[] holes2 = getHoles(1);
        for (int i = NB_HOLES - 1; i >= 0; i--)
        {
            if (holes2[i] < 10) sb.append(" ");
            sb.append(holes2[i]).append("|");
        }
        sb.append("\n");
        
        // Trous joueur 1
        sb.append("|");
        int[] holes1 = getHoles(0);
        for (int i = 0; i < NB_HOLES; i++)
        {
            if (holes1[i] < 10) sb.append(" ");
            sb.append(holes1[i]).append("|");
        }
        sb.append("\n");
        
        // Scores
        sb.append("Score : ").append(getScore(0)).append(" - ").append(getScore(1));
        
        return sb.toString();
	}
}
