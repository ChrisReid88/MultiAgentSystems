package set10111.pc_shop;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

// Start the application and start a customer agent, a manufacturer agent and supplier agent.
public class Application {

	public static void main(String[] args) {
		
		Profile myProfile = new ProfileImpl();
		Runtime myRuntime = Runtime.instance();
		ContainerController myContainer = myRuntime.createMainContainer(myProfile);
		try {
			//Start the agent controller
			AgentController rma = myContainer.createNewAgent("rma", "jade.tools.rma.rma", null);
			rma.start();
			
			AgentController customer = myContainer.createNewAgent("customer", CustomerAgent.class.getCanonicalName(), null);
			customer.start();
			
			AgentController manufacturer = myContainer.createNewAgent("manufacturer", ManufacturerAgent.class.getCanonicalName(), null);
			manufacturer.start();
			
			AgentController supplier = myContainer.createNewAgent("supplier", SupplierAgent.class.getCanonicalName(), null);
			supplier.start();

			AgentController ticker = myContainer.createNewAgent("ticker", TickerAgent.class.getCanonicalName(), null);
			ticker.start();
			
		} catch (Exception e) {
			System.out.println("Exception starting agent: " + e.toString());
		}

	}

}
