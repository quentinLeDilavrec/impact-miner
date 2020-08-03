package fr.quentin.impactMiner;

import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;

public class Utils {
    public static CtElement matchExact(CtElement ele, int start, int end) {
        SourcePosition position = ele.getPosition();
        if (!position.isValidPosition()) {
            return null;
        }
        int sourceStart = position.getSourceStart();
        int sourceEnd = position.getSourceEnd();
        int ds = start - sourceStart;
        int de = sourceEnd - end;
        if (ds == 0 && de == 0) {
            return ele;
        } else if (ds >= 0 && de >= 0) {
            int i = 0;
            for (CtElement child : ele.getDirectChildren()) {
                CtElement r = matchExact(child, start, end);
                if (r != null) {
                    return r;
                }
                i++;
            }
            assert false : ele;
            return null;
        } else if (sourceEnd < start) {
            return null;
        } else if (end < sourceStart) {
            return null;
        } else {
            return null;
        }
    }
}