import mcgui.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

//TODO: implement not deliver if the message has already delivered, casual ordering, debugging

public class ReliableOrderedCaster extends Multicaster {

	/**
	 * No initializations needed for this simple one
	 */
	private int sequencerId;
    private int globalSequence;
	private int localSequence;
	private int[] activeHost;
	//add list of all active hosts
	//history list to keep track of messages since last broadcast
	
	private ArrayList<MyMessage> historyList = new ArrayList<MyMessage>(); 
	private ArrayList<MyMessage> deliveredList = new ArrayList<MyMessage>();
	private LinkedList<MyMessage> holdBackQueue = new LinkedList<MyMessage>();

	public void init() {
		activeHost = new int[hosts];
		for(int i = 0; i<hosts; i++){
			activeHost[i] = 1;
		}
		sequencerId = getMinActiveHost();
		assert sequencerId == -1 : "Free Sequencer not found";
        globalSequence = 0;
		localSequence = 0;
		mcui.debug("Sequencer is "+ sequencerId);
		mcui.debug("The network has "+hosts+" hosts!");
	}

	public void sequencerCast(MyMessage message){
		for(int i=0; i < hosts; i++) {
			/* Sends to everyone except itself */
			if(i != id) {
				bcom.basicsend(i, message);
			}
		}
	}

	/**
	 * The GUI calls this module to multicast a message
	 */
	public void cast(String messagetext) {
        MyMessage message = new MyMessage(id,messagetext,0,"initial");
        Collections.sort(historyList, new Comparator<MyMessage>(){
                @Override
                public int compare(MyMessage m1, MyMessage m2){
                return m1.sequence - m2.sequence;
            }
        });
		if (id == sequencerId){
			//sequencer
            message.setMessageType("order");
            message.setSequence(globalSequence); // might need to change to global sequence later
            message.setHistory(historyList);
			sequencerCast(message);
			mcui.debug("Sent out: \""+messagetext+"\"");
			mcui.deliver(id, messagetext);
			deliveredList.add(message);
            historyList = new ArrayList<MyMessage>();
            globalSequence++;

		} else {
			//node is not a sequencer, send to sequencer
            mcui.debug(""+ historyList.size());
            message.setHistory(historyList);
            mcui.debug(""+message.history.size());
			bcom.basicsend(sequencerId, message);
            historyList = new ArrayList<MyMessage>();
			mcui.debug("Send out to sequencer");
		}
	}

	/**
	 * Receive a basic message
	 * @param message  The message received
	 */
	public void basicreceive(int peer,Message message) {
		MyMessage receivedMessage = ((MyMessage)message);
        boolean isAlreadyContain = false;
		if (id == sequencerId){
			//sequencer, 
			if (receivedMessage.messageType.equals("initial")){
                mcui.debug("in sequencer");
                mcui.debug(""+receivedMessage.history.size());
				receivedMessage.setMessageType("order");
                receivedMessage.setSequence(globalSequence);
				sequencerCast(receivedMessage);
				mcui.debug("Sent out: \""+receivedMessage.text+"\"");
				mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
				deliveredList.add(receivedMessage);
				historyList.add(receivedMessage);  // Add the message to history list before broadcasting
                globalSequence++;
			}
		} else{
            if (receivedMessage.sequence > globalSequence) {
                globalSequence = receivedMessage.sequence+1;
            }
            //check for causal ordering first
            causalDeliver(receivedMessage);

			//not a sequencer, wait until localSequence == S, then deliver a message
			//put the received message in a queue if not yet delivered the message
			if (localSequence != receivedMessage.sequence){
				if (!isAlreadyContain(deliveredList, receivedMessage)) {
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
				if (!isAlreadyContain(deliveredList, receivedMessage)){
					reliableCast(receivedMessage);
				}
				mcui.debug("add message to deliveredList");
				mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
				deliveredList.add(receivedMessage);
                if (receivedMessage.getSender() != id){
                    historyList.add(receivedMessage);
                }
				localSequence++;
			}

			//loop though all items in queue and deliver it if message.sequence == localSequence
			Iterator<MyMessage> iterator = holdBackQueue.iterator();
			while (iterator.hasNext()) {
				mcui.debug("loop to try to deliver a message");
				MyMessage msg = (MyMessage)iterator.next();
				if (msg.sequence == localSequence){
                    if (!isAlreadyContain(deliveredList, msg)){
                        mcui.deliver(msg.getSender(), msg.text);
                        deliveredList.add(msg);
                        historyList.add(msg);
                        iterator.remove();
                        localSequence++;
                        reliableCast(msg);
                    }
				}
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
        activeHost[peer] = 0;
		// Select a new sequencer
		if(sequencerId == peer) {
            sequencerId = getMinActiveHost();
		}
        mcui.debug("Now Sequencer is : " + sequencerId);
	}

	public int getMinActiveHost() {
		for(int i=0; i<hosts;i++){
			if (activeHost[i] == 1) {
				return i;
			}
		}
		return -1;
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

    public void causalDeliver(MyMessage receivedMessage){
        for (MyMessage history : receivedMessage.history){
            mcui.debug("inside message history");
            mcui.debug(history.text);
            if (!isAlreadyContain(deliveredList, history)){
                //deliver
                mcui.deliver(history.getSender(), history.text);
                deliveredList.add(history);
                historyList.add(history);
                localSequence++;
            }
        }
    }

    public boolean isAlreadyContain(ArrayList<MyMessage> messageList, MyMessage message){
        boolean isAlreadyContain = false;
        for (MyMessage m : messageList){
            if (message.isSameMessage(m)) {
                isAlreadyContain = true;
            }
        }
        return isAlreadyContain; 
    }
}
