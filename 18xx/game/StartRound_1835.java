/* $Header: /Users/blentz/rails_rcs/cvs/18xx/game/Attic/StartRound_1835.java,v 1.2 2005/05/25 19:08:17 evos Exp $
 * 
 * Created on 23-May-2005
 * Change Log:
 */
package game;

import java.util.*;

/**
 * Implements an 1835-style startpacket sale.
 * @author Erik Vos
 */
public class StartRound_1835 extends StartRound {
    
    /* To control the player sequence in the Clemens and Snake variants */  
    private static int cycle = 0;
    private static int startRoundNumber = 0;
    private int turns = 0;
    private int numberOfPlayers = GameManager.getNumberOfPlayers();
    private String variant;
    
    /* Additional variants */
    public static final String CLEMENS_VARIANT = "Clemens";
    public static final String SNAKE_VARIANT = "Snake";
    
     /**
     * Constructor, only to be used in dynamic instantiation.
     */
    public StartRound_1835 () {
        super();
     }
    
    /**
     * Start the 1835-style start round.
     * @param startPacket The startpacket to be sold in this start round.
     */
    public void start (StartPacket startPacket) {
    	super.start(startPacket);
        startRoundNumber++;
        variant = GameManager.getVariant();
        if (variant.equalsIgnoreCase("Clemens")) {
            GameManager.setCurrentPlayerIndex(numberOfPlayers-1);
        } else {
            GameManager.setCurrentPlayerIndex(0);
        }
        defaultStep = nextStep = BUY_OR_PASS;
    }

    /**
     * Get a list of items that may be bought immediately.<p>
     * In an 1835-style auction this method will usually return
     * several items.
     * TODO: The start packet layout.
     * @return An array of start items that can be bought.
     */
    public StartItem[] getBuyableItems () {
        List buyItems = new ArrayList();
        Iterator it = startPacket.getItems().iterator();
        StartItem b;
        int row;
        int minRow = 0;
        int items = 0;
        while (it.hasNext()) {
            if (!(b = (StartItem)it.next()).isSold()) {
            	if (variant.equalsIgnoreCase(CLEMENS_VARIANT)) {
            		buyItems.add(b);
            	} else {
            		row = b.getRow();
            		if (minRow == 0) minRow = row;
            		if (row == minRow) {
            			// Allow all items in the top row.
            			buyItems.add(b);
            			items++;
            		} else if (row == minRow + 1 && items == 1) {
            			// Allow the first item in the next row if the
            			// top row has only one item.
            			buyItems.add(b);
            			break;
            		} else if (row > minRow + 1) break;
            	}
            }
        }
        if (buyItems.size() > 0) {
            return (StartItem[]) buyItems.toArray(new StartItem[0]);
        } else {
            return new StartItem[0];
        }
    }
    
    /**
     * Get a list of items that mat be bid upon.<p>
     * In an 1835-style auction this method will always return null.
     * @return An array of start items that may be bid upon.
     */
    public StartItem[] getBiddableItems () {
        return null;
    }
    
    /**
     * Get the currentPlayer.
     * @return The current Player object.
     * @see GameManager.getCurrentPlayer().
     */
    public Player getCurrentPlayer() {
        return GameManager.getCurrentPlayer();
    }
    
    /**
     * Get the company for which a par price must be set in 
     * the SET_PRICE state. In other states, null is returned.
     * @return The PublicCompany object for which a par price is needed.
     */
    public PublicCompanyI getCompanyNeedingPrice () {
        return null;
    }
    
    /*----- Action methods -----*/
    
    /**
     * The current player bids 5 more than the previous bid
     * on a given start item.<p>
     * A separate method is provided for this action because 5
     * is the usual amount with which bids are raised.
     * @param playerName The name of the current player (for checking purposes).
     * @param itemName The name of the start item on which the bid is placed.
     */
    public boolean bid5 (String playerName, String itemName) {
        
        Log.error ("Invalid action in this game");
        return false;
    }
    
    /**
     * The current player bids on a given start item. 
     * @param playerName The name of the current player (for checking purposes).
     * @param itemName The name of the start item on which the bid is placed.
     * @param amount The bid amount.
     */
    public boolean bid (String playerName, String itemName, int amount) {
        
        Log.error ("Invalid action in this game");
        return false;
    }
    
    /**
     * Define the next action to take after a start item is bought.
     *
     */
    protected void setNextAction() {
        
        if (startPacket.areAllSold()) {
            // No more start items: start a stock round
            GameManager.getInstance().nextRound(this);
        } else {
        	int currentIndex = GameManager.getCurrentPlayerIndex();
        	int newIndex = 0;
        	if (++turns == numberOfPlayers) {
        		cycle++;
        		turns = 0;
        	}
        	if (startRoundNumber > 1) {
        		newIndex = GameManager.getPriorityPlayerIndex();
        	} else if (variant.equalsIgnoreCase(CLEMENS_VARIANT)) {
        		newIndex = cycle == 0 ? numberOfPlayers - 1 - turns : turns;
        	} else if (variant.equalsIgnoreCase(SNAKE_VARIANT)) {
        		newIndex = cycle == 1 ? numberOfPlayers - 1 - turns : turns;
        	} else {
        		newIndex = turns;
        	}
        	GameManager.setCurrentPlayerIndex(newIndex);
         	nextStep = BUY_OR_PASS;
        }
        return;
    }
    
    /**
     * Set a par price.
     * @param playerName The name of the par price setting player.
     * @param companyName The name of teh company for which a par price is set.
     * @param parPrice The par price.
     */
    public boolean setPrice (String playerName, String companyName, int parPrice) {
        
        Log.error ("Invalid action in this game");
        return false;
    }
    
    /**
     * Process a player's pass.
     * @param playerName The name of the current player (for checking purposes).
     */
    public boolean pass (String playerName) {

        String errMsg = null;
        Player player = GameManager.getCurrentPlayer();
        
        while (true) {
            
            // Check player
             if (!playerName.equals(player.getName())) {
                errMsg = "Wrong player";
                break;
            }
            break;
        }
        
        if (errMsg != null) {
            Log.error ("Invalid pass by "+playerName+": " + errMsg);
            return false;
        }
        
        Log.write (playerName+" passes.");
        GameManager.setNextPlayer();
        
        if (++numPasses >= numPlayers) {
            // All players have passed. 
            Log.write("All players passed.");
            GameManager.getInstance().nextRound(this);
        }
        
        return true;
    }
    
    /*----- Interbnal functions -----*/
    protected boolean isBuyable (StartItem item) {
    	return !item.isSold();
    }
    
    protected boolean isBiddable (StartItem item) {
    	return false;
    }
    
 }
