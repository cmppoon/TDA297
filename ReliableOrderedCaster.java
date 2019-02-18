
import mcgui.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;

//TODO: implement not deliver if the message has already delivered, casual ordering, debugging

public class ReliableOrderedCaster extends Multicaster {

    /**
     * No initializations needed for this simple one
     */
    private int sequencerId;
    //history list to keep track of messages since last broadcast
    private ArrayList<MyMessage> historyList = new ArrayList<MyMessage>(); 
    private ArrayList<MyMessage> deliveredList = new ArrayList<MyMessage>();
    private int localSequence;
    private LinkedList<MyMessage> holdBackQueue = new LinkedList<MyMessage>();
    private int[] activeHost;
    //add list of all active hosts

    public void init() {
        activeHost = new int[hosts];
        for(int i = 0; i<hosts; i++){
            activeHost[i] = 1;
        }
        sequencerId = getMinActiveHost();
	assert sequencerId == -1 : "Free Sequencer not found";
		
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

    public void sequencerCast(String messagetext, int sender){
        for(int i=0; i < hosts; i++) {
            /* Sends to everyone except itself */
            if(i != id) {
                bcom.basicsend(i,new MyMessage(id, messagetext,localSequence, "order"));
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
            deliveredList.add(new MyMessage(id,messagetext, localSequence, "order"));
        
        } else {
            //node is not a sequencer, send to sequencer
            bcom.basicsend(sequencerId, new MyMessage(id, messagetext, 0, "initial"));
            mcui.debug("Send out to sequencer");
        }
    }
    
    /**
     * Receive a basic message
     * @param message  The message received
     */
    public void basicreceive(int peer,Message message) {
        MyMessage receivedMessage = ((MyMessage)message);
        if (id == sequencerId){
            //sequencer, 
            mcui.debug("in sequencer");
            if (receivedMessage.messageType.equals("initial")){
                mcui.debug("receive initial message");
                receivedMessage.setMessageType("order");
                sequencerCast(receivedMessage.text, receivedMessage.getSender());
                mcui.debug("Sent out: \""+receivedMessage.text+"\"");
                mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
                deliveredList.add(receivedMessage);
            }
        } else{
            //not a sequencer, wait until localSequence == S, then deliver a message
            mcui.debug("receive message");
            //put the received message in a queue if not yet delivered the message
            if (localSequence != receivedMessage.sequence){
                boolean isAlreadyContain = false;
                for (MyMessage m : deliveredList){
                    if (receivedMessage.isSameMessage(m)) {
                        isAlreadyContain = true;
                    }
                }
                if (!isAlreadyContain) {
                    mcui.debug("put message in a queue");
                    holdBackQueue.add(receivedMessage);
                    Collections.sort(holdBackQueue, new Comparator<MyMessage>(){
                        @Override
                        public int compare(MyMessage m1, MyMessage m2){
                            return m1.sequence - m2.sequence;
                        }
                    });
                }
            } else {
                //deliver
                mcui.debug("deliver message");
                mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
                if (!deliveredList.contains(receivedMessage)){
                    reliableCast(receivedMessage);
                }
                mcui.debug("add message to deliveredList");
                deliveredList.add(receivedMessage);
                localSequence++;
            }

            //loop though all items in queue and deliver it if message.sequence == localSequence
            Iterator<MyMessage> iterator = holdBackQueue.iterator();
            while (iterator.hasNext()) {
                mcui.debug("loop to try to deliver a message");
                MyMessage msg = (MyMessage)iterator.next();
                boolean isAlreadyContain = false;
                for (MyMessage m : deliveredList){
                    if (msg.isSameMessage(m)) {
                        isAlreadyContain = true;
                    }
                }
                if (msg.sequence == localSequence && !isAlreadyContain){
                    mcui.deliver(msg.getSender(), msg.text);
                    deliveredList.add(msg);
                    iterator.remove();
                    localSequence++;
                    reliableCast(msg);
                }
            }
                
        }
    }

    public void reliableCast(MyMessage message){
        //broadcast to others to provide reliable broadcast
        mcui.debug("reliable broadcast");
        for(int i=0; i < hosts; i++) {
            /* Sends to everyone except itself */
            if(i != id) {
                bcom.basicsend(i, message);
            }
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
		
	    // Select a new sequencer
	    if(sequencerId == peer) {
		    for (int i = 0; i < hosts; i++) {
			    if (activeHost[i]) {
				    sequencerId = i;
				    break;
			    }
		    }
	    }

    }
}
