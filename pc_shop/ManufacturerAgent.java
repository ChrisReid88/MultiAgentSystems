package set10111.pc_shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

public class ManufacturerAgent extends Agent {

	private AID tickerAgent;
	private ArrayList<AID> suppliers = new ArrayList<>();
	private ArrayList<String> partsToOrder = new ArrayList<>();
	private ArrayList<String> partsOrdered = new ArrayList<>();
	private HashMap<String, ArrayList<Offer>> currentOffers = new HashMap<>();
	private HashMap<String, Integer> stock = new HashMap<>();
	private String order;
	private int queriesSent;
	private String[] proposal;
	private double income;
	private int outcome;

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
		addBehaviour(new TickerDayWaiter(this));
	}

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

	public class TickerDayWaiter extends CyclicBehaviour {
		public TickerDayWaiter(Agent a) {
			super(a);
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
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					dailyActivity.addSubBehaviour(new FindSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveCustomerOrder(myAgent));
					dailyActivity.addSubBehaviour(new SupplierEnquiry(myAgent));
					dailyActivity.addSubBehaviour(new CollectOffers(myAgent));
					dailyActivity.addSubBehaviour(new RespondToSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new ReceiveOrder(myAgent));
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
				System.out.println(income);
				for (int i = 0; i < 6; i++) {
					partsToOrder.add(partSep[i]);
				}
			} else {
				block();
			}
		}
	}

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

				}

			} catch (FIPAException e) {
				e.printStackTrace();
			}
		}
	}

	public class SupplierEnquiry extends OneShotBehaviour {
		public SupplierEnquiry(Agent a) {
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

	public class CollectOffers extends Behaviour {
		private int numRepliesReceived = 0;

		public CollectOffers(Agent a) {
			super(a);
			currentOffers.clear();
			doWait(500);
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
					}
				}
			}
		}
	}

	public class ReceiveOrder extends Behaviour {
		private String partsReceived;
		private double partPrice;

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
				partPrice = Double.parseDouble(sep[1]);
				partsOrdered.add(sep[0]);
				outcome += partPrice;
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
			System.out.println(partsOrdered.size());
			System.out.println("Part price: " + outcome);

			return 0;
		}

	}

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
			// send a message to each seller that we have finished
			ACLMessage supDone = new ACLMessage(ACLMessage.INFORM);
			supDone.setContent("done");
			for (AID supplier : suppliers) {
				supDone.addReceiver(supplier);
			}
			myAgent.send(supDone);
		}
	}

}
