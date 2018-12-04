package set10111.pc_shop;

import jade.core.AID;

public class Offer {
	private AID seller;
	private int price;
	private int deliveryTimeDays;
	
	public Offer(AID seller, int price, int deliveryTimeDays) {
		super();
		this.seller = seller;
		this.price = price;
		this.deliveryTimeDays = deliveryTimeDays;
	}
	
		

	public int getDeliveryTimeDays() {
		return deliveryTimeDays;
	}



	public AID getSeller() {
		return seller;
	}
	
	public int getPrice() {
		return price;
	}
	
}
