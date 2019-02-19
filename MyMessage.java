
import mcgui.*;

/**
 * Message implementation for ExampleCaster.
 *
 * @author Andreas Larsson &lt;larandr@chalmers.se&gt;
 */
public class MyMessage extends Message {
        
    String text;
    String messageType;
    int sequence;
    int messageCreator;

    public MyMessage(int sender,String text, int sequence, String messageType) {
        super(sender);
        this.text = text;
        this.messageType = messageType;
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

    public void setMessageType(String type){
        this.messageType = type;
    }

    public boolean isSameMessage(MyMessage message){
        return this.getSender() == message.getSender() && this.text.equals(message.text) && this.sequence == message.sequence && this.messageType.equals(message.messageType);
    }
    
    public static final long serialVersionUID = 0;
}
