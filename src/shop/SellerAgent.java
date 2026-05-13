package shop;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import shop.BehaviourTree.*;

public class SellerAgent extends Agent {
    private Main gui;
    private double basePrice;
    private double profitMargin = 0.20;
    private double lastPrice = 0;

    private int totalSales = 0;
    private int totalRejections = 0;
    private int consecutiveRejections = 0;
    private int consecutiveAccepts = 0;

    private ACLMessage currentMsg = null;
    private Node decisionTree;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            gui = (Main) args[0];
            basePrice = (Double) args[1];
        } else {
            basePrice = 800.0;
        }

        if (gui != null) {
            gui.log("🏪 Seller ready - Base: $" + basePrice);
            gui.updateAgentStatus("Seller1", "Active", new java.awt.Color(230, 126, 34));
            gui.updatePrice(basePrice * 1.2, profitMargin);
        }

        decisionTree = buildDecisionTree();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    currentMsg = msg;
                    Status result = decisionTree.tick();
                    if (gui != null) gui.log("🌳 BT root result: " + result);
                } else {
                    block();
                }
            }
        });
    }

    private Node buildDecisionTree() {
        return new Selector(
                buildCFPBranch(),
                buildAcceptBranch(),
                buildRejectBranch()
        );
    }

    private Node buildCFPBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.CFP),
                new Selector(
                        new Sequence(
                                new Condition(() -> {
                                    double rate = (totalSales + totalRejections) == 0 ? 1.0
                                            : (double) totalSales / (totalSales + totalRejections);
                                    boolean trigger = consecutiveRejections >= 3
                                            || (rate < 0.40 && (totalSales + totalRejections) >= 5);
                                    if (trigger && gui != null)
                                        gui.log("🌳 BT: Condition CONSERVATIVE met (streak: "
                                                + consecutiveRejections + ", rate: "
                                                + String.format("%.0f", rate * 100) + "%)");
                                    return trigger;
                                }),
                                new Action(() -> {
                                    profitMargin = Math.max(0.05, profitMargin - 0.05);
                                    if (gui != null) {
                                        gui.log("🌳 BT: Action → CONSERVATIVE margin = " + (int)(profitMargin * 100) + "%");
                                        gui.updateSellerStrategy("CONSERVATIVE");
                                    }
                                    return Status.SUCCESS;
                                })
                        ),
                        new Sequence(
                                new Condition(() -> {
                                    double rate = (totalSales + totalRejections) == 0 ? 1.0
                                            : (double) totalSales / (totalSales + totalRejections);
                                    boolean trigger = (consecutiveAccepts >= 3 && profitMargin < 0.45)
                                            || (rate > 0.75 && (totalSales + totalRejections) >= 5);
                                    if (trigger && gui != null)
                                        gui.log("🌳 BT: Condition AGGRESSIVE met (streak: "
                                                + consecutiveAccepts + ", rate: "
                                                + String.format("%.0f", rate * 100) + "%)");
                                    return trigger;
                                }),
                                new Action(() -> {
                                    profitMargin = Math.min(0.50, profitMargin + 0.05);
                                    if (gui != null) {
                                        gui.log("🌳 BT: Action → AGGRESSIVE margin = " + (int)(profitMargin * 100) + "%");
                                        gui.updateSellerStrategy("AGGRESSIVE");
                                    }
                                    return Status.SUCCESS;
                                })
                        ),
                        new Action(() -> {
                            if (gui != null) {
                                gui.log("🌳 BT: Action → NEUTRAL margin = " + (int)(profitMargin * 100) + "%");
                                gui.updateSellerStrategy("NEUTRAL");
                            }
                            return Status.SUCCESS;
                        })
                ),
                new Action(() -> {
                    lastPrice = basePrice * (1 + profitMargin);
                    String customer = currentMsg.getReplyWith();

                    if (gui != null) gui.log("💵 Seller: Proposing $"
                            + String.format("%.2f", lastPrice)
                            + " to " + customer
                            + " [margin: " + (int)(profitMargin * 100) + "%]");

                    ACLMessage reply = currentMsg.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(lastPrice));
                    reply.setReplyWith(customer);
                    send(reply);

                    if (gui != null) gui.updatePrice(lastPrice, profitMargin);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildAcceptBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL),
                new Action(() -> {
                    totalSales++;
                    consecutiveAccepts++;
                    consecutiveRejections = 0;
                    profitMargin = Math.min(0.50, profitMargin + 0.05);

                    if (gui != null) {
                        gui.log("💰 Seller: SOLD at $" + String.format("%.2f", lastPrice)
                                + " | Sales: " + totalSales
                                + " | Streak: +" + consecutiveAccepts);
                        gui.updatePrice(lastPrice, profitMargin);
                    }
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildRejectBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.REJECT_PROPOSAL),
                new Action(() -> {
                    totalRejections++;
                    consecutiveRejections++;
                    consecutiveAccepts = 0;
                    profitMargin = Math.max(0.05, profitMargin - 0.05);

                    if (gui != null) {
                        gui.log("😞 Seller: Rejected | Total: " + totalRejections
                                + " | Streak: -" + consecutiveRejections);
                        gui.updatePrice(basePrice * (1 + profitMargin), profitMargin);
                    }
                    return Status.SUCCESS;
                })
        );
    }
}