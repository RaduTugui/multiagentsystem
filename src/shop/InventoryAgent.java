package shop;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import shop.BehaviourTree.*;
import java.util.HashMap;
import java.util.Map;

public class InventoryAgent extends Agent {
    private Main gui;
    private Map<String, Integer> stock = new HashMap<>();
    private Map<String, Integer> maxStock = new HashMap<>();
    private static final double LOW_STOCK_THRESHOLD = 0.30;
    private static final double HIGH_STOCK_THRESHOLD = 0.80;
    private ACLMessage currentMsg = null;
    private Node decisionTree;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) gui = (Main) args[0];

        stock.put("laptop", 10);
        stock.put("phone", 18);
        stock.put("tablet", 14);
        stock.put("watch", 20);

        maxStock.put("laptop", 10);
        maxStock.put("phone", 18);
        maxStock.put("tablet", 14);
        maxStock.put("watch", 20);

        if (gui != null) {
            gui.log("📦 Inventory ready");
            gui.updateAgentStatus("Inventory1", "Active", new java.awt.Color(241, 196, 15));
            updateStockDisplay();
        }

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
                buildQueryBranch(),
                buildRestockBranch()
        );
    }

    private Node buildQueryBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.QUERY_IF),
                new Action(() -> {
                    String item = currentMsg.getContent();
                    String customer = currentMsg.getReplyWith();
                    int current = stock.getOrDefault(item, 0);
                    int max = maxStock.getOrDefault(item, 0);
                    double fillRate = max == 0 ? 0 : (double) current / max;

                    ACLMessage reply = currentMsg.createReply();

                    if (current > 0) {
                        stock.put(item, current - 1);
                        reply.setPerformative(ACLMessage.CONFIRM);

                        if (fillRate <= LOW_STOCK_THRESHOLD) {
                            if (gui != null) gui.log("⚠️ Inventory [BT Decision]: LOW STOCK for "
                                    + item + " (" + (current - 1) + " left)");
                            gui.updateStockAlert(item, true);
                        } else {
                            if (gui != null) gui.log("📦 Inventory: " + item
                                    + " reserved (" + (current - 1) + " left)");
                            gui.updateStockAlert(item, false);
                        }
                        gui.updateStock(item, current - 1, max);
                    } else {
                        reply.setPerformative(ACLMessage.DISCONFIRM);
                        if (gui != null) gui.log("❌ Inventory [BT Decision]: "
                                + item + " OUT OF STOCK");
                    }

                    reply.setReplyWith(customer);
                    send(reply);
                    return Status.SUCCESS;
                })
        );
    }

    private Node buildRestockBranch() {
        return new Sequence(
                new Condition(() -> currentMsg.getPerformative() == ACLMessage.CANCEL),
                new Selector(
                        new Sequence(
                                new Condition(() -> {
                                    String item = currentMsg.getContent();
                                    int current = stock.getOrDefault(item, 0);
                                    int max = maxStock.getOrDefault(item, 0);
                                    double fillRate = max == 0 ? 0 : (double) current / max;
                                    boolean shouldRestock = fillRate < HIGH_STOCK_THRESHOLD;
                                    if (gui != null) gui.log("🌳 BT Inventory: Restock condition — fill "
                                            + String.format("%.0f", fillRate * 100) + "% → "
                                            + (shouldRestock ? "RESTOCK" : "SKIP"));
                                    return shouldRestock;
                                }),
                                new Action(() -> {
                                    String item = currentMsg.getContent();
                                    int current = stock.getOrDefault(item, 0);
                                    int max = maxStock.getOrDefault(item, 0);
                                    stock.put(item, current + 1);

                                    if (gui != null) {
                                        gui.log("📦 Inventory [BT Decision]: Restocking " + item
                                                + " (" + (current + 1) + " units now)");
                                        gui.updateStock(item, current + 1, max);
                                        gui.updateStockAlert(item, (current + 1) <= max * LOW_STOCK_THRESHOLD);
                                    }
                                    return Status.SUCCESS;
                                })
                        ),
                        new Action(() -> {
                            if (gui != null) gui.log("📦 Inventory [BT Decision]: Restock skipped — already above 80% capacity");
                            return Status.SUCCESS;
                        })
                )
        );
    }

    private void updateStockDisplay() {
        if (gui == null) return;
        for (Map.Entry<String, Integer> e : stock.entrySet()) {
            gui.updateStock(e.getKey(), e.getValue(), maxStock.get(e.getKey()));
        }
    }
}