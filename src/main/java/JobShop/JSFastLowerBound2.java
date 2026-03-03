package JobShop;

import org.ddolib.modeling.FastLowerBound;

import java.util.Set;

public class JSFastLowerBound2 implements FastLowerBound<JSState> {

    private final JSProblem2 problem;

    public JSFastLowerBound2(JSProblem2 problem) {
        this.problem = problem;
    }

    @Override
    public double fastLowerBound(JSState state, Set<Integer> set) {
        return state.lb - problem.getMakespan(state);
    }


}
