package rails.ui.swing;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.plaf.basic.*;

import org.apache.log4j.Logger;

import rails.game.*;
import rails.ui.swing.GameUIManager;
import rails.util.*;


import java.util.*;
import java.util.List;

/**
 * The Options dialog window displays the first window presented to the user.
 * This window contains all of the options available for starting a new rails.game.
 */
public class Options extends JDialog implements ActionListener
{

	GridBagConstraints gc;
	JPanel optionsPane, playersPane, buttonPane;
	JButton newButton, loadButton, quitButton, okButton;
	JComboBox[] playerBoxes;
	JComboBox gameNameBox;
	JTextField[] playerNameFields;
	BasicComboBoxRenderer renderer;
	Dimension size, optSize;
    
    GameUIManager gameUIManager;
    
    Map<String, String> gameNotes = new HashMap<String, String>();
    Map<String, String> gameDescs = new HashMap<String, String>();
    List<String> games = new ArrayList<String>();
    List<GameOption> availableOptions;
    List<JComponent> optionComponents;
    
    String gameName;
    
    int optionsStep = 1;
	
	protected static Logger log = Logger.getLogger(Options.class.getPackage().getName());

	private void initialize()
	{
		gc = new GridBagConstraints();

		optionsPane = new JPanel();
		playersPane = new JPanel();
		buttonPane = new JPanel();

		newButton = new JButton("New Game");
		loadButton = new JButton("Load Game");
		quitButton = new JButton("Quit");

		newButton.setMnemonic(KeyEvent.VK_N);
		loadButton.setMnemonic(KeyEvent.VK_L);
		quitButton.setMnemonic(KeyEvent.VK_Q);

		renderer = new BasicComboBoxRenderer();
		size = new Dimension(50, 30);
		optSize = new Dimension(50, 50);
		gameNameBox = new JComboBox();

		playerBoxes = new JComboBox[Player.MAX_PLAYERS];
		playerNameFields = new JTextField[Player.MAX_PLAYERS];

		this.getContentPane().setLayout(new GridBagLayout());
		this.getContentPane().setLayout(new GridBagLayout());
		this.setTitle("Rails: New Game");
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		renderer.setPreferredSize(size);

		playersPane.add(new JLabel("Players:"));
		playersPane.add(new JLabel(""));
		playersPane.setLayout(new GridLayout(Player.MAX_PLAYERS + 1, 0));
		playersPane.setBorder(BorderFactory.createLoweredBevelBorder());

		for (int i = 0; i < playerBoxes.length; i++)
		{
			playerBoxes[i] = new JComboBox();
			playerBoxes[i].setRenderer(renderer);
			playerBoxes[i].addItem("None");
			playerBoxes[i].addItem("Human");
			playerBoxes[i].addItem("AI eventually goes here.");
			playerBoxes[i].setSelectedIndex(1);
			playersPane.add(playerBoxes[i]);
			playerBoxes[i].setPreferredSize(size);

			playerNameFields[i] = new JTextField();
			playerNameFields[i].setPreferredSize(size);
			playersPane.add(playerNameFields[i]);
		}

		/* Prefill with any configured player names.
		 * This can be useful to speed up testing purposes.
		 */
		String testPlayerList = Config.get("default_players");
		if (Util.hasValue(testPlayerList)) {
			String[] testPlayers = testPlayerList.split(",");
			for (int i=0; i<testPlayers.length; i++) {
				playerNameFields[i].setText(testPlayers[i]);
			}
		}

		populateGameList(games.toArray(), gameNameBox);

		optionsPane.add(new JLabel("Game Options"));
		optionsPane.add(new JLabel(""));
		
		optionsPane.add(new JLabel("Game:"));
		optionsPane.add(gameNameBox);
		optionsPane.setLayout(new GridLayout(5, 2));
		optionsPane.setBorder(BorderFactory.createLoweredBevelBorder());
		optionsPane.setPreferredSize(optSize);

		newButton.addActionListener(this);
		loadButton.addActionListener(this);
		quitButton.addActionListener(this);

		//XXX: Until we can load/save a rails.game, we'll set this to disabled to reduce confusion.
		loadButton.setEnabled(true);
		
		buttonPane.add(newButton);
		buttonPane.add(loadButton);
		buttonPane.add(quitButton);
		buttonPane.setBorder(BorderFactory.createLoweredBevelBorder());
	}

	private void populateGridBag()
	{
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 1.0;
		gc.weighty = 1.0;
		gc.gridwidth = 1;
		gc.fill = GridBagConstraints.BOTH;
		this.getContentPane().add(playersPane, gc);

		gc.gridx = 0;
		gc.gridy = 1;
		gc.fill = 1;
		gc.weightx = 0.5;
		gc.weighty = 0.5;
		gc.gridwidth = 1;
		// gc.ipadx = 50;
		gc.ipady = 50;
		this.getContentPane().add(optionsPane, gc);

		gc.gridx = 0;
		gc.gridy = 2;
		gc.weightx = 0.0;
		gc.weighty = 0.0;
		gc.gridwidth = 1;
		gc.ipady = 0;
		gc.fill = GridBagConstraints.HORIZONTAL;
		this.getContentPane().add(buttonPane, gc);
	}

	private void populateGameList(Object[] gameNames, JComboBox gameNameBox)
	{
		Arrays.sort(gameNames);
		for (int i = 0; i < gameNames.length; i++)
		{
			if(gameNames[i] instanceof String)
			{
				String gameText = (String) gameNames[i];
				if (!games.isEmpty() && !games.contains(gameText)) continue;
				if (gameNotes.containsKey(gameNames[i])) {
					gameText += " " + gameNotes.get(gameNames[i]);
				}
				gameNameBox.addItem(gameText);
			}
		}
	}

	public Options(GameUIManager gameUIManager)
	{
		super();
        
        this.gameUIManager = gameUIManager;

        getGameList();
		initialize();
		populateGridBag();

		this.pack();
		this.setVisible(true);
	}
	
	private void getGameList() {
		
		String gameList = Config.get("games");
		String gameNote, gameDesc;
		if (!Util.hasValue(gameList)) return;
		for (String gameName : gameList.split(",")) {
			games.add (gameName);
			gameNote = Config.get("game."+gameName+".note");
			if (Util.hasValue(gameNote)) {
				gameNotes.put(gameName, gameNote);
			}
			gameDesc = Config.get("game."+gameName+".description");
			if (Util.hasValue(gameDesc)) {
				gameDescs.put(gameName, gameDesc);
			}
		}
	}

	public void actionPerformed(ActionEvent arg0)
	{
        if (optionsStep == 1) {
            actionPerformed1 (arg0);
        } else if (optionsStep == 2) {
            actionPerformed2 (arg0);
        }
        if (optionsStep == 3) {
            finishSetup();
        }
    }
    
    private void actionPerformed1 (ActionEvent arg0) {
        
		if (arg0.getSource().equals(newButton))
		{
			ArrayList<String> playerNames = new ArrayList<String>();

			for (int i = 0; i < playerBoxes.length; i++)
			{
				if (playerBoxes[i].getSelectedItem()
						.toString()
						.equalsIgnoreCase("Human")
						&& !playerNameFields[i].getText().equals(""))
				{
					playerNames.add(playerNameFields[i].getText());
				}
			}

			if (playerNames.size() < Player.MIN_PLAYERS
					|| playerNames.size() > Player.MAX_PLAYERS)
			{
				if (JOptionPane.showConfirmDialog(this,
						"Not enough players. Continue Anyway?",
						"Are you sure?",
						JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
				{
					return;
				}
			}

			try
			{
				this.setVisible(false);

				gameName = gameNameBox.getSelectedItem().toString().split(" ")[0];
				Game.getPlayerManager(playerNames);
				Game.prepare(gameName);
				
                // Request variant (deprecated)
                /*
				List<String> variants = GameManager.getVariants();
				if (variants != null && variants.size() > 1) {
				    String variant = (String) JOptionPane.showInputDialog (
				            this,
				            LocalText.getText("WHICH_VARIANT", gameName),
				            "", 
				            JOptionPane.PLAIN_MESSAGE,
				            null,
				            (String[])variants.toArray(new String[0]),
				            (String)variants.get(0));
				    if (variant != null) {
				    	GameManager.setVariant(variant);
				    } else {
				    	JOptionPane.showMessageDialog(this, "Null variant selected!??");
				    }
				}
                */
                
                // Request game options (new)
                availableOptions = GameManager.getAvailableOptions();
                if (availableOptions != null && !availableOptions.isEmpty()) {
                    requestGameOptions();
                    optionsStep = 2;
                } else {
                    optionsStep = 3;
                }
                
			}
			catch (NullPointerException e)
			{
				e.printStackTrace();
				JOptionPane.showMessageDialog(this,
						"Unable to load selected rails.game. Exiting...");
				System.exit(-1);
			}

		}

		if (arg0.getSource().equals(loadButton))
		{
			setVisible (false);
			gameUIManager.loadGame();
		}

		if (arg0.getSource().equals(quitButton))
		{
			System.exit(0);
		}

	}
    
    private void finishSetup() {
        
		Game.initialise();
		Player.initPlayers(Game.getPlayerManager().getPlayersArray());
        GameManager.getInstance().startGame();

        gameUIManager.gameUIInit();

    }
    
    private void requestGameOptions () {
        
        getContentPane().removeAll();
        //getContentPane().setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        //new BoxLayout(getContentPane(), BoxLayout.Y_AXIS);
        Box box = new Box (BoxLayout.Y_AXIS);
        
        optionsStep = 2;
        optionComponents = new ArrayList<JComponent>();
        
        for (GameOption option : availableOptions) {
            JPanel panel = new JPanel();
            if (option.isBoolean()) {
                JCheckBox checkbox = new JCheckBox (LocalText.getText(option.getName()));
                if (option.getDefaultValue().equalsIgnoreCase("yes")) {
                    checkbox.setSelected(true);
                }
                panel.add(checkbox);
                optionComponents.add(checkbox);
            } else {
                panel.add (new JLabel (LocalText.getText("Select",
                        LocalText.getText(option.getName()))));
                JComboBox dropdown = new JComboBox();
                for (String value : option.getAllowedValues()) {
                    dropdown.addItem(value);
                }
                panel.add(dropdown);
                optionComponents.add(dropdown);
            }
            panel.setBorder(BorderFactory.createLoweredBevelBorder());
            //getContentPane().add(panel);
            box.add(panel);
        }
        JPanel buttonPanel = new JPanel();
        okButton = new JButton(LocalText.getText(("OK")));
        okButton.addActionListener(this);
        buttonPanel.add(okButton);
        //getContentPane().add(buttonPanel);
        box.add (buttonPanel);
        getContentPane().add (box);
        
        pack();
        setLocation(200,200);
        setVisible(true);
    }
	
    private void actionPerformed2 (ActionEvent arg0) {
 
        log.debug("actionPerformed2 entered");
        if (arg0.getSource().equals(okButton)) {
            
            log.debug("OK button pressed");
            
            setVisible (false);
            
            GameOption option;
            JCheckBox checkbox;
            JComboBox dropdown;
            String value;
            
            for (int i=0; i<availableOptions.size(); i++) {
                option = availableOptions.get(i);
                if (option.isBoolean()) {
                    checkbox = (JCheckBox) optionComponents.get(i);
                    value = checkbox.isSelected() ? "yes" : "no";
                } else {
                    dropdown = (JComboBox) optionComponents.get(i);
                    value = (String)dropdown.getSelectedItem();
                }
                GameManager.setGameOption(option.getName(), value);
                log.info("Game option "+option.getName()+" set to "+value);
            }
            
        }
        optionsStep = 3;
    }
        

}
