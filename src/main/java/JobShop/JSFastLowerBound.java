package JobShop;

import org.ddolib.modeling.FastLowerBound;

import java.util.*;
import static java.lang.Math.max;

public class JSFastLowerBound implements FastLowerBound<JSState> {

    private final JSProblem problem;

    public JSFastLowerBound(JSProblem problem) {
        this.problem = problem;
    }

    @Override
    public double fastLowerBound(JSState state, Set<Integer> set) {
        return state.lb - problem.getMakespan(state);
    }


}
