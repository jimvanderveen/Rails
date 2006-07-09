package game;

import game.special.*;
import java.util.*;
import util.Util;

/**
 * Implements a basic Operating Round.
 * <p>
 * A new instance must be created for each new Operating Round. At the end of a
 * round, the current instance should be discarded.
 * <p>
 * Permanent memory is formed by static attributes.
 */
public class OperatingRound implements Round
{

	/* Transient memory (per round only) */
	//protected Player currentPlayer;
	//protected int currentPlayerIndex;
	protected int step;
	protected boolean actionPossible = true;
	protected String actionNotPossibleMessage = "";

	protected TreeMap operatingCompanies;
	protected PublicCompanyI[] operatingCompanyArray;
	protected int operatingCompanyIndex = 0;
	protected PublicCompanyI operatingCompany;

	protected int[] tileLayCost;
	protected String[] tilesLaid;
	protected int[] baseTokenLayCost;
	protected String[] baseTokensLaid;
	protected int[] revenue;
	protected int[] trainBuyCost;
	protected int[] privateBuyCost;

	protected List currentSpecialProperties = null;

	protected PhaseI currentPhase;
	protected String thisOrNumber;
	
	protected BuyableTrain savedBuyableTrain = null;
	protected int savedPrice = 0;
	protected int cashToBeRaisedByPresident = 0;

	/**
	 * Number of tiles that may be laid. TODO: This does not cover cases like "2
	 * yellow or 1 upgrade allowed".
	 */
	protected int normalTileLaysAllowed = 1;
	protected int normalTileLaysDone = 0;
	protected int extraTileLaysAllowed = 0;
	protected int extraTileLaysDone = 0;

	protected int splitRule = SPLIT_NOT_ALLOWED; // To be made configurable

	/* Permanent memory */
	static protected Player[] players;
	static protected PublicCompanyI[] companies;
	static protected int numberOfCompanies = 0;
	static protected int relativeORNumber = 0;
	static protected int cumulativeORNumber = 0;

	/* Constants */
	public static final int SPLIT_NOT_ALLOWED = 0;
	public static final int SPLIT_ROUND_UP = 1; // More money to the
	// shareholders
	public static final int SPLIT_ROUND_DOWN = 2; // More to the treasury

	public static final int STEP_LAY_TRACK = 0;
	public static final int STEP_LAY_TOKEN = 1;
	public static final int STEP_CALC_REVENUE = 2;
	public static final int STEP_PAYOUT = 3;
	public static final int STEP_BUY_TRAIN = 4;
	public static final int STEP_FINAL = 5;
	protected static int[] steps = new int[] { STEP_LAY_TRACK, STEP_LAY_TOKEN,
			STEP_CALC_REVENUE, STEP_PAYOUT, STEP_BUY_TRAIN, STEP_FINAL };

	/**
	 * The constructor.
	 */
	public OperatingRound()
	{

		if (players == null)
		{
			players = Game.getPlayerManager().getPlayersArray();
		}
		if (companies == null)
		{
			companies = (PublicCompanyI[]) Game.getCompanyManager()
					.getAllPublicCompanies()
					.toArray(new PublicCompanyI[0]);
		}
		// Determine operating sequence for this OR.
		// Shortcut: order considered fixed at the OR start. This is not always
		// true.
		operatingCompanies = new TreeMap();
		PublicCompanyI company;
		StockSpaceI space;
		int key, stackPos;
		int minorNo = 0;
		for (int i = 0; i < companies.length; i++)
		{
			company = companies[i];
			if (!company.hasFloated())
				continue;
			// Key must put companies in reverse operating order, because sort
			// is ascending.
			if (company.hasStockPrice())
			{
				space = company.getCurrentPrice();
				key = 1000000 * (999 - space.getPrice()) + 10000
						* (99 - space.getColumn()) + 100 * space.getRow()
						+ space.getStackPosition(company);
			}
			else
			{
				key = ++minorNo;
			}
			operatingCompanies.put(new Integer(key), company);
		}

		operatingCompanyArray = (PublicCompanyI[]) operatingCompanies.values()
				.toArray(new PublicCompanyI[0]);
		step = steps[0];

		relativeORNumber++;
		cumulativeORNumber++;
		thisOrNumber = getCompositeORNumber();

		Log.write("\nStart of Operating Round " + getCompositeORNumber());

		numberOfCompanies = operatingCompanyArray.length;

		revenue = new int[numberOfCompanies];
		tilesLaid = new String[numberOfCompanies];
		tileLayCost = new int[numberOfCompanies];
		baseTokensLaid = new String[numberOfCompanies];
		baseTokenLayCost = new int[numberOfCompanies];
		trainBuyCost = new int[numberOfCompanies];
		privateBuyCost = new int[numberOfCompanies];

		// Private companies pay out
		Iterator it = Game.getCompanyManager()
				.getAllPrivateCompanies()
				.iterator();
		PrivateCompanyI priv;
		while (it.hasNext())
		{
			priv = (PrivateCompanyI) it.next();
			if (!priv.isClosed())
				priv.payOut();
		}

		if (operatingCompanyArray.length > 0)
		{
			operatingCompany = operatingCompanyArray[operatingCompanyIndex];
			GameManager.getInstance().setRound(this);

			// prepare any specials
			prepareStep(step);
		}
		else
		{
			// No operating companies yet: close the round.
			Log.write("End of Operating Round" + getCompositeORNumber());
			GameManager.getInstance().nextRound(this);
		}
	}

	/*----- General methods -----*/

	/**
	 * Return the operating round (OR) number in the format sr.or, where sr is
	 * the last stock round number and or is the relative OR number.
	 * 
	 * @return Composite SR/OR number.
	 */
	public String getCompositeORNumber()
	{
		return StockRound.getLastStockRoundNumber() + "." + relativeORNumber;
	}

	/**
	 * Get the relative OR number. This number restarts at 1 after each stock
	 * round.
	 * 
	 * @return Relative OR number
	 */
	public int getRelativeORNumber()
	{
		return relativeORNumber;
	}

	/**
	 * Get the cumulative OR number. This number never restarts.
	 * 
	 * @return Cumulative OR number.
	 */
	public int getCumulativeORNumber()
	{
		return cumulativeORNumber;
	}

	public static int getLastORNumber()
	{
		return cumulativeORNumber;
	}

	/**
	 * @deprecated Currently needed, but will be removed in a later stage.
	 */
	public static void resetRelativeORNumber()
	{
		relativeORNumber = 0;
	}

	/*----- METHODS THAT PROCESS PLAYER ACTIONS -----*/

	/**
	 * A (perhaps temporary) method via which the cost of track laying can be
	 * accounted for.
	 * 
	 * @param companyName
	 *            The name of the company that lays the track.
	 * @param amountSpent
	 *            The cost of laying the track, which is subtracted from the
	 *            company treasury.
	 */
	public boolean layTile(String companyName, MapHex hex, TileI tile,
			int orientation)
	{

		String errMsg = null;
		int cost = 0;
		SpecialTileLay stl = null;

		// Dummy loop to enable a quick jump out.
		while (true)
		{
			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName + " (expected "
						+ operatingCompany.getName() + ")";
				break;
			}
			// Must be correct step
			if (step != STEP_LAY_TRACK)
			{
				errMsg = "Wrong action, expected Tile laying cost";
				break;
			}

			if (tile == null)
				break;

			if (!tile.isLayableNow())
			{
				errMsg = "Tile " + tile.getName() + " is not yet available";
				break;
			}
			if (tile.countFreeTiles() == 0)
			{
				errMsg = "Tile " + tile.getName() + " is not available";
				break;
			}

			// Was a special property used?
			if (currentSpecialProperties != null)
			{
				stl = (SpecialTileLay) checkForUseOfSpecialProperty(hex);
				if (stl == null && normalTileLaysDone >= normalTileLaysAllowed)
				{
					errMsg = "Cannot lay a tile without using a Private special property";
					break;
				}
			}

			// Sort out cost
			if (hex.getCurrentTile().getId() == hex.getPreprintedTileId())
			{
				cost = hex.getTileCost();
			}
			else
			{
				cost = 0;
			}

			// Amount must be non-negative multiple of 10
			if (cost < 0)
			{
				errMsg = "Negative amount not allowed";
				break;
			}
			if (cost % 10 != 0)
			{
				errMsg = "Amount must be a multiple of 10";
				break;
			}
			// Does the company have the money?
			if (cost > operatingCompany.getCash())
			{
				errMsg = "Not enough money";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot process tile laying: " + errMsg);
			return false;
		}

		if (tile != null)
		{
			hex.upgrade(tile, orientation);

			if (cost > 0) Bank.transferCash((CashHolder) operatingCompany, null, cost);
			tileLayCost[operatingCompanyIndex] = cost;
			tilesLaid[operatingCompanyIndex] = Util.appendWithComma(tilesLaid[operatingCompanyIndex],
					"#" + tile.getName() + "/" + hex.getName() + "/"
							+ MapHex.getOrientationName(orientation)); // FIXME:
																		// Wrong!
			Log.write(operatingCompany.getName() + " lays tile "
					+ tile.getName() + " at hex " + hex.getName()
					+ (cost > 0 ? " for " + Bank.format(cost) : ""));

			// Was a special property used?
			if (stl != null)
			{
				// System.out.println("A special property of "
				// + stl.getCompany().getName() + " is used");
				stl.setExercised();
				if (stl.isExtra())
					extraTileLaysDone++;
				else
					normalTileLaysDone++;
				currentSpecialProperties = operatingCompany.getPortfolio()
						.getSpecialProperties(game.special.SpecialTileLay.class);
			}
			else
			{
				normalTileLaysDone++;
			}
		}

		// System.out.println("Normal="+normalTileLaysDone+"/"+normalTileLaysAllowed
		// +" special="+extraTileLaysDone+"/"+extraTileLaysAllowed);
		if (tile == null || normalTileLaysDone >= normalTileLaysAllowed
				&& extraTileLaysDone >= extraTileLaysAllowed)
		{
			nextStep(operatingCompany);
		}

		return true;
	}

	public String getLastTileLaid()
	{
		return tilesLaid[operatingCompanyIndex];
	}

	public int getLastTileLayCost()
	{
		return tileLayCost[operatingCompanyIndex];
	}

	private SpecialORProperty checkForUseOfSpecialProperty(MapHex hex)
	{
		if (currentSpecialProperties == null)
			return null;

		Iterator it = currentSpecialProperties.iterator();
		SpecialProperty sp;
		while (it.hasNext())
		{
			sp = (SpecialProperty) it.next();
			if (sp instanceof SpecialTileLay
					&& ((SpecialTileLay) sp).getLocation() == hex)
			{
				return (SpecialORProperty) sp;
			}
		}
		return null;
	}

	/**
	 * A (perhaps temporary) method via which the cost of station token laying
	 * can be accounted for.
	 * 
	 * @param companyName
	 *            The name of the company that lays the token.
	 * @param amountSpent
	 *            The cost of laying the token, which is subtracted from the
	 *            company treasury.
	 * @return
	 */
	public boolean layBaseToken(String companyName, MapHex hex, int station)
	{

		String errMsg = null;
		int cost = 0;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_LAY_TOKEN)
			{
				errMsg = "Wrong action, not expecting Token lay";
				break;
			}

			if (!operatingCompany.hasTokens())
			{
				errMsg = "Company has no more tokens";
				break;
			}
			cost = Game.getCompanyManager()
					.getBaseTokenLayCostBySequence(operatingCompany.getNextBaseTokenIndex());

			// Does the company have the money?
			if (cost > operatingCompany.getCash())
			{
				errMsg = "Not enough money";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot process token laying on " + hex.getName()
					+ " for " + Bank.format(cost) + ": " + errMsg);
			return false;
		}

		if (!operatingCompany.layBaseToken(hex, station)) // FIXME: Need to
															// specify station!
			return false;

		baseTokensLaid[operatingCompanyIndex] = Util.appendWithComma(baseTokensLaid[operatingCompanyIndex],
				hex.getName());
		baseTokenLayCost[operatingCompanyIndex] = cost;

		if (cost > 0)
		{
			Bank.transferCash((CashHolder) operatingCompany, null, cost);
			Log.write(companyName + " lays a token on " + hex.getName()
					+ " for " + Bank.format(cost));
		}
		else
		{
			Log.write(companyName + " lays a free token on " + hex.getName());
		}

		nextStep(operatingCompany);

		return true;
	}

	/**
	 * @return The name of the hex where the last Base Token was laid.
	 */
	public String getLastBaseTokenLaid()
	{
		return baseTokensLaid[operatingCompanyIndex];
	}

	/**
	 * @return The cost of the last Base token laid.
	 */
	public int getLastBaseTokenLayCost()
	{
		return baseTokenLayCost[operatingCompanyIndex];
	}

	/**
	 * Set a given revenue. This may be a temporary method. We will have to
	 * enter revenues manually as long as the program cannot yet do the
	 * calculations.
	 * 
	 * @param amount
	 *            The revenue.
	 * @return False if an error is found.
	 */
	public boolean setRevenue(String companyName, int amount)
	{

		String errMsg = null;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_CALC_REVENUE)
			{
				errMsg = "Wrong action, expected Revenue calculation";
				break;
			}

			// Amount must be non-negative multiple of 10
			if (amount < 0)
			{
				errMsg = "Negative amount not allowed";
				break;
			}
			if (amount % 10 != 0)
			{
				errMsg = "Must be a multiple of 10";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot process revenue of " + amount + ": " + errMsg);
			return false;
		}

		revenue[operatingCompanyIndex] = amount;
		Log.write(companyName + " earns " + Bank.format(amount));

		nextStep(operatingCompany);

		// If we already know what to do: do it.
		if (amount == 0)
		{
			operatingCompany.withhold(0);
			nextStep(operatingCompany);
		}
		else if (operatingCompany.isSplitAlways())
		{
			operatingCompany.splitRevenue(amount);
			nextStep(operatingCompany);
		}

		return true;
	}

	/**
	 * A previously entered revenue is fully paid out as dividend.
	 * <p>
	 * Note: <b>setRevenue()</b> must have been called before this method.
	 * 
	 * @param companyName
	 *            Name of the company paying dividend.
	 * @return False if an error is found.
	 */
	public boolean fullPayout(String companyName)
	{

		String errMsg = null;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_PAYOUT)
			{
				errMsg = "Wrong action, expected Revenue Assignment";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot payout revenue of "
					+ Bank.format(revenue[operatingCompanyIndex]) + ": "
					+ errMsg);
			return false;
		}

		Log.write(companyName + " pays out full dividend of "
				+ Bank.format(revenue[operatingCompanyIndex]));
		operatingCompany.payOut(revenue[operatingCompanyIndex]);

		nextStep(operatingCompany);

		return true;
	}

	/**
	 * A previously entered revenue is split, i.e. half of it is paid out as
	 * dividend, the other half is retained.
	 * <p>
	 * Note: <b>setRevenue()</b> must have been called before this method.
	 * 
	 * @param companyName
	 *            Name of the company splitting the dividend.
	 * @return False if an error is found. TODO Check if split is allowed. TODO
	 *         The actual payout. TODO Rounding up or down an odd revenue per
	 *         share.
	 */
	public boolean splitPayout(String companyName)
	{

		String errMsg = null;

		// Dummy loop to enable quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_PAYOUT)
			{
				errMsg = "Wrong action, expected Revenue Assignment";
				break;
			}
			// Split must be allowed
			if (splitRule == SPLIT_NOT_ALLOWED)
			{
				errMsg = "Split not allowed";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot split revenue of "
					+ Bank.format(revenue[operatingCompanyIndex]) + ": "
					+ errMsg);
			return false;
		}

		Log.write(companyName + " pays out half dividend");
		operatingCompany.splitRevenue(revenue[operatingCompanyIndex]);
		nextStep(operatingCompany);

		return true;
	}

	/**
	 * A previously entered revenue is fully withheld.
	 * <p>
	 * Note: <b>setRevenue()</b> must have been called before this method.
	 * 
	 * @param companyName
	 *            Name of the company withholding the dividend.
	 * @return False if an error is found.
	 */
	public boolean withholdPayout(String companyName)
	{

		String errMsg = null;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_PAYOUT)
			{
				errMsg = "Wrong action, expected Revenue Assignment";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot withhold revenue of " + revenue + ": " + errMsg);
			return false;
		}
		Log.write(companyName + " withholds dividend of "
				+ Bank.format(revenue[operatingCompanyIndex]));

		operatingCompany.withhold(revenue[operatingCompanyIndex]);

		nextStep(operatingCompany);

		return true;
	}

	/**
	 * Internal method: change the OR state to the next step. If the currently
	 * Operating Company is done, notify this.
	 * 
	 * @param company
	 *            The current company.
	 */
	protected void nextStep(PublicCompanyI company)
	{
		actionPossible = true;
		actionNotPossibleMessage = "";

		// Cycle through the steps until we reach one where action is allowed.
		while (++step < steps.length)
		{

			if (step == STEP_LAY_TOKEN
					&& operatingCompany.getNumCityTokens() == 0)
				continue;

			if (step == STEP_CALC_REVENUE
					&& operatingCompany.getPortfolio().getTrains().length == 0)
			{
				// No trains, then the revenue is zero.
				// setRevenue (operatingCompany.getName(), 0);
				actionPossible = false;
				actionNotPossibleMessage = "No trains owned, so Revenue is "
						+ Bank.format(0);
				// which will call this method again twice,
				// so by now the step will be increased to STEP_BUY_TRAIN.
			}

			// No reason found to skip this step
			return;
		}

		if (step >= steps.length)
			done(company.getName());

	}

	public boolean isActionAllowed()
	{
		return actionPossible;
	}

	public String getActionNotAllowedMessage()
	{
		return actionNotPossibleMessage;
	}

	protected void prepareStep(int step)
	{

		currentPhase = PhaseManager.getInstance().getCurrentPhase();

		if (step == STEP_LAY_TRACK)
		{
			normalTileLaysDone = 0;
			extraTileLaysDone = 0;
			tileLayCost[operatingCompanyIndex] = 0;
			tilesLaid[operatingCompanyIndex] = "";

			checkForExtraTileLays();
		}
		else if (step == STEP_LAY_TOKEN)
		{

			baseTokenLayCost[operatingCompanyIndex] = 0;
			baseTokensLaid[operatingCompanyIndex] = "";
		}
		else
		{
			currentSpecialProperties = null;
		}
	}

	private void checkForExtraTileLays()
	{
		extraTileLaysAllowed = 0;
		currentSpecialProperties = operatingCompany.getPortfolio()
				.getSpecialProperties(game.special.SpecialTileLay.class);
		if (currentSpecialProperties != null)
		{
			Iterator it = currentSpecialProperties.iterator();
			while (it.hasNext())
			{
				SpecialTileLay stl = (SpecialTileLay) it.next();
				if (stl.isExtra() && !stl.isExercised())
					extraTileLaysAllowed++;
			}
		}

	}

	public List getSpecialProperties()
	{
		return currentSpecialProperties;
	}

	public void skip(String compName)
	{

		nextStep(operatingCompany);

	}

	/**
	 * The current Company is done operating.
	 * 
	 * @param company
	 *            Name of the company that finished operating.
	 * @return False if an error is found.
	 */
	public boolean done(String companyName)
	{
		String errMsg = null;

		if (!companyName.equals(operatingCompany.getName()))
		{
			errMsg = "Wrong company " + companyName;
			return false;
		}
		
		if(operatingCompany.getPortfolio().getTrains().length == 0)
		{
			//FIXME: Need to check for valid route before throwing an error.
			errMsg = companyName + " owns no trains.";
			setStep(STEP_BUY_TRAIN);
			Log.error(errMsg);
			return false;
		}

		if (++operatingCompanyIndex >= operatingCompanyArray.length)
		{
			// OR done. Inform GameManager.
			Log.write("End of Operating Round " + getCompositeORNumber());
			operatingCompany = null;
			GameManager.getInstance().nextRound(this);
			return true;
		}

		operatingCompany = operatingCompanyArray[operatingCompanyIndex];
		step = steps[0];
		prepareStep(step);

		return true;
	}

	/**
	 */
	public boolean buyTrain(String companyName, BuyableTrain bTrain, int price)
	{

		return buyTrain(companyName, bTrain, price, null);
	}

	public boolean buyTrain(String companyName, BuyableTrain bTrain, int price,
			TrainI exchangedTrain)
	{

	    TrainI train = null;
		String errMsg = null;
		int presidentCash = 0;
		boolean presidentMustSellShares = false;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Portfolio oldHolder = train.getHolder();
			// CashHolder oldOwner = oldHolder.getOwner();

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}
			// Must be correct step
			if (step != STEP_BUY_TRAIN)
			{
				errMsg = "Wrong action, expected Train buying cost";
				break;
			}

			if (bTrain == null || (train = bTrain.getTrain()) == null)
			{
				errMsg = "No train specified";
				break;
			}

			// Zero price means face value.
			if (price == 0)
				price = train.getCost();

			// Amount must be non-negative
			if (price < 0)
			{
				errMsg = "Negative amount not allowed";
				break;
			}

			// Does the company have room for another train?
			int currentNumberOfTrains = operatingCompany.getPortfolio()
					.getTrains().length;
			int trainLimit = operatingCompany.getTrainLimit(PhaseManager.getInstance()
					.getCurrentPhaseIndex());
			if (currentNumberOfTrains >= trainLimit)
			{
				errMsg = "Would exceed train limit of " + trainLimit;
				break;
			}
			
			/* Check if this is an emergency buy */
			Player currentPlayer = operatingCompany.getPresident();
			if (bTrain.mustPresidentAddCash()) {
			    // From the Bank
		        presidentCash = bTrain.getPresidentCashToAdd();
			    if (currentPlayer.getCash() >= presidentCash) {
			        Bank.transferCash(currentPlayer, operatingCompany, presidentCash);
			    } else {
			        presidentMustSellShares = true;
			        cashToBeRaisedByPresident = presidentCash - currentPlayer.getCash();
			    }
			} else if (bTrain.mayPresidentAddCash()) {
			    // From another company
			    presidentCash = price - operatingCompany.getCash();
			    if (presidentCash > bTrain.getPresidentCashToAdd()) {
			        errMsg = "President may not add more than " 
			            + Bank.format (bTrain.getPresidentCashToAdd());
			        break;
			    } else if (currentPlayer.getCash() >= presidentCash) {
			        Bank.transferCash(currentPlayer, operatingCompany, presidentCash);
			    } else {
			        presidentMustSellShares = true;
			        cashToBeRaisedByPresident = presidentCash - currentPlayer.getCash();
			    }
			    
			} else {
			    // No forced buy - does the company have the money?
				if (price > operatingCompany.getCash())
				{
					errMsg = "Not enough money";
					break;
				}
			}

			break;
		}
		if (errMsg != null)
		{
			Log.error(companyName + " cannot buy " 
			        + (train != null ? train.getName()+"-" : "unknown ")
					+ "train for " + Bank.format(price) + ": " + errMsg);
			return false;
		}
		
		if (presidentMustSellShares) {
		    savedBuyableTrain = bTrain;
		    savedPrice = price;

			GameManager.getInstance().startShareSellingRound (this, operatingCompany, 
			        cashToBeRaisedByPresident);

		    return true;
		}

		Portfolio oldHolder = train.getHolder();
		CashHolder oldOwner = oldHolder.getOwner();

		if (exchangedTrain != null)
		{
			TrainI oldTrain = operatingCompany.getPortfolio()
					.getTrainOfType(exchangedTrain.getType());
			Bank.getPool().buyTrain(oldTrain, 0);
			Log.write(operatingCompany.getName() + " exchanges "
					+ exchangedTrain.getName() + "-train for a " 
					+ train.getName() + "-train from " 
					+ oldHolder.getName() + " for "
					+ Bank.format(price));
		}
		else
		{
			Log.write(operatingCompany.getName() + " buys " + train.getName()
					+ "-train from " + oldHolder.getName() + " for "
					+ Bank.format(price));
		}

		operatingCompany.buyTrain(train, price);
		if (oldHolder == Bank.getIpo()) train.getType().addToBoughtFromIPO();
		trainBuyCost[operatingCompanyIndex] += price;

		TrainManager.get().checkTrainAvailability(train, oldHolder);
		currentPhase = GameManager.getCurrentPhase();

		return true;
	}
	
	public void resumeTrainBuying () {
	    
	    buyTrain (operatingCompany.getName(), savedBuyableTrain, savedPrice);
	    savedBuyableTrain = null;
	}

	public int getLastTrainBuyCost()
	{
		return trainBuyCost[operatingCompanyIndex];
	}

	/**
	 * Let a public company buy a private company.
	 * 
	 * @param company
	 *            Name of the company buying a private company.
	 * @param privateName
	 *            Name of teh private company.
	 * @param price
	 *            Price to be paid.
	 * @return False if an error is found. TODO: Is private buying allowed at
	 *         all? TODO: Is the game phase correct?
	 */
	public boolean buyPrivate(String companyName, String privateName, int price)
	{

		String errMsg = null;
		PrivateCompanyI privCo = null;
		CashHolder owner = null;
		Player player = null;
		int basePrice;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Must be correct company.
			if (!companyName.equals(operatingCompany.getName()))
			{
				errMsg = "Wrong company " + companyName;
				break;
			}

			// Does private exist?
			if ((privCo = Game.getCompanyManager()
					.getPrivateCompany(privateName)) == null)
			{
				errMsg = "Private " + privateName + " does not exist";
				break;
			}
			// Is private still open?
			if (privCo.isClosed())
			{
				errMsg = "Private " + privateName + " is already closed";
				break;
			}
			// Is private owned by a player?
			owner = privCo.getPortfolio().getOwner();
			if (!(owner instanceof Player))
			{
				errMsg = "Private " + privateName + " is not owned by a player";
				break;
			}
			player = (Player) owner;
			basePrice = privCo.getBasePrice();

			// Is private buying allowed?
			if (!currentPhase.isPrivateSellingAllowed())
			{
				errMsg = "Private buying is not allowed";
				break;
			}

			// Price must be in the allowed range
			if (price < basePrice
					* operatingCompany.getLowerPrivatePriceFactor())
			{
				errMsg = "Price is less than lower limit of "
						+ Bank.format((int) (basePrice * operatingCompany.getLowerPrivatePriceFactor()));
				break;
			}
			if (price > basePrice
					* operatingCompany.getUpperPrivatePriceFactor())
			{
				errMsg = "Price is more than upper limit of "
						+ Bank.format((int) (basePrice * operatingCompany.getUpperPrivatePriceFactor()));
				break;
			}
			// Does the company have the money?
			if (price > operatingCompany.getCash())
			{
				errMsg = "Not enough money";
				break;
			}
			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot buy private " + privateName
					+ (owner == null ? "" : " from " + owner.getName())
					+ " for " + Bank.format(price) + ": " + errMsg);
			return false;
		}

		operatingCompany.getPortfolio().buyPrivate(privCo,
				player.getPortfolio(),
				price);
		privateBuyCost[operatingCompanyIndex] += price;

		// We may have got an extra tile lay right
		checkForExtraTileLays();

		return true;

	}

	public int getLastPrivateBuyCost()
	{
		return privateBuyCost[operatingCompanyIndex];
	}

	/**
	 * Close a private. For now, this is an action to be initiated separately
	 * from the UI, but it will soon be coupled to the actual actions that
	 * initiate private closing. By then, this method will probably no longer be
	 * accessible from the UI, which why it is deprecated from its creation.
	 * 
	 * @param privateName
	 *            name of the private to be closed.
	 * @return False if an error occurs.
	 * @deprecated Will probably move elsewhere and become not accessible to the
	 *             UI.
	 */
	public boolean closePrivate(String privateName)
	{
		String errMsg = null;
		PrivateCompanyI privCo = null;

		// Dummy loop to enable a quick jump out.
		while (true)
		{

			// Checks
			// Does private exist?
			if ((privCo = Game.getCompanyManager()
					.getPrivateCompany(privateName)) == null)
			{
				errMsg = "Private " + privateName + " does not exist";
				break;
			}
			// Is private still open?
			if (privCo.isClosed())
			{
				errMsg = "Private " + privateName + " is already closed";
				break;
			}

			break;
		}
		if (errMsg != null)
		{
			Log.error("Cannot close private " + privateName + ": " + errMsg);
			return false;
		}

		privCo.setClosed();
		Log.write("Private " + privateName + " is closed");

		return true;

	}
	
	/*----- METHODS TO BE CALLED TO SET UP THE NEXT TURN -----*/

	/**
	 * @return The player that has the turn (in this case: the President of the
	 *         currently operating company).
	 */
	public Player getCurrentPlayer()
	{
		return operatingCompany.getPresident();
	}

	/**
	 * Get the public company that has the turn to operate.
	 * 
	 * @return The currently operating company object.
	 */
	public PublicCompanyI getOperatingCompany()
	{
		return operatingCompany;
	}

	public PublicCompanyI[] getOperatingCompanies()
	{
		return operatingCompanyArray;
	}

	/**
	 * Get the current operating round step (i.e. the next action).
	 * 
	 * @return The number that defines the next action.
	 */
	public int getStep()
	{
		return step;
	}
	
	/**
	 * Bypass normal order of operations and explicitly set round step.
	 * This should only be done for specific game exceptions, such as forced train purchases.
	 * 
	 * @param step
	 */
	private void setStep(int step)
	{
		this.step = step;
	}

	public int getOperatingCompanyIndex()
	{
		return operatingCompanyIndex;
	}

	/**
	 * Get a list of private companies that are available for buying, i.e. which
	 * are in the hands of players.
	 * 
	 * @return An array of the buyable privates. TODO Check if privates can be
	 *         bought at all.
	 */
	public PrivateCompanyI[] getBuyablePrivates()
	{
		ArrayList buyablePrivates = new ArrayList();
		PrivateCompanyI privCo;
		Iterator it = Game.getCompanyManager()
				.getAllPrivateCompanies()
				.iterator();
		while (it.hasNext())
		{
			if ((privCo = (PrivateCompanyI) it.next()).getPortfolio()
					.getOwner() instanceof Player)
				buyablePrivates.add(privCo);
		}
		return (PrivateCompanyI[]) buyablePrivates.toArray(new PrivateCompanyI[0]);
	}
	
	/** 
	 * Get a list of buyable trains for the currently operating company.
	 * Omit trains that the company has no money for. If there is no cash to
	 * buy any train from the Bank, prepare for emergency train buying.
	 * @return List of all trains that could potentially be bought.
	 */
	public List getBuyableTrains() {
	    
	    if (operatingCompany == null) return null;
	    
	    int cash = operatingCompany.getCash();
	    int cost;
	    List buyableTrains = new ArrayList();
	    List trains;
	    TrainI train;
	    boolean hasTrains = operatingCompany.getPortfolio().getTrains().length > 0;
	    boolean presidentMayHelp = false;
	    TrainI cheapestTrain = null;
	    int costOfCheapestTrain = 0;
	    
	    /* New trains */
        trains =  TrainManager.get().getAvailableNewTrains();
        for (Iterator it = trains.iterator(); it.hasNext(); ) {
            train = (TrainI) it.next();
            cost = train.getCost();
            if (cost <= cash) {
                buyableTrains.add (new BuyableTrain (train, cost));
            } else if (costOfCheapestTrain == 0 || cost < costOfCheapestTrain) {
                cheapestTrain = train;
                costOfCheapestTrain = cost;
            }
            if (train.canBeExchanged() && hasTrains) {
                cost = train.getType().getFirstExchangeCost();
                if (cost <= cash) buyableTrains.add (new BuyableTrain (train, cost).setForExchange());
            }
        }
        
        /* Used trains */
        trains = Bank.getPool().getUniqueTrains();
		for (Iterator it = trains.iterator(); it.hasNext();) {
		    train = (TrainI) it.next();
		    cost = train.getCost();
		    if (cost <= cash) {
		        buyableTrains.add (new BuyableTrain (train, cost));
		    } else if (costOfCheapestTrain == 0 || cost < costOfCheapestTrain) {
                cheapestTrain = train;
		        costOfCheapestTrain = cost;
		    }
		}
		if (!hasTrains && buyableTrains.isEmpty()) {
		    buyableTrains.add (new BuyableTrain (cheapestTrain, costOfCheapestTrain)
		            .setPresidentMustAddCash(costOfCheapestTrain - cash));
		    presidentMayHelp = true;
		}
		
		/* Other company trains */
		PublicCompanyI c;
		BuyableTrain bt;
		for (int j = 0; j < operatingCompanyArray.length; j++) {
			c = operatingCompanyArray[j];
			if (c == operatingCompany) continue;
			trains = c.getPortfolio().getUniqueTrains();
			for (Iterator it = trains.iterator(); it.hasNext();) {
			    train = (TrainI) it.next();
			    bt = new BuyableTrain (train, 0);
			    if (presidentMayHelp && cash < train.getCost()) {
			        bt.setPresidentMayAddCash(train.getCost() - cash);
			    }
			    buyableTrains.add (bt);
			}
		}
    
	    return buyableTrains;
	}
	
	/**
	 * Chech if revenue may be split.
	 * 
	 * @return True if revenue can be split.
	 */
	public boolean isSplitAllowed()
	{
		return (splitRule != SPLIT_NOT_ALLOWED);
	}

	/**
	 * Get all possible token laying costs in a game. This is a (perhaps
	 * temporary) method to play without a map.
	 * 
	 * @author Erik Vos
	 */
	public int[] getTokenLayCosts()
	{
		// Result is currently hardcoded, but can be made configurable.
		return new int[] { 0, 40, 100 };
	}

	public String getHelp()
	{
		StringBuffer b = new StringBuffer();
		b.append("<big>Operating round: ")
				.append(getCompositeORNumber())
				.append("</big><br>");
		b.append("<br><b>")
				.append(operatingCompany.getName())
				.append("</b> (president ")
				.append(getCurrentPlayer().getName())
				.append(") has the turn.");
		b.append("<br><br>Currently allowed actions:");
		if (step == STEP_LAY_TRACK)
		{
			b.append("<br> - Lay a tile");
			b.append("<br> - Press 'Done' if you do not want to lay a tile");
		}
		else if (step == STEP_LAY_TOKEN)
		{
			b.append("<br> - Lay a base token or press Done");
			b.append("<br> - Press 'Done' if you do not want to lay a base");
		}
		else if (step == STEP_CALC_REVENUE)
		{
			b.append("<br> - Enter new revenue amount");
			b.append("<br> - Press 'Done' if your revenue is zero");
		}
		else if (step == STEP_PAYOUT)
		{
			b.append("<br> - Choose how the revenue will be paid out");
		}
		else if (step == STEP_BUY_TRAIN)
		{
			b.append("<br> - Buy one or more trains");
			b.append("<br> - Press 'Done' to finish your turn");
		}
		/* TODO: The below if needs be refined. */
		if (GameManager.getCurrentPhase().isPrivateSellingAllowed()
				&& step != STEP_PAYOUT)
		{
			b.append("<br> - Buy one or more Privates");
		}

		if (step == STEP_LAY_TRACK)
		{
			b.append("<br><br><b>Tile laying</b> proceeds as follows:");
			b.append("<br><br> 1. On the map, select the hex that you want to lay a new tile upon.");
			b.append("<br>If tile laying is allowed on this hex, the current tile will shrink a bit <br>and a red background will show up around its edges;");
			b.append("<br>in addition, the tiles that can be laid on that hex will be displayed<br> in the 'upgrade panel' at the left hand side of the map.");
			b.append("<br>If tile laying is not allowed there, nothing will happen.");
			b.append("<br><br> 2. Select a tile in the upgrade panel.<br>This tile will be copied to the selected hex,<br>in some orientation");
			b.append("<br><br> 3. If you want to turn the tile just laid to a different orientation, click it.");
			b.append("<br>Repeatedly clicking the tile will rotate it through all allowed orientations.");
			b.append("<br><br> 4. Confirm tile laying by clicking 'Done'");
			b.append("<br><br>Before 'Done' has been pressed, you can change your mind<br>as often as you want");
			b.append(" (presuming that the other players don't get angry).");
			b.append("<br> - If you want to select another hex: repeat step 1");
			b.append("<br> - If you want to lay another tile on the currently selected hex: repeat step 2.");
			b.append("<br> - If you want to undo hex selection: click outside of the map hexes.");
			b.append("<br> - If you don't want to lay a tile after all: press 'Cancel'");
		}

		return b.toString();
	}

}
