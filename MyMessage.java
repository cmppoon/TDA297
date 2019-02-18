
import mcgui.*;

/**
 * Message implementation for ExampleCaster.
 *
 * @author Andreas Larsson &lt;larandr@chalmers.se&gt;
 */
public class MyMessage extends Message {
        
    String text;
    int sequence;
    int messageCreator;

    public MyMessage(int sender,String text, int messageCreator, int sequence) {
        super(sender);
        this.text = text;
        this.messageCreator = messageCreator;
        this.sequence = sequence;
    }
    
    /**
     * Returns the text of the message only. The toString method can
     * be implemented to show additional things useful for debugging
     * purposes.
     */
    public String getText() {
        return text;
    }
    
    public static final long serialVersionUID = 0;
}
