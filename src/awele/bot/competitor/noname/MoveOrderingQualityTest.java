package awele.bot.competitor.noname;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
import awele.core.Board;

/**
 * Joue 10 parties et calcule des stats directement liées à la "qualité" pratique
 * de ton move ordering via ce que tu as demandé : profondeurs atteintes (min/max/moy)
 * et, en bonus, nodes visités (min/max/moy) et résultat des parties.
 *
 * IMPORTANT:
 * - Ce test utilise NoNameBot tel quel (MAX_TIME_MS=98ms), donc il est sensible à la machine.
 * - Les assertions sont volontairement "soft" pour éviter la flakiness.
 * - Les stats sont imprimées dans la sortie de test (console).
 */
public class MoveOrderingQualityTest {

    private static final int NB_GAMES = 25;

    // Garde-fous anti-boucle (Awélé peut durer longtemps si bug)
    private static final int MAX_PLIES_PER_GAME = 300;

    // Seuils "soft" (à ajuster après 1-2 runs chez toi)
    private static final double MIN_AVG_DEPTH = 3.0;   // profondeur moyenne par coup de NoName
    private static final double MIN_MIN_DEPTH = 1.0;   // profondeur minimale atteinte (souvent 1 si time pressure)

    @Test
    void play10Games_and_reportDepthAndNodesStats() throws Exception {
        NoNameBot bot = new NoNameBot();
        bot.initialize();

        Random rng = new Random(12345);

        int wins = 0, losses = 0, draws = 0;

        List<Integer> depths = new ArrayList<>();
        List<Long> nodes = new ArrayList<>();

        for (int g = 0; g < NB_GAMES; g++) {
            Board board = new Board();

            // Alterne qui commence pour éviter biais
            int botPlayer = (g % 2 == 0) ? 0 : 1;

            int plies = 0;
            while (!isTerminal(board) && plies < MAX_PLIES_PER_GAME) {
                int current = board.getCurrentPlayer();

                if (current == botPlayer) {
                    // NoNameBot remet BitMinMaxNode.nodeCount à 0 au début de getDecision()
                    double[] decision = bot.getDecision(board);

                    int depthReached = bot.getLastDepthReached();
                    long nodeCount = BitMinMaxNode.nodeCount; // nodes du dernier coup uniquement

                    depths.add(depthReached);
                    nodes.add(nodeCount);

                    board.playMove(current, decision);
                } else {
                    // Opponent "random mais légal" via vecteur de décision random
                    double[] randomDecision = randomDecision(rng);
                    board.playMove(current, randomDecision);
                }

                plies++;
            }

            int s0 = board.getScore(0);
            int s1 = board.getScore(1);

            int botScore = (botPlayer == 0) ? s0 : s1;
            int oppScore = (botPlayer == 0) ? s1 : s0;

            if (botScore > oppScore) wins++;
            else if (botScore < oppScore) losses++;
            else draws++;
        }

        Stats depthStats = Stats.fromInts(depths);
        Stats nodeStats = Stats.fromLongs(nodes);

        System.out.println("=== Move ordering stats from " + NB_GAMES + " games ===");
        System.out.println("Games: wins=" + wins + " losses=" + losses + " draws=" + draws);
        System.out.println("Depth reached (NoName moves): min=" + depthStats.min
                + " avg=" + depthStats.avg + " max=" + depthStats.max + " n=" + depths.size());
        System.out.println("Nodes visited (NoName moves): min=" + nodeStats.min
                + " avg=" + nodeStats.avg + " max=" + nodeStats.max + " n=" + nodes.size());

        // Assertions "soft" (sinon risque de flakiness selon machine)
        assertTrue(depthStats.avg >= MIN_AVG_DEPTH,
                "Profondeur moyenne trop faible (" + depthStats.avg + "). "
                        + "Si ça échoue sur CI, baisse MIN_AVG_DEPTH ou fais une version fixed-depth (sans timer).");

        assertTrue(depthStats.min >= MIN_MIN_DEPTH,
                "Profondeur min inattendue (" + depthStats.min + ").");
    }

    private static boolean isTerminal(Board b) {
        // Cohérent avec tes conditions dans BitMinMaxNode/BitBoard
        // - score gagnant ~25
        // - peu de graines restantes
        return b.getScore(0) >= 25
                || b.getScore(1) >= 25
                || b.getNbSeeds() <= 6;
    }

    private static double[] randomDecision(Random rng) {
        double[] d = new double[Board.NB_HOLES];
        for (int i = 0; i < d.length; i++) {
            // distribution large pour limiter les égalités
            d[i] = rng.nextDouble();
        }
        return d;
    }

    private static final class Stats {
        final double min;
        final double avg;
        final double max;

        private Stats(double min, double avg, double max) {
            this.min = min;
            this.avg = avg;
            this.max = max;
        }

        static Stats fromInts(List<Integer> xs) {
            if (xs.isEmpty()) return new Stats(0, 0, 0);
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double sum = 0;
            for (int x : xs) {
                min = Math.min(min, x);
                max = Math.max(max, x);
                sum += x;
            }
            return new Stats(min, sum / xs.size(), max);
        }

        static Stats fromLongs(List<Long> xs) {
            if (xs.isEmpty()) return new Stats(0, 0, 0);
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double sum = 0;
            for (long x : xs) {
                min = Math.min(min, x);
                max = Math.max(max, x);
                sum += x;
            }
            return new Stats(min, sum / xs.size(), max);
        }
    }
}
