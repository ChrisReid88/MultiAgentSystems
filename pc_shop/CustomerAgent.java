package set10111.pc_shop;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Random;

public class CustomerAgent extends Agent {

	private AID tickerAgent;
	private ArrayList<AID> manufacturers = new ArrayList<>();

	private String order;

	protected void setup() {
		System.out.println("Customer-agent " + getAID().getName() + " is ready.");
		// Register with Yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("customer");
		sd.setName(getLocalName() + "-customer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

		addBehaviour(new TickerWaiter(this));
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

	public class TickerWaiter extends CyclicBehaviour {
		public TickerWaiter(Agent a) {
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
					// spawn new sequential behaviour for day's activities
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					// sub-behaviours will execute in the order they are added
					dailyActivity.addSubBehaviour(new FindManufacturers(myAgent));
					dailyActivity.addSubBehaviour(new GenerateCustomerOrders());
					dailyActivity.addSubBehaviour(new SendOrder(myAgent));
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

	public class FindManufacturers extends OneShotBehaviour {
		public FindManufacturers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription manuTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			manuTemplate.addServices(sd);
			try {
				manufacturers.clear();
				DFAgentDescription[] agentType1 = DFService.search(myAgent, manuTemplate);
				for (int i = 0; i < agentType1.length; i++) {
					manufacturers.add(agentType1[i].getName());
				}
			} catch (FIPAException e) {
				e.printStackTrace();
			}
		}
	}

	public class GenerateCustomerOrders extends OneShotBehaviour {
		public void action() {
			String cpu = "";
			String motherboard = "";
			String screen = "";
			String memory = "";
			String storage = "";
			String os = "";
			int quantity;
			double price;
			int dueInDays;
			order = "";
			
			// Get random generated order
			quantity = (int) Math.floor(1 + 50 * Math.random());
			price = quantity * Math.floor(600 + 200 * Math.random());
			dueInDays = (int) Math.floor(1 + 10 * Math.random());

			// Choose between laptop and desktop pc
			if (Math.random() < 0.5) {
				cpu = "desktopCPU";
				motherboard = "desktopMotherboard";
				screen = "noScreen";
			} else {
				cpu = "laptopCPU";
				motherboard = "laptopMotherboard";
				screen = "screen";
			}
			// Choose memory size
			if (Math.random() < 0.5) {
				memory = "8Gb";

			} else {
				memory = "16Gb";
			}
			// Chose storage size
			if (Math.random() < 0.5) {
				storage = "1Tb";

			} else {
				storage = "2Tb";
			}
			// Choose operating system
			if (Math.random() < 0.5) {
				os = "windows";

			} else {
				os = "linux";
			}
			order = cpu + "," + motherboard + "," + screen + "," + memory + "," + storage + "," + os + "," + quantity
					+ "," + price + "," + dueInDays;
			System.out.println(order);
		}
	}

	public class SendOrder extends OneShotBehaviour {
		public SendOrder(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			ACLMessage pcOrder = new ACLMessage(ACLMessage.INFORM);
			pcOrder.setContent(order);
			pcOrder.setConversationId("order");
			for (AID manu : manufacturers) {
				pcOrder.addReceiver(manu);
			}
			myAgent.send(pcOrder);
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
		}
	}
}
