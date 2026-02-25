package awele.bot.competitor.noname.training;

import java.util.Random;

import awele.bot.competitor.noname.evaluation.PositionEvaluator;

/**
 * @author Lucas Fagioli
 * Sélecteur adaptatif de profils d'adversaires pour l'entraînement SPSA
 */
public final class AdaptiveOpponentSelector
{
	/**
	 * Archétypes stratégiques de l'Awélé
	 * Chaque profil correspond à un style de jeu
	 */
	public enum OpponentProfile
	{
		/** Score pur : diif(score_candidat - score_adverse) */
		BALANCED,
		
		/** Offensif : maximise les captures immédiates */
		ATTACKER,
		
		/** Défensif : protège ses trous vulénables (1-2 graines) */
		DEFENDER,
		
		/** Accumulateur : construit des krous */
		HOARDER,
		
		/** Affameur : vide les trous adverses, force les positions de starvation */
		STARVER,
		
		/** Self-play : adversaire avec les poids actuels du candidat */
		SELF,
		
		/**
		 * Boss : adversaire fixe jouant à haute profondeur avec DiffScore pur
		 * Est toujours présent dans la sélection
		 * Rapporte deux fois plus de points
		 */
		BOSS
	};
	
	// ===== Poids =====
	
	/**
	 * Poids caractéristiques de chaque profil
	 */
	private static final double[][] PROFILE_WEIGHTS = {
        { 10.0, 8.0, 0.5, 7.0, 5.0, 14.0, 5.0, 3.5, 6.0, 6.0 }, // BALANCED
        { 9.0, 6.0, 0.8, 5.0, 4.0, 13.0, 4.0, 4.0, 5.0, 5.0 }, // ATTACKER
        { 9.0, 7.0, 0.2, 9.5, 6.0, 14.0, 4.0, 2.5, 8.0, 7.0 }, // DEFENDER
        { 8.0, 10.0, 0.3, 6.0, 5.0, 13.0, 7.0, 2.5, 5.0, 6.0 }, // HOARDER
        { 9.0, 6.0, 0.5, 7.0, 8.0, 14.0, 4.0, 3.0, 6.0, 9.0 }, // STARVER
        { 8.0, 7.0, 0.3, 6.0, 5.0, 16.0, 3.5, 4.5, 5.0, 6.0 }, // SELF
        { 9.5, 8.0, 0.5, 7.5, 5.5, 14.5, 5.0, 3.5, 6.5, 6.5 } // BOSS
	};
	
	// ===== Profondeur de profil =====
	
	/**
	 * Sentinelle indiquant que la profondeur effective est celle passée à l'évaluateur
	 */
	public static final int DYNMIC_DEPTH = -1;
	
	/**
	 * Profondeur de recherche pour le profil BOSS
	 */
	public static final int BOSS_DEPTH = 8;
	
	/**
	 * Profondeur de recherche pour chaque profil
	 */
	private static final int[] PROFILE_DEPTHS = {
		DYNMIC_DEPTH, // BALANCED
		DYNMIC_DEPTH, // ATTACKER
		DYNMIC_DEPTH, // DEFENDER
		DYNMIC_DEPTH, // HOARDER
		DYNMIC_DEPTH, // STARVER
		DYNMIC_DEPTH, // SELF
		BOSS_DEPTH,   // BOSS
	};
	
	// ===== Multiplicateur de fitness =====
	
	/**
	 * Multiplicateur de fitness pour chaque profil
	 */
	private static final double[] PROFILE_MULTIPLIERS = {
		1.0, // BALANCED
		1.0, // ATTACKER
		1.0, // DEFENDER
		1.0, // HOARDER
		1.0, // STARVER
		1.0, // SELF
		1.5, // BOSS
	};
	
	// ===== Probabilités initiales =====
	
	/**
	 * Probabilités initiales d'apparition de chaque profil
	 */
	private static final double[] INITIAL_PROBABILITIES = {
		0.15, // BALANCED
		0.20, // ATTACKER
		0.20, // DEFENDER
		0.20, // HOARDER
		0.15, // STARVER
		0.10, // SELF
		0.00, // BOSS (toujours présent, mais ne participe pas à la sélection aléatoire)
	};
	
	/**
	 * Probabilité minimale garantie pour chaque profil
	 * (ca évite de perdre complètement la trace d'un adversaire potentiel et de l'oublier)
	 */
	private static final double EPSILON = 0.05;
	
	/**
	 * Taille de la fenêtre
	 */
	private static final int WIN_RATE_WINDOW = 20;
	
	// ===== Variables internes =====
	
	private final int nbProfiles;
	private final double[] probabilities;
	private final double[][] winRateWindow;
	private final int[] winRateIntex;
	private final double[] winRateSum;
	private final int[] winRateCount;
	private final PositionEvaluator[] evaluators;
	private double[] bestKnownWeights;
	private final Random random;
	
	public AdaptiveOpponentSelector()
	{
		this.nbProfiles = OpponentProfile.values().length;
		this.probabilities = INITIAL_PROBABILITIES.clone();
		this.winRateWindow = new double[nbProfiles][WIN_RATE_WINDOW];
		this.winRateIntex = new int[nbProfiles];
		this.winRateSum = new double[nbProfiles];
		this.winRateCount = new int[nbProfiles];
		this.evaluators = new PositionEvaluator[nbProfiles];
		this.random = new Random(86L);
		
		for (OpponentProfile profile : OpponentProfile.values())
		{
			final int index = profile.ordinal();
			
			this.evaluators[index] = new PositionEvaluator(PROFILE_WEIGHTS[index].clone());
		}
	}
	
	/**
	 * Tire un profil selon la distribution
	 * @param candidateWeights Les poids du candidat, utilisés si le profil tiré est SELF
	 * @return Le profil d'adversaire sélectionné pour la prochaine évaluation
	 */
	public OpponentProfile selectProfile(double[] candidateWeights)
	{
		return sampleProfile();
	}
	
	/**
	 * Rapporte le résultat d'une partie pou update les proba
	 * @param profile Le profil d'adversaire contre lequel la partie a été jouée
	 * @param result Le résultat de la partie du point de vue du candidat : -1 pour une défaite, 0 pour un match nul, +1 pour une victoire
	 */
	public void reportResult(OpponentProfile profile, int result)
	{
		if (profile == OpponentProfile.BOSS)
			return;
		
		final int index = profile.ordinal();
		final double outcome = (result + 1.0) / 2.0;
		
		winRateSum[index] -= winRateWindow[index][winRateIntex[index]];
		winRateWindow[index][winRateIntex[index]] = outcome;
		winRateSum[index] += outcome;
		winRateIntex[index] = (winRateIntex[index] + 1) % WIN_RATE_WINDOW;
		
		if (winRateCount[index] < WIN_RATE_WINDOW)
			winRateCount[index]++;
		
		updateProbabilities();
	}
	
	public PositionEvaluator getEvaluator(OpponentProfile profile)
	{
		return evaluators[profile.ordinal()];
	}
	
	public int getDepth(OpponentProfile profile)
	{
		return PROFILE_DEPTHS[profile.ordinal()];
	}
	
	public double getMultiplier(OpponentProfile profile)
	{
		return PROFILE_MULTIPLIERS[profile.ordinal()];
	}
	
	public double[] getProbabilities()
	{
		return probabilities.clone();
	}
	
	/**
	 * Retourne le taux de victoire courant contre un profil d'adversaire donné
	 * @param profile Le profil d'adversaire pour lequel on veut connaître le taux de victoire
	 * @return Le taux de victoire moyen contre ce profil, basé sur les dernières parties jouées contre lui (ou 0.5 si aucune partie jouée)
	 */
	public double getWinRate(OpponentProfile profile)
	{
		final int index = profile.ordinal();
		
		if (winRateCount[index] == 0)
			return 0.5;
		
		return winRateSum[index] / (double)winRateCount[index];
	}
	
	/**
	 * Retourne le profil le plus probable actuellement
	 * @return Le profil d'adversaire le plus probable selon les probabilités actuelles
	 */
	public OpponentProfile getDominantProfile()
	{
		int bestIndex = 0;
		
		for (int i = 1; i < nbProfiles; i++)
		{
			if (probabilities[i] > probabilities[bestIndex])
				bestIndex = i;
		}
		
		return OpponentProfile.values()[bestIndex];
	}
	
	public void updateBestWeights(double[] weights)
	{
		this.bestKnownWeights = weights.clone();
		this.evaluators[OpponentProfile.SELF.ordinal()] =
			new PositionEvaluator(this.bestKnownWeights.clone());
	}
	
	/**
	 * Retourne un profil d'adversaire à utiliser pour la prochaine évaluation
	 * @return Un profil d'adversaire échantillonné selon les probabilités actuelles
	 */
	private OpponentProfile sampleProfile()
	{
		final double r = random.nextDouble();
		double cumulative = 0.0;
		
		final OpponentProfile[] profiles = OpponentProfile.values();
		
		for (int i = 0; i < nbProfiles; i++)
		{
			cumulative += probabilities[i];
			
			if (r < cumulative)
				return profiles[i];
		}
		
		return profiles[nbProfiles - 1];
	}
	
	/**
	 * Update les proba à partir des taux de victoire
	 */
	private void updateProbabilities()
	{
		double sum = 0.0;
		final double[] raw = new double[nbProfiles];
		
		for (int i = 0; i < nbProfiles; i++)
		{
			if (OpponentProfile.values()[i] == OpponentProfile.BOSS)
			{
				raw[i] = 0.0;
				continue;
			}
			
			final double winRate = (winRateCount[i] == 0)
				? 0.5
				: winRateSum[i] / (double)winRateCount[i];
			
			raw[i] = (1.0 - winRate) + EPSILON;
			sum += raw[i];
		}
		
		for (int i = 0; i < nbProfiles; i++)
			probabilities[i] = raw[i] / sum;
	}
}
