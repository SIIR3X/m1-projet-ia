package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * Heuristique : vulnérabilité structurelle.
 * Compte les trous du joueur contenant 1 ou 2 graines (pondéré),
 * car ce sont des cibles fréquentes de capture.
 *
 * Plus c'est faible, mieux c'est -> on renvoie une valeur négative.
 */
public final class VulnerableHolesHeuristic implements PositionHeuristic
{
    public static final String ID = "vulnerable_holes";

    @Override
    public double evaluate(BitBoard board, int player)
    {
        int penalty = 0;

        for (int hole = 0; hole < BitConstants.NB_HOLES; hole++)
        {
            final int seeds = board.getSeeds(player, hole);

            // Pondération simple : 1 graine = très fragile, 2 graines = fragile.
            if (seeds == 1) penalty += 2;
            else if (seeds == 2) penalty += 1;
        }

        return -penalty;
    }

    @Override
    public String getId()
    {
        return ID;
    }
}