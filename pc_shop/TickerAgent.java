package set10111.pc_shop;

import jade.core.Agent;
import java.util.ArrayList;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class TickerAgent extends Agent {

	public static final int NUM_DAYS = 90;

	protected void setup() {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("ticker-agent");
		sd.setName(getLocalName() + "-ticker-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);

		} catch (FIPAException e) {
			e.printStackTrace();
		}
		doWait(2000);
		addBehaviour(new SynchAgentsBehaviour(this));
	}

	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	public class SynchAgentsBehaviour extends Behaviour {

		private int step = 0;
		private int numFinReceived = 0;
		int day = 0;
		private ArrayList<AID> systemAgents = new ArrayList<>();

		public SynchAgentsBehaviour(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			switch (step) {
			case 0:
				DFAgentDescription template1 = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("customer");
				template1.addServices(sd);

				DFAgentDescription template2 = new DFAgentDescription();
				ServiceDescription sd2 = new ServiceDescription();
				sd2.setType("manufacturer");
				template2.addServices(sd2);

				DFAgentDescription template3 = new DFAgentDescription();
				ServiceDescription sd3 = new ServiceDescription();
				sd3.setType("supplier");
				template3.addServices(sd3);

				try {
					DFAgentDescription[] agentsType1  = DFService.search(myAgent,template1); 
					for(int i=0; i<agentsType1.length; i++){
						systemAgents.add(agentsType1[i].getName()); // this is the AID
					}
					DFAgentDescription[] agentsType2  = DFService.search(myAgent,template2); 
					for(int i=0; i<agentsType2.length; i++){
						systemAgents.add(agentsType2[i].getName()); // this is the AID
					}
					
					DFAgentDescription[] agentsType3  = DFService.search(myAgent,template3); 
					for(int i=0; i<agentsType3.length; i++){
						systemAgents.add(agentsType3[i].getName()); // this is the AID
					}
					
				} catch (FIPAException e) {
					e.printStackTrace();
				}

				// Send new day message to agents
				ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
				tick.setContent("new day");
				for (AID id : systemAgents) {
					tick.addReceiver(id);
				}
				myAgent.send(tick);
				step++;
				day++;
				break;
			case 1:
				MessageTemplate mt = MessageTemplate.MatchContent("done");
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					numFinReceived++;
					
					if (numFinReceived >= systemAgents.size()) {
						step++;
					}
				} else {
					block();
				}
			}
		}

		@Override
		public boolean done() {
			return step == 2;
		}

		@Override
		public void reset() {
			super.reset();
			step = 0;
			numFinReceived = 0;
			systemAgents.clear();
		}

		@Override
		public int onEnd() {
			System.out.println("End of Day " + day);
			if (day == NUM_DAYS) {
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.setContent("terminate");
				for (AID agent : systemAgents) {
					msg.addReceiver(agent);
				}
				myAgent.send(msg);
				myAgent.doDelete();
			} else {
				reset();
				myAgent.addBehaviour(this);
			}
			return 0;
		}
	}
}
