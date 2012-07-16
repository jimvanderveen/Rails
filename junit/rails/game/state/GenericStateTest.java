package rails.game.state;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class GenericStateTest {

    private final static String DEFAULT_ID = "Default";
    private final static String INIT_ID = "Init"; 
    private final static String ITEM_ID = "Item";
    private final static String ANOTHER_ID = "Another";

    private Root root;
    private ChangeStack stack;
    private GenericState<Item> state_default;
    private GenericState<Item> state_init;
    
    private Item item, another_item;

    @Before
    public void setUp() {
        root = StateTestUtils.setUpRoot();
        stack = root.getStateManager().getChangeStack();
        
        item = new AbstractItemImpl(root, ITEM_ID);
        another_item = new AbstractItemImpl(root, ANOTHER_ID);
        
        state_default = GenericState.create(root, DEFAULT_ID);
        state_init = GenericState.create(root, INIT_ID, item);
    }
    
    @Test
    public void testValue() {
        assertNull(state_default.value());
        assertSame(item, state_init.value());
    }
    
    @Test
    public void testSet() {
        state_default.set(item);
        assertSame(item, state_default.value());
        state_default.set(null);
        assertNull(state_default.value());
        state_init.set(another_item);
        assertSame(another_item, state_init.value());
    }
    
    @Test
    public void testSetSameIgnored() {
        state_default.set(null);
        state_init.set(item);
        stack.closeCurrentChangeSet();
        assertThat(stack.getLastClosedChangeSet().getStates()).doesNotContain(state_default, state_init);
    }

    @Test
    public void testUndoRedo() {
        assertNull(state_default.value());
        assertSame(item, state_init.value());

        state_default.set(item);
        state_init.set(another_item);
        assertSame(item, state_default.value());
        assertSame(another_item, state_init.value());

        stack.closeCurrentChangeSet();
        // remark: state_init is an internal (isObservable = false)
        assertThat(stack.getLastClosedChangeSet().getStates()).contains(state_default);
        
        stack.undo();
        assertNull(state_default.value());
        assertSame(item, state_init.value());

        stack.redo();
        assertSame(item, state_default.value());
        assertSame(another_item, state_init.value());
    }

}
