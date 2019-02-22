import mcgui.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

public class ReliableOrderedCaster extends Multicaster {
	private int sequencerId;
    private int globalSequence; //sequence used by sequencer
	private int localSequence; 
    private int createdAt; 
	private int[] activeHost; //list of all avtive hosts
	private ArrayList<MyMessage> historyList = new ArrayList<MyMessage>(); //keep track of history since last broadcast
	private ArrayList<MyMessage> deliveredList = new ArrayList<MyMessage>(); //keep track of all delivered message
    private ArrayList<MyMessage> notDeliveredList = new ArrayList<MyMessage>(); //keep track of all sent message but not yet received back
	private LinkedList<MyMessage> holdBackQueue = new LinkedList<MyMessage>(); //keep message unti localSequence == message.sequence

	public void init() {
		activeHost = new int[hosts];
		for(int i = 0; i<hosts; i++){
			activeHost[i] = 1;
		}
		sequencerId = getMinActiveHost();
		assert sequencerId == -1 : "Free Sequencer not found";
        globalSequence = 0;
		localSequence = 0;
        createdAt = 0;
		mcui.debug("Sequencer is "+ sequencerId);
		mcui.debug("The network has "+hosts+" hosts!");
	}

	/**
	 * The GUI calls this module to multicast a message
	 */
	public void cast(String messagetext) {
        MyMessage message = new MyMessage(id,messagetext,0,"initial", createdAt++);
        Collections.sort(historyList, new Comparator<MyMessage>(){
                @Override
                public int compare(MyMessage m1, MyMessage m2){
                return m1.sequence - m2.sequence;
            }
        });
		if (id == sequencerId){
			//sequencer
            message.setMessageType("order");
            message.setSequence(globalSequence);
            message.setHistory(historyList); // attach history before broadcasting
			sequencerCast(message);
			mcui.deliver(id, messagetext);
			deliveredList.add(message);
            historyList = new ArrayList<MyMessage>(); // clear histroy
            globalSequence++;

		} else {
			//node is not a sequencer, send to sequencer
            message.setHistory(historyList); // attach history before sending to sequencer
			bcom.basicsend(sequencerId, message);
            historyList = new ArrayList<MyMessage>(); // clear history
            notDeliveredList.add(message); // add message to notDeliveredList
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
			if (receivedMessage.messageType.equals("initial")){
				receivedMessage.setMessageType("order");
                receivedMessage.setSequence(globalSequence); // assign sequence to message
				sequencerCast(receivedMessage);
				mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
				deliveredList.add(receivedMessage);
				historyList.add(receivedMessage);  // Add the message to history list before broadcasting
                globalSequence++;
			}
		} 
        else {
            //not a sequencer 
            //adjust global clock
            if (receivedMessage.sequence > globalSequence) {
                globalSequence = receivedMessage.sequence+1;
            }
            checkHistory(receivedMessage); // check message's history first
            basicDeliver(receivedMessage); 
            checkHoldBackQueue(); //try to deliver message in holdBackQueue if possible
		}

        //remove message in notDelivereList since the message is received
        if (receivedMessage.getSender() == id) {
            Iterator<MyMessage> iterator = notDeliveredList.iterator();
            while (iterator.hasNext()){
                MyMessage m = (MyMessage)iterator.next();
                if (receivedMessage.text.equals(m.text) && receivedMessage.createdAt == m.createdAt){
                    iterator.remove();    
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

        //after a new sequencer is selected, send every message in notDeliveredList to the new sequencer    
        for (MyMessage m : notDeliveredList) {
            bcom.basicsend(sequencerId, m);
        }
	}

	public int getMinActiveHost() {
		for(int i=0; i<hosts;i++){
			if (activeHost[i] == 1) {
				return i;
			}
		}
		return -1;
	}

	public void sequencerCast(MyMessage message){
		for(int i=0; i < hosts; i++) {
			/* Sends to everyone except itself */
			if(i != id) {
				bcom.basicsend(i, message);
			}
		}
	}

	public void reliableCast(MyMessage message){
		//broadcast to others to provide reliable broadcast
		for(int i=0; i < hosts; i++) {
			/* Sends to everyone except itself */
			if(i != id) {
				bcom.basicsend(i, message);
			}
		}
	}

    public void checkHoldBackQueue(){
        //loop though all items in queue and deliver it if message.sequence == localSequence
        Iterator<MyMessage> iterator = holdBackQueue.iterator();
        while (iterator.hasNext()) {
            MyMessage msg = (MyMessage)iterator.next();
            if (localSequence < msg.sequence) {
                if (msg.sequence == localSequence){
                    if (!isAlreadyContain(deliveredList, msg)){
                        mcui.deliver(msg.getSender(), msg.text);
                        deliveredList.add(msg);
                        if (msg.getSender() != id){
                            historyList.add(msg);
                        }
                        iterator.remove();
                        localSequence++;
                        reliableCast(msg);
                    }
                }
            }
            else {
                break;
            }
        }
    }

    public void checkHistory(MyMessage receivedMessage){
        for (MyMessage history : receivedMessage.history){
            if (!isAlreadyContain(deliveredList, history)){
                //deliver
                mcui.deliver(history.getSender(), history.text);
                deliveredList.add(history);
                if (history.getSender() != id){
                    historyList.add(history);
                }
                localSequence++;
                reliableCast(history);
            }
        }
    }

    public void basicDeliver(MyMessage receivedMessage){
        //wait until localSequence == receivedMessage.sequence, then deliver a message
        if (localSequence != receivedMessage.sequence){
            //put the received message in a queue if not yet delivered the message
            if (!isAlreadyContain(deliveredList, receivedMessage)) {
                holdBackQueue.add(receivedMessage);
                Collections.sort(holdBackQueue, new Comparator<MyMessage>(){
                        @Override
                        public int compare(MyMessage m1, MyMessage m2){
                        return m1.sequence - m2.sequence;
                    }
                });
            }
        } 
        else {
            //deliver
            if (!isAlreadyContain(deliveredList, receivedMessage)){
                mcui.deliver(receivedMessage.getSender(), receivedMessage.text);
                deliveredList.add(receivedMessage);
                if (receivedMessage.getSender() != id){
                    historyList.add(receivedMessage);
                }
                localSequence++;
                //if the message is delivered for the first time, broadcast to every other nodes
                reliableCast(receivedMessage);
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
