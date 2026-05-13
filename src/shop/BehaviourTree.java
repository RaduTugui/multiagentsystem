package shop;

public class BehaviourTree {

    public enum Status { SUCCESS, FAILURE, RUNNING }

    public interface Node {
        Status tick();
    }

    public static class Selector implements Node {
        private final Node[] children;

        public Selector(Node... children) {
            this.children = children;
        }

        public Status tick() {
            for (Node child : children) {
                Status s = child.tick();
                if (s == Status.SUCCESS) return Status.SUCCESS;
                if (s == Status.RUNNING)  return Status.RUNNING;
            }
            return Status.FAILURE;
        }
    }

    public static class Sequence implements Node {
        private final Node[] children;

        public Sequence(Node... children) {
            this.children = children;
        }

        public Status tick() {
            for (Node child : children) {
                Status s = child.tick();
                if (s == Status.FAILURE) return Status.FAILURE;
                if (s == Status.RUNNING)  return Status.RUNNING;
            }
            return Status.SUCCESS;
        }
    }

    public static class Condition implements Node {
        private final java.util.function.BooleanSupplier check;

        public Condition(java.util.function.BooleanSupplier check) {
            this.check = check;
        }

        public Status tick() {
            return check.getAsBoolean() ? Status.SUCCESS : Status.FAILURE;
        }
    }

    public static class Action implements Node {
        private final java.util.function.Supplier<Status> action;

        public Action(java.util.function.Supplier<Status> action) {
            this.action = action;
        }

        public Status tick() {
            return action.get();
        }
    }
}