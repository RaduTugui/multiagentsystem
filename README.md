# E-Commerce Multi-Agent System

## Problem Domain
A simulated e-commerce platform where autonomous agents
negotiate product purchases. Customers request items,
a broker orchestrates the negotiation, a seller
dynamically adjusts pricing strategy, an inventory
manager controls stock, and a shipping agent handles
delivery.

## Architecture
The system uses JADE (Java Agent DEvelopment Framework)
and implements decision making via a custom Behaviour
Tree framework.

### Agents
- **BrokerAgent** — orchestrates all communication between agents
- **SellerAgent** — decides pricing strategy using a Behaviour Tree (AGGRESSIVE / NEUTRAL / CONSERVATIVE)
- **InventoryAgent** — manages stock and decides restocking using a Behaviour Tree
- **CustomerAgent** — evaluates prices and negotiates using a Behaviour Tree
- **ShippingAgent** — simulates delivery

### Decision Making
Each agent runs an independent Behaviour Tree evaluated
on every received message. Node types: Selector,
Sequence, Condition, Action.

## How to Run

### Requirements
- Java 8 or higher
- JADE library (jade.jar) — download from https://jade.tilab.com/

### Steps
1. Clone the repository
   git clone https://github.com/RaduTugui/multiagentsystem.git

2. Add jade.jar to your classpath

3. Compile
   javac -cp jade.jar src/shop/*.java

4. Run
   java -cp jade.jar:src shop.Main

### Or open in IntelliJ / Eclipse
1. Import as Java project
2. Add jade.jar as a library
3. Run Main.java