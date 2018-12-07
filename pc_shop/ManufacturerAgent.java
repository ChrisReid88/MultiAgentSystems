package set10111.pc_shop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.leap.LinkedList;

//Takes order from customer, orders parts from supplier and sends orders to customer
public class ManufacturerAgent extends Agent {

	
	private AID tickerAgent;
	private AID customer;
	private ArrayList<AID> suppliers = new ArrayList<>();
	private ArrayList<String> partsToOrder = new ArrayList<>();
	private ArrayList<String> partsOrdered = new ArrayList<>();
	private HashMap<String, ArrayList<Offer>> currentOffers = new HashMap<>();
	private HashMap<String, Integer> stock = new HashMap<>();
	private Queue<Integer> deliveryDates = new ArrayDeque<Integer>();
	private String order;
	private int queriesSent;
	private String[] proposal;
	private double income;
	private double outcome;
	private int quantityOfParts;
	private int day = 1;
	private int deadLine;
	
	@Override
	protected void setup() {

		System.out.println("Manufacturer-agent " + getAID().getName() + " is ready.");
		// add this agent to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("manufacturer");
		sd.setName(getLocalName() + "-manufacturer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		
		//Setting the current stock level of each component as 0.
		stock.put("desktopCPU", 0);
		stock.put("desktopMotherboard", 0);
		stock.put("laptopMotherboard", 0);
		stock.put("laptopCPU", 0);
		stock.put("1Tb", 0);
		stock.put("2Tb", 0);
		stock.put("Screen", 0);
		stock.put("8Gb", 0);
		stock.put("16Gb", 0);
		stock.put("windows", 0);
		stock.put("linux", 0);

		addBehaviour(new TickerDayWaiter(this));
	}

	//De-register from the yellow pages
	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Manufacturer-agent " + getAID().getName() + " terminating.");
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	//ticker waiter that starts the new day and activates the behaviours of the agent.
	public class TickerDayWaiter extends CyclicBehaviour {
		public TickerDayWaiter(Agent a) {
			super(a);
			doWait(100);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("new day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				if (tickerAgent == null) {
					tickerAgent = msg.getSender();
				}
				if (msg.getContent().equals("new day")) {
					System.out.print("Day " + day + "| ");

					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					dailyActivity.addSubBehaviour(new FindSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveCustomerOrder(myAgent));
					dailyActivity.addSubBehaviour(new RequestComponents(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveOffers(myAgent));
					dailyActivity.addSubBehaviour(new RespondToSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveOrder(myAgent));
					dailyActivity.addSubBehaviour(new sendToCust(myAgent));
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					myAgent.addBehaviour(dailyActivity);

				} else {
					// termination message to end simulation
					myAgent.doDelete();
				}
			} else {
				block();
			}
		}
	}
	
	//Receives the customer order String and splits it into relevant properties 
	public class ReceiveCustomerOrder extends OneShotBehaviour {
		public ReceiveCustomerOrder(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			partsToOrder.clear();
			MessageTemplate mt = MessageTemplate.MatchConversationId("order");
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				order = msg.getContent();
				String[] partSep = order.split(",");
				income += Double.parseDouble(partSep[7]);
				deadLine = Integer.parseInt(partSep[6]);
				quantityOfParts = Integer.parseInt(partSep[6]);
				System.out.print("Income from order : " + partSep[7] + " | ");
				for (int i = 0; i < partSep.length - 1; i++) {
					partsToOrder.add(partSep[i]);
				}
			} else {
				block();
			}
		}
	}
	 
	//Get a list of all the suppliers AIDs and store it in an array
	public class FindSuppliers extends OneShotBehaviour {
		public FindSuppliers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("supplier");
			supplierTemplate.addServices(sd);
			try {
				suppliers.clear();
				DFAgentDescription[] agentsType = DFService.search(myAgent, supplierTemplate);
				for (int i = 0; i < agentsType.length; i++) {
					suppliers.add(agentsType[i].getName());
					customer = agentsType[i].getName();
				}

			} catch (FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	//Call for proposal to the suppliers given each part in the order
	public class RequestComponents extends OneShotBehaviour {
		public RequestComponents(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			// send out a call for proposals for each part
			queriesSent = 0;
			for (String part : partsToOrder) {
				ACLMessage enquiry = new ACLMessage(ACLMessage.CFP);
				enquiry.setContent(part);
				enquiry.setConversationId(part);
				for (AID supplier : suppliers) {
					enquiry.addReceiver(supplier);
					queriesSent++;

					myAgent.send(enquiry);
				}
			}
		}
	}

	// Take in the offers that were proposed by the suppliers 
	public class ReceiveOffers extends Behaviour {
		private int numRepliesReceived = 0;

		public ReceiveOffers(Agent a) {
			super(a);
			currentOffers.clear();
			doWait(100);
		}

		@Override
		public void action() {
			boolean received = false;

			for (String part : partsToOrder) {
				MessageTemplate mt = MessageTemplate.MatchConversationId(part);
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					received = true;
					numRepliesReceived++;
					if (msg.getPerformative() == ACLMessage.PROPOSE) {
						// we have an offer
						if (!currentOffers.containsKey(part)) {
							proposal = msg.getContent().split(",");
							ArrayList<Offer> offers = new ArrayList<>();

							offers.add(new Offer(msg.getSender(), Integer.parseInt(proposal[0]),
									Integer.parseInt(proposal[1])));
							currentOffers.put(part, offers);

						}
						// subsequent offers
						else {
							ArrayList<Offer> offers = currentOffers.get(part);
							offers.add(new Offer(msg.getSender(), Integer.parseInt(proposal[0]),
									Integer.parseInt(proposal[1])));
						}
					}
				}
			}

			if (!received) {
				block();
			}
		}

		@Override
		public boolean done() {
			return numRepliesReceived == queriesSent;
		}
	}

	// Accepts the suppliers proposal. Need to add refusal option to other suppliers
	public class RespondToSuppliers extends OneShotBehaviour {

		public RespondToSuppliers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			for (String part : partsToOrder) {
				if (currentOffers.containsKey(part)) {
					ArrayList<Offer> offers = currentOffers.get(part);
					for (Offer o : offers) {
						ACLMessage orderPart = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						orderPart.addReceiver(o.getSeller());
						orderPart.setContent(part);
						myAgent.send(orderPart);
						outcome = 0;
					}
				}
			}
		}
	}

	// receives the order from the supplier 
	public class ReceiveOrder extends Behaviour {
		private String partsReceived;
		private double partPrice;
		int date;
		int previousDate;

		public ReceiveOrder(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				partsReceived = msg.getContent();
				String[] sep = partsReceived.split(",");
				date = Integer.parseInt(sep[2]);
				

				stock.computeIfPresent(sep[0], (k, v) -> v + quantityOfParts);
				deliveryDates.add(date);
				partPrice = Double.parseDouble(sep[1]);

				if (!deliveryDates.isEmpty()) {
					if (day == date) {
						deliveryDates.remove();
						stock.computeIfPresent(sep[0], (k, v) -> v - quantityOfParts);

					} else if (day ==(date+1)) {
						deliveryDates.remove();
						stock.computeIfPresent(sep[0], (k, v) -> v - quantityOfParts);
					}
					
					
				}
				partsOrdered.add(sep[0]);
				outcome += partPrice*quantityOfParts;
			}
		}

		@Override
		public boolean done() {
			boolean receivedAllParts = false;

			if (!partsOrdered.isEmpty()) {

				if (partsOrdered.get(partsOrdered.size() - 1).contains("windows")
						|| partsOrdered.get(partsOrdered.size() - 1).contains("linux"))

					receivedAllParts = true;
			}
			return receivedAllParts;
		}

		@Override
		public int onEnd() {
			
			
			return 0;
		}
	}
	
	//Send the PC to the customer and work out profits/
	public class sendToCust extends OneShotBehaviour {

		public sendToCust(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			int penalty =0;
			ACLMessage pcOrder = new ACLMessage(ACLMessage.INFORM);
			pcOrder.setContent(order);
			pcOrder.setConversationId("order");
			pcOrder.addReceiver(customer);
			myAgent.send(pcOrder);
			
			if (day > deadLine ) {
				int overdue = day - deadLine;
				penalty = overdue * 20; 
			}
			income = ((income - outcome) - penalty);
			System.out.print("Supplies Purchased: " + outcome + " | ");
			System.out.print("Cumulative profit: " + ((income - outcome) - penalty)  + " | ");
			System.out.print("PC's sent to customer: " + quantityOfParts + " | ");
			System.out.print("Late penalty: £" + penalty + "\n");
			;
		}
	}

	
	// Send "done" to the supplier and and the ticker agent to finish the day
	public class EndDay extends OneShotBehaviour {

		public EndDay(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(tickerAgent);
			msg.setContent("done");
			myAgent.send(msg);
			// send a message to each supplier that we have finished
			ACLMessage supDone = new ACLMessage(ACLMessage.INFORM);
			supDone.setContent("done");
			for (AID supplier : suppliers) {
				supDone.addReceiver(supplier);
			}
			myAgent.send(supDone);
			day++;
		}
	}

}
