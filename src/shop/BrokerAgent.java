package shop;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import shop.BehaviourTree.*;
import java.util.HashMap;
import java.util.Map;

public class BrokerAgent extends Agent {
    private Main gui;
    private Map<String, AID> activeCustomers = new HashMap<>();
    private Map<String, String> activeItems = new HashMap<>();
    private Map<String, Integer> customerAttempts = new HashMap<>();
    private ACLMessage currentMsg = null;
    private Node decisionTree;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) gui = (Main) args[0];

        if (gui != null) gui.log("🤝 Broker ready.");

        decisionTree = buildDecisionTree();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    currentMsg = msg;
                    decisionTree.tick();
                } else {
                    block();
                }
            }
        });
    }

    private Node buildDecisionTree() {
        return new Selector(
                buildCustomerRequestBranch(),
                buildInventoryConfirmBranch(),
                buildInventoryDisconfirmBranch(),
                buildProposeBranch(),
                buildCustomerAcceptBranch(),
                buildCustomerRejectBranch(),
                buildDeliveredBranch()
        );
    }

    private Node buildCustomerRequestBranch() {
        return new Sequence(
                new Condition(() ->
                        currentMsg.getPerformative() == ACLMessage.REQUEST
                                && currentMsg.getSender().getLocalName().startsWith("Customer")),
                new Action(() -> {
                    String customerName = currentMsg.getSender().getLocalName();
                    String requestedItem = currentMsg.getContent();

                    activeCustomers.put(customerName, currentMsg.getSender());
                    activeItems.put(customerName, requestedItem);
                    customerAttempts.merge(customerName, 1, Integer::sum);

                    if (gui != null) gui.log("🤝 Broker: Request from " + customerName
                            + " (attempt #" + customerAttempts.get(customerName) + ") for " + requestedItem);

                    ACLMessage check = new ACLMessage(ACLMessage.QUERY_IF);
                    check.addReceiver(new AID("Inventory1", AID.ISLOCALNAME));
                    check.setContent(requestedItem);
                    check.setReplyWith(customerName);
                    send(check);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildInventoryConfirmBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.CONFIRM),
                new Action(() -> {
                    String trackingTag = currentMsg.getReplyWith();
                    String item = activeItems.get(trackingTag);
                    if (gui != null) gui.log("🤝 Broker: " + item + " in stock for " + trackingTag + ", requesting price...");

                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(new AID("Seller1", AID.ISLOCALNAME));
                    cfp.setContent(item);
                    cfp.setReplyWith(trackingTag);
                    send(cfp);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildInventoryDisconfirmBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.DISCONFIRM),
                new Action(() -> {
                    String trackingTag = currentMsg.getReplyWith();
                    AID actualCustomer = activeCustomers.get(trackingTag);
                    String item = activeItems.get(trackingTag);

                    if (gui != null) gui.log("🤝 Broker: Out of stock for " + item + " (" + trackingTag + ")");

                    ACLMessage reply = new ACLMessage(ACLMessage.FAILURE);
                    reply.addReceiver(actualCustomer);
                    reply.setContent("OUT_OF_STOCK");
                    send(reply);

                    activeCustomers.remove(trackingTag);
                    activeItems.remove(trackingTag);
                    customerAttempts.remove(trackingTag);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildProposeBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.PROPOSE),
                new Action(() -> {
                    String trackingTag = currentMsg.getReplyWith();
                    AID actualCustomer = activeCustomers.get(trackingTag);
                    double price = Double.parseDouble(currentMsg.getContent());

                    if (gui != null) gui.log("🤝 Broker: Forwarding price $"
                            + String.format("%.2f", price) + " to " + trackingTag);

                    ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                    inform.addReceiver(actualCustomer);
                    inform.setContent("PRICE:" + price);
                    send(inform);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildCustomerAcceptBranch() {
        return new Sequence(
                new Condition(() ->
                        currentMsg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL
                                && currentMsg.getSender().getLocalName().startsWith("Customer")),
                new Action(() -> {
                    String customerName = currentMsg.getSender().getLocalName();
                    String item = activeItems.get(customerName);

                    if (gui != null) gui.log("🤝 Broker: " + customerName + " accepted after "
                            + customerAttempts.get(customerName) + " attempt(s). Finalizing...");

                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.addReceiver(new AID("Seller1", AID.ISLOCALNAME));
                    accept.setContent(item);
                    send(accept);

                    ACLMessage ship = new ACLMessage(ACLMessage.REQUEST);
                    ship.addReceiver(new AID("Shipping1", AID.ISLOCALNAME));
                    ship.setContent(item + "," + customerName);
                    send(ship);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildCustomerRejectBranch() {
        return new Sequence(
                new Condition(() ->
                        currentMsg.getPerformative() == ACLMessage.REJECT_PROPOSAL
                                && currentMsg.getSender().getLocalName().startsWith("Customer")),
                new Action(() -> {
                    String customerName = currentMsg.getSender().getLocalName();
                    String item = activeItems.get(customerName);
                    int attempts = customerAttempts.getOrDefault(customerName, 1);

                    ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                    reject.addReceiver(new AID("Seller1", AID.ISLOCALNAME));
                    send(reject);

                    return Status.SUCCESS;
                }),
                new Selector(
                        new Sequence(
                                new Condition(() -> {
                                    String customerName = currentMsg.getSender().getLocalName();
                                    int attempts = customerAttempts.getOrDefault(customerName, 1);
                                    boolean maxed = attempts >= 3;
                                    if (maxed && gui != null)
                                        gui.log("🌳 BT Broker: Condition met — max attempts reached, dropping " + customerName);
                                    return maxed;
                                }),
                                new Action(() -> {
                                    String customerName = currentMsg.getSender().getLocalName();
                                    String item = activeItems.get(customerName);

                                    if (gui != null) gui.log("🤝 Broker [BT Decision]: Dropping " + customerName);

                                    ACLMessage restock = new ACLMessage(ACLMessage.CANCEL);
                                    restock.addReceiver(new AID("Inventory1", AID.ISLOCALNAME));
                                    restock.setContent(item);
                                    send(restock);

                                    activeCustomers.remove(customerName);
                                    activeItems.remove(customerName);
                                    customerAttempts.remove(customerName);
                                    return Status.SUCCESS;
                                })
                        ),
                        new Action(() -> {
                            String customerName = currentMsg.getSender().getLocalName();
                            String item = activeItems.get(customerName);
                            int attempts = customerAttempts.getOrDefault(customerName, 1);

                            if (gui != null) gui.log("🌳 BT Broker: Re-entering negotiation for "
                                    + customerName + " (attempt " + attempts + "/3)");

                            ACLMessage restock = new ACLMessage(ACLMessage.CANCEL);
                            restock.addReceiver(new AID("Inventory1", AID.ISLOCALNAME));
                            restock.setContent(item);
                            send(restock);

                            ACLMessage checkAgain = new ACLMessage(ACLMessage.QUERY_IF);
                            checkAgain.addReceiver(new AID("Inventory1", AID.ISLOCALNAME));
                            checkAgain.setContent(item);
                            checkAgain.setReplyWith(customerName);
                            send(checkAgain);
                            return Status.SUCCESS;
                        })
                )
        );
    }

    private Node buildDeliveredBranch() {
        return new Sequence(
                new Condition(() ->
                        currentMsg.getPerformative() == ACLMessage.INFORM
                                && currentMsg.getContent().startsWith("DELIVERED")),
                new Action(() -> {
                    String[] parts = currentMsg.getContent().split(",");
                    String customerName = parts[1];
                    AID actualCustomer = activeCustomers.get(customerName);
                    String item = activeItems.get(customerName);

                    if (gui != null) gui.log("🤝 Broker: Delivery confirmed for " + customerName + "!");

                    ACLMessage done = new ACLMessage(ACLMessage.INFORM);
                    done.addReceiver(actualCustomer);
                    done.setContent("DELIVERED:" + item);
                    send(done);

                    activeCustomers.remove(customerName);
                    activeItems.remove(customerName);
                    customerAttempts.remove(customerName);
                    return Status.SUCCESS;
                })
        );
    }
}