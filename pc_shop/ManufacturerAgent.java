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
import set10111.pc_shop.SupplierAgent.EndDayListener;

public class ManufacturerAgent extends Agent {

	private AID tickerAgent;
	private ArrayList<AID> suppliers = new ArrayList<>();
	private HashMap<String,ArrayList<Offer>> currentOffers = new HashMap<>();
	private String[] parts;
	private String order;
	private int queriesSent;
	private String[] proposal;

	@Override
	protected void setup() {
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
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					dailyActivity.addSubBehaviour(new ReceiveCustomerOrder(myAgent));
					dailyActivity.addSubBehaviour(new FindSuppliers(myAgent));
					dailyActivity.addSubBehaviour(new SupplierEnquiry(myAgent));
					dailyActivity.addSubBehaviour(new CollectOffers(myAgent));
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
			doWait(2000);
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				order = msg.getContent();
				parts = order.split(",");
				System.out.print("FirstPart:" + parts[0]);
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
			queriesSent = 0;
			for (String part : parts) {
				ACLMessage enquire = new ACLMessage(ACLMessage.CFP);

				enquire.setContent(part);
				enquire.setConversationId(part);

				for (AID supplier : suppliers) {
//					System.out.println("supplier: " + supplier);
					enquire.addReceiver(supplier);
					queriesSent++;
				}
				
//				System.out.println("Supp send: " + enquire);
				myAgent.send(enquire);
			}
			System.out.println("No of suppliers: " + suppliers.size());
		}
	}
	
	public class CollectOffers extends Behaviour {
		private int numRepliesReceived = 0;
		
		public CollectOffers(Agent a) {
			super(a);
			currentOffers.clear();
		}

		
		@Override
		public void action() {
			boolean received = false;
			for(String part : parts) {
				MessageTemplate mt = MessageTemplate.MatchConversationId(part);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					received = true;
					numRepliesReceived++;
					if(msg.getPerformative() == ACLMessage.PROPOSE) {
						//we have an offer
						if(!currentOffers.containsKey(part)) {
							proposal = msg.getContent().split(",");
							ArrayList<Offer> offers = new ArrayList<>();
							
							offers.add(new Offer(msg.getSender(),
									Integer.parseInt(proposal[0]),Integer.parseInt(proposal[1])));
							currentOffers.put(part, offers);
						}
						//subsequent offers
						else {
							ArrayList<Offer> offers = currentOffers.get(part);
							offers.add(new Offer(msg.getSender(),
									Integer.parseInt(proposal[0]),Integer.parseInt(proposal[1])));
						}			
					}
				}
			}
			if(!received) {
				block();
			}
		}

		@Override
		public boolean done() {
			return numRepliesReceived == queriesSent;
		}

		@Override
		public int onEnd() {
			//print the offers
			for(String part : parts) {
				if(currentOffers.containsKey(part)) {
					ArrayList<Offer> offers = currentOffers.get(part);
					for(Offer o : offers) {
						System.out.println(part + "," + o.getSeller().getLocalName() + ",£" + o.getPrice() + " DT: "+ o.getDeliveryTimeDays());
					}
				}
				else {
					System.out.println("No offers for " + part);
				}
			}
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
			System.out.println(tickerAgent);
			msg.setContent("done");
			myAgent.send(msg);
			// send a message to each seller that we have finished
			ACLMessage supDone = new ACLMessage(ACLMessage.INFORM);
			supDone.setContent("done");
			for (AID supp : suppliers) {
				myAgent.send(supDone);
			}
		}

	}
}
