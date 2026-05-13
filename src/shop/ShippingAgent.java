package shop;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class ShippingAgent extends Agent {
    private Main gui;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) gui = (Main) args[0];

        if (gui != null) {
            gui.log("🚚 Shipping ready");
            gui.updateAgentStatus("Shipping1", "Active", new java.awt.Color(26, 188, 156));
        }

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && msg.getPerformative() == ACLMessage.REQUEST) {
                    handleShippingRequest(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void handleShippingRequest(ACLMessage msg) {
        String[] parts = msg.getContent().split(",");
        String item = parts[0];
        String customer = parts[1];

        if (gui != null) {
            gui.log("🚚 Shipping: Processing " + item + " for " + customer);
            gui.updateAgentStatus("Shipping1", "Delivering", new java.awt.Color(231, 76, 60));
        }

        // Simulate delivery time
        new Thread(() -> {
            try {
                Thread.sleep(2000);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("DELIVERED," + customer);
                send(reply);

                if (gui != null) {
                    gui.log("✅ Shipping: " + item + " DELIVERED to " + customer);
                    gui.updateAgentStatus("Shipping1", "Active", new java.awt.Color(26, 188, 156));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}