package awele.bot.competitor.noname.core.bitboard;

/**
 * @author Lucas Fagioli
 * Constantes pour la représentation bit à bit du plateau d'Awélé
 */
public final class BitConstants
{
	// ===== Constantes du jeu =====
	
	/**
	 * Nombre de trous par joueur
	 */
	public static final int NB_HOLES = 6;
	
	/**
	 * Nombre de graines initiales par trou
	 */
	public static final int INITIAL_SEEDS = 4;
	
	/**
	 * Nombre minimum de graines à capturer pour que la capture soit valide
	 */
	public static final int MIN_CAPTURE = 2;
	
	/**
	 * Nombre maximum de graines pouvant être capturées en un seul coup
	 */
	public static final int MAX_CAPTURE = 3;
	
	/**
	 * Score nécessaire pour gagner la partie
	 */
	public static final int WINNING_SCORE = 25;
	
	/**
	 * Nombre minimum de graines que le joueur doit avoir pour pouvoir continuer à jouer
	 */
	public static final int MIN_SEEDS_TO_CONTINUE = 6;
	
	/**
	 * Seuil de graines dans les trous d'origine pour décider de sauter l'origine lors de la distribution
	 */
	public static final int SKIP_ORIGIN_THRESHOLD = 12;
	
	// ===== Offsets des bits =====
	
	/**
	 * Offset pour les trous (bits 0 à 35)
	 */
	public static final int HOLES_OFFSET = 0;
	
	/**
	 * Nombre de bits par trou
	 */
	public static final int BITS_PER_HOLE = 6;
	
	/**
	 * Offset pour les scores (bits 36 à 41)
	 */
	public static final int SCORE_OFFSET = 36;
	
	/**
	 * Offset pour l'ID du propriétaire (bit 42)
	 */
	public static final int OWNER_OFFSET = 42;
	
	/**
	 * Offset pour les coups valides (bits 43 à 48)
	 */
	public static final int VALID_MOVES_OFFSET = 43;
	
	/**
	 * Offset pour le nombre de coups possibles (bits 49 à 51)
	 */
	public static final int NB_MOVES_OFFSET = 49;
	
	/**
	 * Offset pour le joueur actuel (bit 53)
	 */
	public static final int CURRENT_PLAYER_OFFSET = 53;
	
	/**
	 * Offset pour l'index de l'IA (bits 54)
	 */
	public static final int AI_INDEX_OFFSET = 54;
	
	/**
	 * Offset pour le premier coup (bits 55)
	 */
	public static final int FIRST_MOVE_OFFSET = 55;
	
	// ===== Masques de bits =====
	
	/**
	 * Masque pour extraire les graines d'un trou (6 bits)
	 */
	public static final long MASK_6_BITS = 0b111111L;
	
	/**
	 * Masque pour extraire les coups valides (3 bits)
	 */
	public static final long MASK_3_BITS = 0b111L;
	
	/**
	 * Masque pour extraire un seul bit
	 */
	public static final long MASK_1_BIT = 0b1L;
	
	// Empêche l'instanciation de la classe
	private BitConstants()
	{
		throw new AssertionError("Cette classe ne doit pas être instanciée.");
	}
}
