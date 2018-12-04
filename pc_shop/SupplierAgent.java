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
import set10111.pc_shop.ManufacturerAgent.TickerDayWaiter;

public class SupplierAgent extends Agent {

	private HashMap<String, Integer> partsForSale = new HashMap<>();

	private ArrayList<AID> manufacturers = new ArrayList<>();
	private AID tickerAgent;
	private int deliveryTime = 1;

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

	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Customer-agent " + getAID().getName() + " terminating.");
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
					myAgent.addBehaviour(new FindManufacturers(myAgent));
					myAgent.addBehaviour(new SupplierType());
					CyclicBehaviour os = new OffersServer(myAgent);
					myAgent.addBehaviour(os);
					ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
					cyclicBehaviours.add(os);
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

	public class FindManufacturers extends OneShotBehaviour {

		public FindManufacturers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription buyerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("manufacturer");
			buyerTemplate.addServices(sd);
			try {
				manufacturers.clear();
				DFAgentDescription[] agentsType1 = DFService.search(myAgent, buyerTemplate);
				for (int i = 0; i < agentsType1.length; i++) {
					manufacturers.add(agentsType1[i].getName()); // this is the AID
				}
				System.out.println("\nManuf: " + manufacturers);
			}

			catch (FIPAException e) {
				e.printStackTrace();
			}

		}

	}

	public class SupplierType extends OneShotBehaviour {
		public void action() {
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
	}

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
				System.out.println(reply);
				myAgent.send(reply);
			}
			else {
				block();
			}

		}

	}

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
				myAgent.removeBehaviour(this);
			}
		}

	}
}
