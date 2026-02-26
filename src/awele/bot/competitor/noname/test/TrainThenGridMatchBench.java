package awele.bot.competitor.noname.test;


import awele.bot.competitor.noname.NoNameBot;
import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.ordering.MoveEvaluator;
import awele.bot.competitor.noname.search.minmax.BitMinMaxNode;
import awele.bot.competitor.noname.test.MatchSearchStatsTest.MatchReport;
import awele.bot.competitor.noname.test.MatchSearchStatsTest.OrderingConfig;
import awele.core.InvalidBotException;

public final class TrainThenGridMatchBench
{
    private static final int GAMES_PER_CONFIG = 6;

    public static void main(String[] args) throws InvalidBotException
    {
        // 1) Apprentissage des poids (CMA-ES)
        // Remplace MyBot par ta classe réelle
        NoNameBot bot = new NoNameBot();
        bot.learn();

        // Evaluateur appris (ton code met normalement BitMinMaxNode.positionEvaluator à jour)
        final PositionEvaluator trainedEval = BitMinMaxNode.positionEvaluator;

        // 2) Définir l'évaluateur du bot adverse
        // Option A: adversaire "baseline" avec les poids par défaut
        final PositionEvaluator opponentEval = trainedEval;

        // Si tu as un autre bot avec une autre évaluation, remplace la ligne ci-dessus.

        // 3) Match bench sur toutes les configs possibles
        MatchSearchStatsTest bench = new MatchSearchStatsTest();

        System.out.println("config,categories,killers,history,games,winsA,winsB,draws,avgScoreA,avgScoreB,"
                + "A_meanNodes,A_p90Nodes,A_p99Nodes,A_interruptRate,"
                + "B_meanNodes,B_p90Nodes,B_p99Nodes,B_interruptRate");

        int idx = 0;
        for (boolean categories : new boolean[]{false, true})
        {
            for (boolean killers : new boolean[]{false, true})
            {
                for (boolean history : new boolean[]{false, true})
                {
                    idx++;

                    // A = ton bot (entrainé) avec config variable
                    OrderingConfig cfgA = new OrderingConfig(categories, killers, history, false);

                    // B = bot adverse (fixe). Ici je le mets sans ordering additionnel.
                    // Tu peux aussi tester B avec une config fixe différente si tu veux.
                    OrderingConfig cfgB = new OrderingConfig(false, false, false, false);

                    // Important : ne pas laisser les toggles "glisser" entre runs
                    MoveEvaluator.ENABLE_CATEGORY_ORDERING = true;
                    MoveEvaluator.ENABLE_KILLERS = true;

                    MatchReport report = bench.playMatch(trainedEval, opponentEval, cfgA, cfgB, GAMES_PER_CONFIG);

                    System.out.println(report.toCsvLine("cfg" + idx));
                }
            }
        }
    }


}