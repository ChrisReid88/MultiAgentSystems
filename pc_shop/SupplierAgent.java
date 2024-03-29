package set10111.pc_shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

//Supplier agent that sends parts to the manufacturer
public class SupplierAgent extends Agent {

	private HashMap<String, Integer> partsForSale = new HashMap<>();

	private ArrayList<AID> manufacturers = new ArrayList<>();
	private AID tickerAgent;
	private int deliveryTime = 1;
	private int day = 1;
	private String message;

	protected void setup() {

		System.out.println("Supplier-agent " + getAID().getName() + " is ready.");
		// Register with Yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("supplier");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		addBehaviour(new TickerDayWaiter(this));
		partsForSale.clear();

		partsForSale.put("laptopCPU", 200);
		partsForSale.put("desktopCPU", 150);
		partsForSale.put("laptopMotherboard", 125);
		partsForSale.put("desktopMotherboard", 75);
		partsForSale.put("8Gb", 50);
		partsForSale.put("16Gb", 90);
		partsForSale.put("1Tb", 50);
		partsForSale.put("2Tb", 75);
		partsForSale.put("windows", 75);
		partsForSale.put("linux", 0);
		partsForSale.put("screen", 100);
	}

	// Deregister from the yellow pages
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Supplier-agent " + getAID().getName() + " terminating.");
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	// synchronise agent on new day
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
					myAgent.addBehaviour(new FindManufacturers(myAgent));
					CyclicBehaviour os = new OffersServer(myAgent);
					myAgent.addBehaviour(os);
					CyclicBehaviour pos = new PurchaseOffersServer(myAgent);
					myAgent.addBehaviour(pos);
					ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
					cyclicBehaviours.add(os);
					cyclicBehaviours.add(pos);
					myAgent.addBehaviour(new EndDayListener(myAgent, cyclicBehaviours));
				} else {
					// termination message to end simulation
					myAgent.doDelete();
				}
			} else {
				block();
			}
		}
	}

	// Save manufactures AID's in an array
	public class FindManufacturers extends OneShotBehaviour {

		public FindManufacturers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription manufacturerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("manufacturer");
			manufacturerTemplate.addServices(sd);
			try {
				manufacturers.clear();
				DFAgentDescription[] agentsType1 = DFService.search(myAgent, manufacturerTemplate);
				for (int i = 0; i < agentsType1.length; i++) {
					manufacturers.add(agentsType1[i].getName()); // this is the AID
				}
			}

			catch (FIPAException e) {
				e.printStackTrace();
			}

		}

	}

	// Server to listen for call-for-proposals and makes an offer.
	public class OffersServer extends CyclicBehaviour {

		public OffersServer(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage reply = msg.createReply();
				String parts = msg.getContent();
				// we can send an offer
				if (partsForSale.containsKey(parts)) {
					String partPrice = String.valueOf(partsForSale.get(parts));
					String message = partPrice + "," + deliveryTime;
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(message);
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
				}
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}

	// Server to listen for accept proposals
	public class PurchaseOffersServer extends CyclicBehaviour {

		public PurchaseOffersServer(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String part = msg.getContent();
				ACLMessage reply = msg.createReply();
				int dayOfDelivery = deliveryTime + day;
				if (partsForSale.containsKey(part)) {
					String partPrice = String.valueOf(partsForSale.get(part));
					message = part + "," + partPrice + "," + dayOfDelivery;
					reply.setPerformative(ACLMessage.INFORM);
					reply.setContent(message);
				}
				myAgent.send(reply);

			} else {
				block();
			}
		}
	}

	// listen for a done message from the manufacturer. When received send
	// one to the ticker agent to inform of end of day.
	public class EndDayListener extends CyclicBehaviour {
		private int manuFinished = 0;
		private List<Behaviour> toRemove;

		public EndDayListener(Agent a, List<Behaviour> toRemove) {
			super(a);
			this.toRemove = toRemove;
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchContent("done");
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				manuFinished++;
			} else {
				block();
			}
			if (manuFinished == manufacturers.size()) {
				// we are finished
				ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
				tick.setContent("done");
				tick.addReceiver(tickerAgent);
				myAgent.send(tick);
				// remove behaviours
				for (Behaviour b : toRemove) {
					myAgent.removeBehaviour(b);
				}
				day++;
				myAgent.removeBehaviour(this);
			}
		}
	}
}