package fr.quentin.impactMiner;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtElement;

/**
 * 
 */
public class ImpactElement {
    private Position position;
    private final CtElement content;
    private final Map<String, Object> more = new HashMap<>();
    private final Map<Object, Position> evolutions = new HashMap<>();
	public static final String REDUNDANT = "redundant";
	public static final String BEST_FLOW = "best flow graph";
	public static final String BEST_TYPE = "best type graph";
	public static final String BEST_CG = "best call graph";
	public static final String BEST_STRUC = "best struc graph";
    public static final String BEST_OTHER = "best other graph";
    
    /**
     * @return the getEvolutionWithNonCorrectedPosition
     */
    public Map<Object, Position> getEvolutionWithNonCorrectedPosition() {
        return evolutions;
    }

    public <T> T getMD(String key) {
        return (T) more.get(key);
    }

    public <T> T getMD(String key, T deflt) {
        return (T) more.getOrDefault(key, deflt);
    }

    public <T> T putMD(String key, T value) {
        return (T) more.put(key, value);
    }

    public ImpactElement(CtElement e) {
        this(e.getPosition(), e);
    }

    public ImpactElement(Position position) {
        this(position, null);
    }

    public ImpactElement(String file, int start, int end) {
        this(file, start, end, null);
    }

    public ImpactElement(String file, int start, int end, CtElement content) {
        this.position = new Position(file, start, end);
        this.content = content;
    }

    public ImpactElement(SourcePosition position) {
        this(position, null);
    }

    public ImpactElement(SourcePosition position, CtElement content) {
        this(position.getFile().getAbsolutePath(), position.getSourceStart(), position.getSourceEnd(), content);
    }

    public ImpactElement(Position position, CtElement content) {
        this.position = position;
        this.content = content;
    }

    /**
     * @return the position
     */
    public Position getPosition() {
        return position;
    }

    /**
     * @return the evolutions
     */
    public Set<Object> getEvolutions() {
        return evolutions.keySet();
    }

    public void addEvolution(Object evolution, Position nonCorrectedPosition) {
        evolutions.put(evolution, nonCorrectedPosition);
    }

    public void addEvolution(Object evolution) {
        evolutions.put(evolution, position);
    }

    /**
     * 
     * @return nullable
     */
    public CtElement getContent() {
        return content;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((position == null) ? 0 : position.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ImpactElement other = (ImpactElement) obj;
        if (position == null) {
            if (other.position != null)
                return false;
        } else if (!position.equals(other.position))
            return false;
        return true;
    }

}
