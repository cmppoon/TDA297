
import mcgui.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

public class ReliableOrderedCaster extends Multicaster {

    /**
     * No initializations needed for this simple one
     */
    private int sequencerId;
    //history list to keep track of messages since last broadcast
    private List<MyMessage> historyList = new ArrayList<MyMessage>(); 
    private int localSequence;
    private Queue<MyMessage> holdBackQueue = new LinkedList<MyMessage>();
    private int[] activeHost;
    //add list of all active hosts

    public void init() {
        activeHost = new int[hosts];
        for(int i = 0; i<hosts; i++){
            activeHost[i] = 1;
        }
        sequencerId = getMinActiveHost();
        localSequence = 0;
        mcui.debug("Sequencer is "+ sequencerId);
        mcui.debug("The network has "+hosts+" hosts!");
    }

    public int getMinActiveHost() {
        for(int i=0; i<hosts;i++){
            if (activeHost[i] == 1) {
                return i;
            }
        }
        return -1;
    }

    public void sequencerCast(String messagetext, int messageCreator){
        for(int i=0; i < hosts; i++) {
            /* Sends to everyone except itself */
            if(i != id) {
                bcom.basicsend(i,new MyMessage(id, messagetext, messageCreator, localSequence));
            }
        }
        localSequence++;
    }
        
    /**
     * The GUI calls this module to multicast a message
     */
    public void cast(String messagetext) {
        if (id == sequencerId){
            //sequencer
            sequencerCast(messagetext, id);
            mcui.debug("Sent out: \""+messagetext+"\"");
            mcui.deliver(id, messagetext, "from myself!");
        
        } else {
            //node is not a sequencer, send to sequencer
            bcom.basicsend(sequencerId, new MyMessage(id, messagetext, id, 0));
            mcui.debug("Send out to sequencer");
        }
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer,Message message) {
        if (id == sequencerId){
            //sequencer, 
            sequencerCast(((MyMessage)message).text, ((MyMessage)message).messageCreator);
            mcui.debug("Sent out: \""+((MyMessage)message).text+"\"");
            mcui.deliver(((MyMessage)message).messageCreator, ((MyMessage)message).text, "from myself!");

        } else{
            //not a sequencer, wait until localSequence == S, then deliver a message
            mcui.deliver(((MyMessage)message).messageCreator, ((MyMessage)message).text);
        }
    }

    /**
     * Signals that a peer is down and has been down for a while to
     * allow for messages taking different paths from this peer to
     * arrive.
     * @param peer	The dead peer
     */
    public void basicpeerdown(int peer) {
        mcui.debug("Peer "+peer+" has been dead for a while now!");
    }
}
