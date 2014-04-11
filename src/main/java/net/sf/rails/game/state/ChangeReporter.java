package net.sf.rails.game.state;

public interface ChangeReporter {
    
    void updateOnClose(ChangeSet current);
    
    void informOnUndo();

    void informOnRedo();
    
}
