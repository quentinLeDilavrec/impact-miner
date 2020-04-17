package fr.quentin;

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
    private CtElement content;
    private Map<Evolution<Object>,Position> evolutions = new HashMap<>();

    /**
     * @return the getEvolutionWithNonCorrectedPosition
     */
    public Map<Evolution<Object>,Position> getEvolutionWithNonCorrectedPosition() {
        return evolutions;
    }

    ImpactElement(CtElement e) throws IOException {
        this(e.getPosition());
        this.content = e;
    }

    ImpactElement(Position position) {
        this.position = position;
    }

    ImpactElement(String file, int start, int end) {
        this.position = new Position(file, start, end);
    }

    ImpactElement(SourcePosition p) throws IOException {
        this(p.getFile().getCanonicalPath(), p.getSourceStart(), p.getSourceEnd());
    }

    ImpactElement(String file, int start, int end, CtElement content) {
        this(file, start, end);
        this.content = content;
    }

    ImpactElement(SourcePosition position, CtElement content) throws IOException {
        this(position);
        this.content = content;
    }

    ImpactElement(Position position, CtElement content) {
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
    public Set<Evolution<Object>> getEvolutions() {
        return evolutions.keySet();
    }

    public void addEvolution(Evolution<Object> evolution, Position nonCorrectedPosition ) {
        evolutions.put(evolution, nonCorrectedPosition);
    }

    public void addEvolution(Evolution<Object> evolution) {
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
