package shop;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import shop.BehaviourTree.*;

public class CustomerAgent extends Agent {
    private Main gui;
    private double budget = 1000.0;
    private String targetItem = "laptop";
    private boolean requestSent = false;
    private int negotiationRounds = 0;
    private static final int MAX_ROUNDS = 3;
    private ACLMessage currentMsg = null;
    private Node decisionTree;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 3) {
            gui = (Main) args[0];
            budget = (Double) args[1];
            targetItem = (String) args[2];
        }

        if (gui != null) {
            gui.log("👤 " + getLocalName() + " ready. Budget: $"
                    + String.format("%.2f", budget) + " for " + targetItem);
        }

        decisionTree = buildDecisionTree();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                if (!requestSent) {
                    ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                    req.addReceiver(new AID("Broker1", AID.ISLOCALNAME));
                    req.setContent(targetItem);
                    send(req);
                    if (gui != null) gui.log("👤 " + getLocalName() + ": Requested " + targetItem);
                    requestSent = true;
                }

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
                buildPriceBranch(),
                buildDeliveredBranch(),
                buildOutOfStockBranch()
        );
    }

    private Node buildPriceBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getContent().startsWith("PRICE:")),
                new Action(() -> {
                    double price = Double.parseDouble(currentMsg.getContent().split(":")[1]);
                    negotiationRounds++;
                    double effectiveBudget = budget * (1.0 + (negotiationRounds * 0.05));

                    if (gui != null) gui.log("👤 " + getLocalName()
                            + ": Round " + negotiationRounds
                            + " — price $" + String.format("%.2f", price)
                            + " vs effective budget $" + String.format("%.2f", effectiveBudget));

                    return Status.SUCCESS;
                }),
                new Selector(
                        new Sequence(
                                new Condition(() -> {
                                    double price = Double.parseDouble(currentMsg.getContent().split(":")[1]);
                                    double effectiveBudget = budget * (1.0 + (negotiationRounds * 0.05));
                                    boolean affordable = price <= effectiveBudget;
                                    if (affordable && gui != null)
                                        gui.log("🌳 BT Customer: Condition met — price affordable, accepting");
                                    return affordable;
                                }),
                                new Action(() -> {
                                    if (gui != null) gui.log("👤 " + getLocalName() + ": Acceptable deal. Accepting.");
                                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                    accept.addReceiver(new AID("Broker1", AID.ISLOCALNAME));
                                    accept.setContent("ACCEPT");
                                    send(accept);
                                    return Status.SUCCESS;
                                })
                        ),
                        new Sequence(
                                new Condition(() -> {
                                    boolean maxed = negotiationRounds >= MAX_ROUNDS;
                                    if (maxed && gui != null)
                                        gui.log("🌳 BT Customer: Condition met — max rounds reached, walking away");
                                    return maxed;
                                }),
                                new Action(() -> {
                                    if (gui != null) gui.log("👤 " + getLocalName() + ": Max rounds reached. Walking away.");
                                    ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                    reject.addReceiver(new AID("Broker1", AID.ISLOCALNAME));
                                    reject.setContent("REJECT");
                                    send(reject);
                                    gui.transactionCompleted(false);
                                    return Status.SUCCESS;
                                })
                        ),
                        new Action(() -> {
                            if (gui != null) gui.log("👤 " + getLocalName()
                                    + ": Too expensive. Rejecting (round "
                                    + negotiationRounds + "/" + MAX_ROUNDS + ")");
                            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                            reject.addReceiver(new AID("Broker1", AID.ISLOCALNAME));
                            reject.setContent("REJECT");
                            send(reject);
                            return Status.SUCCESS;
                        })
                )
        );
    }

    private Node buildDeliveredBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getContent().startsWith("DELIVERED")),
                new Action(() -> {
                    if (gui != null) {
                        gui.log("👤 " + getLocalName() + ": SUCCESS! Received my " + targetItem + "!");
                        gui.transactionCompleted(true);
                    }
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildOutOfStockBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getContent().equals("OUT_OF_STOCK")),
                new Action(() -> {
                    if (gui != null) {
                        gui.log("👤 " + getLocalName() + ": Out of stock — giving up.");
                        gui.transactionCompleted(false);
                    }
                    return Status.SUCCESS;
                })
        );
    }
}