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
	/**
	 * Profondeur maximale de recherche
	 */
	private static final int MAX_DEPTH = 10;
	
	public NoNameBot() throws InvalidBotException
	{
		this.setBotName("NoNameBot");
		this.addAuthor("Lucas Fagioli");
	}

	@Override
	public void initialize()
	{
		
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
		
		// On initialise le MinMaxNode avec le BitBoard et la profondeur maximale
		BitMinMaxNode.initialize(bitBoard, MAX_DEPTH);
		
		// On crée un BitMaxNode pour le BitBoard actuel et on récupère la décision à partir de ce nœud
		return new BitMaxNode(bitBoard).getDecision();
	}
}
